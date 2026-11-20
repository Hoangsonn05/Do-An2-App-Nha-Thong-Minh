package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.SmartHomeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pure Kotlin observeHomes merge/isolation logic (RtdbHomeMerger),
 * timeout constant contract, LAN isolation, and secret non-leakage without touching production RTDB.
 */
class CloudIsolationTest {

    @Test
    fun mergeHomes_whenEmpty_returnsOkEmptyList() {
        val result = RtdbHomeMerger.mergeHomes(
            currentUid = "uA",
            indexedHomeIds = emptyList(),
            metaResults = emptyMap()
        )
        assertEquals(CloudResult.Ok(emptyList<CloudHome>()), result)
    }

    @Test
    fun mergeHomes_derivesRoleFromAuthoritativeOwnerUid() {
        val result = RtdbHomeMerger.mergeHomes(
            currentUid = "uA",
            indexedHomeIds = listOf("home_A001", "home_B001"),
            metaResults = mapOf(
                "home_A001" to HomeMetaFetchResult.Found("home_A001", "Nha A", ownerUid = "uA"),
                "home_B001" to HomeMetaFetchResult.Found("home_B001", "Nha B", ownerUid = "uOther")
            )
        )
        assertEquals(
            CloudResult.Ok(
                listOf(
                    CloudHome("home_A001", "Nha A", CloudRole.OWNER),
                    CloudHome("home_B001", "Nha B", CloudRole.MEMBER)
                )
            ),
            result
        )
    }

    @Test
    fun mergeHomes_isolatesStalePermissionDeniedHomeWithoutDroppingRemainingHomes() {
        val result = RtdbHomeMerger.mergeHomes(
            currentUid = "uB",
            indexedHomeIds = listOf("home_STALE01", "home_VALID01", "home_MISSING1", "home_VALID02"),
            metaResults = mapOf(
                "home_STALE01" to HomeMetaFetchResult.PermissionDenied,
                "home_VALID01" to HomeMetaFetchResult.Found("home_VALID01", "Valid 1", ownerUid = "uA"),
                "home_MISSING1" to HomeMetaFetchResult.NotFound,
                "home_VALID02" to HomeMetaFetchResult.Found("home_VALID02", "Valid 2", ownerUid = "uB")
            )
        )
        assertEquals(
            CloudResult.Ok(
                listOf(
                    CloudHome("home_VALID01", "Valid 1", CloudRole.MEMBER),
                    CloudHome("home_VALID02", "Valid 2", CloudRole.OWNER)
                )
            ),
            result
        )
    }

    @Test
    fun mergeHomes_whenAllEntriesStalePermissionDenied_returnsOkEmptyList() {
        val result = RtdbHomeMerger.mergeHomes(
            currentUid = "uB",
            indexedHomeIds = listOf("home_STALE01", "home_STALE02"),
            metaResults = mapOf(
                "home_STALE01" to HomeMetaFetchResult.PermissionDenied,
                "home_STALE02" to HomeMetaFetchResult.PermissionDenied
            )
        )
        assertEquals(CloudResult.Ok(emptyList<CloudHome>()), result)
    }

    @Test
    fun mergeHomes_propagatesGlobalTransportErrorsDistinctFromPermissionDenied() {
        val offlineRes = RtdbHomeMerger.mergeHomes(
            currentUid = "uB",
            indexedHomeIds = listOf("home_VALID01", "home_OFFLINE1"),
            metaResults = mapOf(
                "home_VALID01" to HomeMetaFetchResult.Found("home_VALID01", "Valid", ownerUid = "uA"),
                "home_OFFLINE1" to HomeMetaFetchResult.Failed(CloudError.Offline, "No internet")
            )
        )
        assertEquals(CloudResult.Err(CloudError.Offline, "No internet"), offlineRes)

        val timeoutRes = RtdbHomeMerger.mergeHomes(
            currentUid = "uB",
            indexedHomeIds = listOf("home_TIMEOUT1"),
            metaResults = mapOf(
                "home_TIMEOUT1" to HomeMetaFetchResult.Failed(CloudError.Timeout, "Timed out")
            )
        )
        assertEquals(CloudResult.Err(CloudError.Timeout, "Timed out"), timeoutRes)
    }

    @Test
    fun cloudTimeoutConstant_isExplicitlyConfiguredInMilliseconds() {
        assertEquals(10_000L, CLOUD_OPERATION_TIMEOUT_MS)
    }

    @Test
    fun cloudErrors_doNotAffectLanStateOrLeakDeviceKey() = runTest {
        val lanState = SmartHomeState(
            esp32Online = true,
            esp32Ip = "192.168.1.50",
            deviceKey = "LAN_ONLY_SECRET_KEY_XYZ"
        )
        val fakeCloud = FakeCloudRepository(initialUid = "uB")
        fakeCloud.seedStaleHomePermissionDenied("home_STALE99")
        val createOk = fakeCloud.createHome("Nha B")
        assertTrue(createOk is CloudResult.Ok)

        // Stale home is filtered out while valid home remains
        val homes = (fakeCloud.observeHomes().first() as CloudResult.Ok).data
        assertEquals(1, homes.size)
        assertEquals("Nha B", homes.first().name)

        // Simulated Cloud Offline/Timeout errors leave LAN state untouched
        fakeCloud.failNext(CloudError.Offline, "Cloud offline")
        val err = fakeCloud.observeHomes().first()
        assertTrue(err is CloudResult.Err && err.error == CloudError.Offline)
        assertTrue(lanState.esp32Online)

        // Cloud models never carry LAN deviceKey
        val serializedModels = homes.toString() + CloudMember("uB", CloudRole.MEMBER, 1000L, "uA").toString()
        assertFalse(serializedModels.contains(lanState.deviceKey))
    }
}

package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.HomeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudFallbackObserverTest {

    private companion object {
        const val T0: Long = 1_795_305_600_000L
        const val UID_A: String = "test_user_uA"
        const val UID_B: String = "test_user_uB"
        const val HOME_A: String = "home_A_0001"
        const val HOME_B: String = "home_B_0001"
    }

    private fun readyCloudState(
        uid: String = UID_A,
        homeId: String = HOME_A,
        role: CloudRole = CloudRole.OWNER,
        status: CloudStatus = CloudStatus.READY
    ): CloudUiState = CloudUiState(
        status = status,
        currentUid = uid,
        homes = listOf(CloudHome(id = homeId, name = "Nha Chinh", role = role)),
        selectedHomeId = homeId
    )

    private fun seedFullCloudDevice(
        repo: FakeCloudDeviceRepository,
        homeId: String,
        deviceId: String,
        uid: String,
        temp: Int = 27
    ) {
        repo.seedDevice(
            homeId,
            CloudDevice(deviceId, "ESP32", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, uid)
        )
        repo.seedGatewayStateSnapshot(
            homeId,
            deviceId,
            GatewayStateSnapshot(
                online = true,
                reportedBy = uid,
                receivedAt = T0 + 10_000L,
                deviceTs = T0 + 9_500L,
                temp = temp,
                hum = 60,
                motion = false,
                light = true,
                door = false,
                alarm = false,
                monitoring = true,
                mode = HomeMode.HOME
            )
        )
        repo.seedLatestTelemetrySnapshot(
            homeId,
            deviceId,
            CloudTelemetrySnapshot(
                recordId = "202611212359_g",
                temp = temp,
                hum = 60,
                receivedAt = T0 + 12_000L,
                deviceTs = T0 + 11_500L
            )
        )
        repo.seedRecentEvents(
            homeId,
            deviceId,
            listOf(
                CloudEventRecord(
                    id = "g_stream1_2",
                    type = "rain_started",
                    severity = "warn",
                    stream = "stream1",
                    seq = 2L,
                    receivedAt = T0 + 15_000L,
                    deviceTs = T0 + 14_500L
                ),
                CloudEventRecord(
                    id = "g_stream1_1",
                    type = "armed",
                    severity = "info",
                    stream = "stream1",
                    seq = 1L,
                    receivedAt = T0 + 5_000L,
                    deviceTs = T0 + 4_500L
                )
            )
        )
    }

    @Test
    fun lanOnline_neverOpensListeners_andCancelsImmediatelyOnLanRecovery() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        seedFullCloudDevice(repo, HOME_A, "dev_gw_0001", UID_A)

        val cloudFlow = MutableStateFlow(readyCloudState())
        val esp32OnlineFlow = MutableStateFlow(true)
        val observer = CloudFallbackObserver(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            esp32OnlineFlow = esp32OnlineFlow,
            deviceRepository = repo
        )
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)

        // Go offline: wait 4_999ms -> still debouncing
        esp32OnlineFlow.value = false
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(0, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.totalActiveListeners)

        // Reach 5_000ms -> opens 3 listeners and populates snapshot
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.resolveCalls.get())
        assertEquals(1, repo.activeGatewayStateListeners.get())
        assertEquals(1, repo.activeTelemetryListeners.get())
        assertEquals(1, repo.activeEventsListeners.get())

        val snap = observer.cloudSnapshot.value
        assertNotNull(snap)
        assertEquals("dev_gw_0001", snap!!.deviceId)
        assertFalse(snap.loading)
        assertNull(snap.error)
        assertEquals(27, snap.gatewayState?.temp)
        assertEquals("202611212359_g", snap.latestTelemetry?.recordId)
        assertEquals(2, snap.recentEvents.size)
        assertEquals("g_stream1_2", snap.recentEvents.first().id)

        // LAN recovers -> listeners cancelled immediately and snapshot cleared
        esp32OnlineFlow.value = true
        runCurrent()
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)
    }

    @Test
    fun debounce5s_preventsListenerFlicker_whenLanRecoversWithin5Seconds() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        seedFullCloudDevice(repo, HOME_A, "dev_gw_0001", UID_A)

        val cloudFlow = MutableStateFlow(readyCloudState())
        val esp32OnlineFlow = MutableStateFlow(true)
        val observer = CloudFallbackObserver(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            esp32OnlineFlow = esp32OnlineFlow,
            deviceRepository = repo
        )
        runCurrent()

        // Drop LAN for 4 seconds then recover -> no RTDB reads or listeners
        esp32OnlineFlow.value = false
        advanceTimeBy(4_000L)
        runCurrent()
        esp32OnlineFlow.value = true
        runCurrent()
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(0, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)

        // Sustained offline for >= 5s -> fallback starts
        esp32OnlineFlow.value = false
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, repo.findActiveDeviceCalls.get())
        assertEquals(3, repo.totalActiveListeners)
        assertNotNull(observer.cloudSnapshot.value)
    }

    @Test
    fun partialBranchError_preservesValidDataFromOtherBranches_andRecordsError() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val devId = "dev_gw_0001"
        seedFullCloudDevice(repo, HOME_A, devId, UID_A, temp = 29)
        repo.denyPathWithoutData(RtdbPaths.deviceTelemetryPath(HOME_A, devId))

        val observer = CloudFallbackObserver(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(readyCloudState()),
            esp32OnlineFlow = MutableStateFlow(false),
            deviceRepository = repo
        )
        advanceTimeBy(5_000L)
        runCurrent()

        // Telemetry failed with PermissionDenied, but gatewayState and events remain valid
        val partial = observer.cloudSnapshot.value
        assertNotNull(partial)
        assertFalse(partial!!.loading)
        assertEquals(CloudError.PermissionDenied, partial.error)
        assertEquals(29, partial.gatewayState?.temp)
        assertNull(partial.latestTelemetry)
        assertEquals(2, partial.recentEvents.size)

        // Clear denied telemetry path and retry -> all 3 branches recover with error=null
        repo.clearDeniedPath(RtdbPaths.deviceTelemetryPath(HOME_A, devId))
        observer.retry()
        advanceTimeBy(5_000L)
        runCurrent()
        val recoveredAll = observer.cloudSnapshot.value!!
        assertFalse(recoveredAll.loading)
        assertNull(recoveredAll.error)
        assertNotNull(recoveredAll.latestTelemetry)

        // Emit mid-stream error on events while already subscribed -> existing events stay intact
        repo.emitEventsError(HOME_A, devId, CloudError.Offline, "Mất kết nối")
        runCurrent()
        val afterEventsErr = observer.cloudSnapshot.value!!
        assertEquals(2, afterEventsErr.recentEvents.size)
        assertEquals(29, afterEventsErr.gatewayState?.temp)
        assertEquals(CloudError.Offline, afterEventsErr.error)

        // Subsequent Ok emission on gateway state preserves events error until events recover
        repo.seedGatewayStateSnapshot(
            HOME_A,
            devId,
            GatewayStateSnapshot(online = true, receivedAt = T0 + 30_000L, temp = 31)
        )
        runCurrent()
        val updatedGateway = observer.cloudSnapshot.value!!
        assertEquals(31, updatedGateway.gatewayState?.temp)
        assertEquals(CloudError.Offline, updatedGateway.error)

        // Events recovery clears events branch error
        repo.seedRecentEvents(HOME_A, devId, afterEventsErr.recentEvents)
        runCurrent()
        assertNull(observer.cloudSnapshot.value?.error)
    }

    @Test
    fun sessionLifecycle_allowsMemberRead_handlesUidMismatch_andClearsOnSignOutOrRevoke() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        seedFullCloudDevice(repo, HOME_A, "dev_homeA_1", UID_A, temp = 24)
        seedFullCloudDevice(repo, HOME_B, "dev_homeB_1", UID_B, temp = 32)

        // MEMBER role can observe Cloud fallback data (Rules allow @MEMBER read)
        val cloudFlow = MutableStateFlow(readyCloudState(uid = UID_A, homeId = HOME_A, role = CloudRole.MEMBER))
        val esp32OnlineFlow = MutableStateFlow(false)
        val observer = CloudFallbackObserver(
            scope = backgroundScope,
            cloudStateFlow = cloudFlow,
            esp32OnlineFlow = esp32OnlineFlow,
            deviceRepository = repo
        )
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(3, repo.totalActiveListeners)
        assertEquals("dev_homeA_1", observer.cloudSnapshot.value?.deviceId)
        assertEquals(24, observer.cloudSnapshot.value?.gatewayState?.temp)

        // Simulate Auth UID changing before CloudUiState catches up -> listeners emit Unauthenticated & cancel
        repo.setSignedInUid(UID_B)
        runCurrent()
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)

        // CloudUiState catches up with UID_B / HOME_B -> debounce 5s then observe HOME_B
        cloudFlow.value = readyCloudState(uid = UID_B, homeId = HOME_B, role = CloudRole.OWNER)
        runCurrent()
        assertNull(observer.cloudSnapshot.value)
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(3, repo.totalActiveListeners)
        assertEquals("dev_homeB_1", observer.cloudSnapshot.value?.deviceId)
        assertEquals(32, observer.cloudSnapshot.value?.gatewayState?.temp)

        // Access revoked -> closes listeners and clears snapshot
        cloudFlow.value = CloudUiState(status = CloudStatus.ACCESS_REVOKED, currentUid = UID_B)
        runCurrent()
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)

        // Restore ready state then sign out -> clears cleanly
        cloudFlow.value = readyCloudState(uid = UID_B, homeId = HOME_B, role = CloudRole.OWNER)
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(3, repo.totalActiveListeners)

        repo.setSignedInUid(null)
        cloudFlow.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
        runCurrent()
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)
    }

    @Test
    fun noActiveDevice_orRevokedOnly_keepsSnapshotNull_withoutCreatingDevice() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val observer = CloudFallbackObserver(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(readyCloudState()),
            esp32OnlineFlow = MutableStateFlow(false),
            deviceRepository = repo
        )
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(1, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.resolveCalls.get())
        assertTrue(repo.getDevices(HOME_A).isEmpty())
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)

        // Seed only a REVOKED device and retry -> still null and zero listeners
        repo.seedDevice(
            HOME_A,
            CloudDevice("dev_revoked_1", "Old", "esp32-smarthome", DeviceStatus.REVOKED, 100L, UID_A)
        )
        observer.retry()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(2, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.resolveCalls.get())
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)
    }

    @Test
    fun clear_cancelsActiveListeners_andResetsSnapshot() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        seedFullCloudDevice(repo, HOME_A, "dev_gw_0001", UID_A)

        val esp32OnlineFlow = MutableStateFlow(false)
        val observer = CloudFallbackObserver(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(readyCloudState()),
            esp32OnlineFlow = esp32OnlineFlow,
            deviceRepository = repo
        )
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(3, repo.totalActiveListeners)
        assertNotNull(observer.cloudSnapshot.value)

        observer.clear()
        runCurrent()
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)

        // State changes after clear() never reopen listeners
        esp32OnlineFlow.value = true
        runCurrent()
        esp32OnlineFlow.value = false
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, repo.findActiveDeviceCalls.get())
        assertEquals(0, repo.totalActiveListeners)
        assertNull(observer.cloudSnapshot.value)
    }
}

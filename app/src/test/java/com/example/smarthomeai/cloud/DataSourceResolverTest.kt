package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SmartHomeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourceResolverTest {

    private companion object {
        const val T0: Long = 1_795_305_600_000L
    }

    @Test
    fun lanAlwaysWins_whenEsp32OnlineTrue_evenIfCloudSnapshotIsNewer() {
        val snapshot = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(
                online = false,
                reportedBy = "uA",
                receivedAt = T0 + 500_000L,
                deviceTs = T0 + 499_000L,
                temp = 31
            )
        )

        val resolved = DataSourceResolver.resolve(
            esp32Online = true,
            localLastSeen = T0,
            snapshot = snapshot,
            nowMs = T0 + 600_000L
        )

        assertEquals(DataSource.LAN, resolved.source)
        assertEquals(T0, resolved.updatedAt)
        assertNull(resolved.deviceTs)
        assertFalse(resolved.isStale)
        assertEquals(true, resolved.gatewayOnline)

        val resolvedNoLastSeen = DataSourceResolver.resolve(
            esp32Online = true,
            localLastSeen = 0L,
            snapshot = snapshot,
            nowMs = T0 + 600_000L
        )
        assertEquals(DataSource.LAN, resolvedNoLastSeen.source)
        assertNull(resolvedNoLastSeen.updatedAt)
        assertFalse(resolvedNoLastSeen.isStale)
    }

    @Test
    fun cloudSelected_whenLanOffline_andCloudReceivedAtGreaterThanLocalLastSeen() {
        val snapshot = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(
                online = true,
                reportedBy = "uA",
                receivedAt = T0 + 20_000L,
                deviceTs = T0 + 19_500L,
                temp = 28,
                hum = 62,
                mode = HomeMode.AWAY
            )
        )
        val state = SmartHomeState(
            esp32Online = false,
            lastSeen = T0
        )

        val resolved = DataSourceResolver.resolve(
            state = state,
            snapshot = snapshot,
            nowMs = T0 + 60_000L
        )

        assertEquals(DataSource.CLOUD, resolved.source)
        assertEquals(T0 + 20_000L, resolved.updatedAt)
        assertEquals(T0 + 19_500L, resolved.deviceTs)
        assertFalse(resolved.isStale)
        assertEquals(true, resolved.gatewayOnline)
    }

    @Test
    fun localCacheSelected_whenLanOffline_andLocalLastSeenNewerOrEqualOrCloudMissing() {
        // 1. Missing Cloud snapshot -> LOCAL_CACHE
        val noCloud = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = T0,
            snapshot = null,
            nowMs = T0 + 60_000L
        )
        assertEquals(DataSource.LOCAL_CACHE, noCloud.source)
        assertEquals(T0, noCloud.updatedAt)
        assertFalse(noCloud.isStale)
        assertNull(noCloud.gatewayOnline)

        // 2. Equal timestamps (cloudReceivedAt == localLastSeen) -> LOCAL_CACHE
        val equalSnapshot = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(online = true, receivedAt = T0)
        )
        val equalResolved = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = T0,
            snapshot = equalSnapshot,
            nowMs = T0 + 300_000L // exact boundary = not stale
        )
        assertEquals(DataSource.LOCAL_CACHE, equalResolved.source)
        assertFalse(equalResolved.isStale)

        // 3. Phone clock ahead of Firebase server -> deterministic LOCAL_CACHE fallback
        val skewSnapshot = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(online = true, receivedAt = T0 + 5_000L)
        )
        val skewResolved = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = T0 + 10_000L,
            snapshot = skewSnapshot,
            nowMs = T0 + 310_001L // > 300_000L past localLastSeen -> stale
        )
        assertEquals(DataSource.LOCAL_CACHE, skewResolved.source)
        assertEquals(T0 + 10_000L, skewResolved.updatedAt)
        assertTrue(skewResolved.isStale)
    }

    @Test
    fun noneSelected_whenLanOffline_noCloudData_andNoLocalCache() {
        val resolvedNull = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = 0L,
            snapshot = null,
            nowMs = T0
        )
        assertEquals(DataSource.NONE, resolvedNull.source)
        assertNull(resolvedNull.updatedAt)
        assertNull(resolvedNull.deviceTs)
        assertTrue(resolvedNull.isStale)
        assertNull(resolvedNull.gatewayOnline)

        val emptySnapshot = CloudDeviceSnapshot(deviceId = "dev_0001", loading = false)
        val resolvedEmpty = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = -1L,
            snapshot = emptySnapshot,
            nowMs = T0
        )
        assertEquals(DataSource.NONE, resolvedEmpty.source)
        assertTrue(resolvedEmpty.isStale)
    }

    @Test
    fun stalenessDetection_marksStaleAfter5Minutes_orWhenGatewayReportedOffline() {
        val onlineCloud = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(
                online = true,
                receivedAt = T0,
                deviceTs = T0 - 500L
            )
        )

        // Exact 5-minute threshold (300_000L) -> not stale
        val atThreshold = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = T0 - 1_000L,
            snapshot = onlineCloud,
            nowMs = T0 + DataSourceResolver.STALE_THRESHOLD_MS
        )
        assertEquals(DataSource.CLOUD, atThreshold.source)
        assertFalse(atThreshold.isStale)

        // 1ms past 5-minute threshold -> stale
        val pastThreshold = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = T0 - 1_000L,
            snapshot = onlineCloud,
            nowMs = T0 + DataSourceResolver.STALE_THRESHOLD_MS + 1L
        )
        assertEquals(DataSource.CLOUD, pastThreshold.source)
        assertTrue(pastThreshold.isStale)

        // Gateway reported online=false -> stale even if timestamp is fresh
        val offlineGatewayCloud = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(
                online = false,
                receivedAt = T0,
                deviceTs = T0 - 200L
            )
        )
        val gatewayOfflineResolved = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = T0 - 1_000L,
            snapshot = offlineGatewayCloud,
            nowMs = T0 + 1_000L
        )
        assertEquals(DataSource.CLOUD, gatewayOfflineResolved.source)
        assertTrue(gatewayOfflineResolved.isStale)
        assertEquals(false, gatewayOfflineResolved.gatewayOnline)

        // Telemetry-only snapshot (gatewayState == null) uses latestTelemetry.deviceTs and age threshold
        val telemetryOnly = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            latestTelemetry = CloudTelemetrySnapshot(
                recordId = "202611212359_g",
                temp = 29,
                hum = 65,
                receivedAt = T0,
                deviceTs = T0 - 300L
            )
        )
        val telemetryResolved = DataSourceResolver.resolve(
            esp32Online = false,
            localLastSeen = 0L,
            snapshot = telemetryOnly,
            nowMs = T0 + 10_000L
        )
        assertEquals(DataSource.CLOUD, telemetryResolved.source)
        assertEquals(T0 - 300L, telemetryResolved.deviceTs)
        assertFalse(telemetryResolved.isStale)
        assertNull(telemetryResolved.gatewayOnline)
    }

    @Test
    fun bestTimestampResolution_andBridgeDefaultCompatibility() {
        val snapshot = CloudDeviceSnapshot(
            deviceId = "dev_0001",
            gatewayState = GatewayStateSnapshot(online = true, receivedAt = T0 + 10_000L, deviceTs = null),
            latestTelemetry = CloudTelemetrySnapshot(
                recordId = "202611212359_g",
                temp = 27,
                hum = 60,
                receivedAt = T0 + 20_000L,
                deviceTs = T0 + 19_000L
            ),
            recentEvents = listOf(
                CloudEventRecord(
                    id = "g_stream1_5",
                    type = "rain_started",
                    severity = "warn",
                    stream = "stream1",
                    seq = 5L,
                    receivedAt = T0 + 35_000L
                )
            )
        )

        assertEquals(T0 + 35_000L, snapshot.bestReceivedAt)
        assertEquals(T0 + 19_000L, snapshot.bestDeviceTs)

        val legacyBridge = object : CloudDeviceUiBridge {
            override val gatewaySync: StateFlow<GatewaySyncUiState> =
                MutableStateFlow(GatewaySyncUiState())
        }
        assertNull(legacyBridge.cloudSnapshot.value)
    }

    @Test
    fun rtdbParsers_handleValidPartialUnknownModeAndMalformedInputsSafely() {
        // Unknown mode degrades gracefully to null without throwing; out-of-range temp is filtered out
        val parsedGateway = RtdbDeviceRepository.parseGatewayStateValues(
            rawOnline = true,
            rawReportedBy = "uA",
            rawReceivedAt = T0,
            rawDeviceTs = T0 - 100L,
            rawTemp = 29,
            rawHum = 120, // invalid (>100) -> filtered to null
            rawMotion = true,
            rawLight = false,
            rawDoor = true,
            rawAlarm = false,
            rawMonitoring = true,
            rawMode = "UNKNOWN_MODE"
        )
        assertNotNull(parsedGateway)
        assertEquals(true, parsedGateway!!.online)
        assertEquals(29, parsedGateway.temp)
        assertNull(parsedGateway.hum)
        assertNull(parsedGateway.mode)

        // Missing receivedAt or online -> null
        assertNull(
            RtdbDeviceRepository.parseGatewayStateValues(
                rawOnline = null,
                rawReportedBy = "uA",
                rawReceivedAt = T0,
                rawDeviceTs = null,
                rawTemp = 25,
                rawHum = 60,
                rawMotion = null,
                rawLight = null,
                rawDoor = null,
                rawAlarm = null,
                rawMonitoring = null,
                rawMode = "HOME"
            )
        )

        // Telemetry with both temp and hum null/out-of-range -> null
        assertNull(RtdbDeviceRepository.parseTelemetryValues("202611212359_g", null, 101, T0, null))
        assertNotNull(RtdbDeviceRepository.parseTelemetryValues("202611212359_d", 26, null, T0, T0))

        // Event descending sort orders by (receivedAt, seq, id) descending
        val e1 = CloudEventRecord("g_s1_1", "armed", "info", "s1", 1L, T0)
        val e2 = CloudEventRecord("g_s1_2", "motion", "warn", "s1", 2L, T0)
        val e3 = CloudEventRecord("g_s1_3", "rain_started", "warn", "s1", 3L, T0 + 1_000L)
        val sorted = RtdbDeviceRepository.sortRecentEventsDescending(listOf(e1, e2, e3), limit = 2)
        assertEquals(listOf(e3, e2), sorted)
    }
}

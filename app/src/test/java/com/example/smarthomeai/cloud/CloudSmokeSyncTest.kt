package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.HardwareStatus
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SensorSample
import com.example.smarthomeai.data.SmartHomeState
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
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSmokeSyncTest {

    private companion object {
        const val BASE_TIME_MS: Long = 1_795_305_570_000L
        const val UID_A: String = "test_user_uA"
        const val HOME_A: String = "home_A_0001"
    }

    private fun ownerCloudState(
        uid: String = UID_A,
        homeId: String = HOME_A
    ): CloudUiState = CloudUiState(
        status = CloudStatus.READY,
        currentUid = uid,
        homes = listOf(CloudHome(id = homeId, name = "Nha Chinh", role = CloudRole.OWNER)),
        selectedHomeId = homeId
    )

    @Test
    fun buildGatewayStatePayload_includesValidSmokeFields_andFiltersOutOfRangeOrNull() {
        val ts = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER

        val validPayload = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = true,
                lastSeen = BASE_TIME_MS,
                mode = HomeMode.HOME,
                temp = 29,
                hum = 62,
                smokePpm = 0,
                smokeAlert = false,
                window = true,
                fan = false
            ),
            reportedByUid = UID_A,
            timestampValue = ts
        )
        @Suppress("UNCHECKED_CAST")
        val validValues = validPayload["values"] as Map<String, Any>
        assertEquals(0, validValues["smokePpm"])
        assertEquals(false, validValues["smokeAlert"])
        assertEquals(true, validValues["window"])
        assertEquals(false, validValues["fan"])

        val upperBoundPayload = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = true,
                lastSeen = BASE_TIME_MS,
                mode = HomeMode.AWAY,
                smokePpm = 10_000,
                smokeAlert = true,
                window = true,
                fan = true
            ),
            reportedByUid = UID_A,
            timestampValue = ts
        )
        @Suppress("UNCHECKED_CAST")
        val upperValues = upperBoundPayload["values"] as Map<String, Any>
        assertEquals(10_000, upperValues["smokePpm"])
        assertEquals(true, upperValues["smokeAlert"])
        assertEquals(true, upperValues["window"])
        assertEquals(true, upperValues["fan"])

        val outOfRangePayload = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = true,
                lastSeen = BASE_TIME_MS,
                mode = HomeMode.HOME,
                smokePpm = -1,
                smokeAlert = null,
                window = null,
                fan = null
            ),
            reportedByUid = UID_A,
            timestampValue = ts
        )
        @Suppress("UNCHECKED_CAST")
        val outValues = outOfRangePayload["values"] as Map<String, Any>
        assertFalse(outValues.containsKey("smokePpm"))
        assertFalse(outValues.containsKey("smokeAlert"))
        assertFalse(outValues.containsKey("window"))
        assertFalse(outValues.containsKey("fan"))

        val overMaxPayload = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = true,
                lastSeen = BASE_TIME_MS,
                mode = HomeMode.HOME,
                smokePpm = 10_001
            ),
            reportedByUid = UID_A,
            timestampValue = ts
        )
        @Suppress("UNCHECKED_CAST")
        val overMaxValues = overMaxPayload["values"] as Map<String, Any>
        assertFalse(overMaxValues.containsKey("smokePpm"))
    }

    @Test
    fun classifySeverity_classifiesSmokeEventsAccurately() {
        assertEquals(EventSeverity.CRITICAL, CloudMapper.classifySeverity("smoke_detected"))
        assertEquals(EventSeverity.CRITICAL, CloudMapper.classifySeverity("smoke_auto_vent"))

        val infoSmokeEvents = listOf(
            "smoke_cleared",
            "window_opened",
            "window_closed",
            "fan_on",
            "fan_off",
            "smoke_rule_changed"
        )
        for (kind in infoSmokeEvents) {
            assertEquals("Expected INFO for $kind", EventSeverity.INFO, CloudMapper.classifySeverity(kind))
        }
    }

    @Test
    fun buildTelemetryPayload_supportsTempHumSmokePpm_andSmokePpmOnly() {
        val ts = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER

        val combined = CloudMapper.buildTelemetryPayload(
            sample = SensorSample(
                timestamp = BASE_TIME_MS,
                temperature = 30,
                humidity = 65,
                smokePpm = 280
            ),
            reportedByUid = UID_A,
            timestampValue = ts
        )
        assertNotNull(combined)
        assertEquals(
            mapOf("temp" to 30, "hum" to 65, "smokePpm" to 280),
            combined!!.second["values"]
        )

        val smokeOnly = CloudMapper.buildTelemetryPayload(
            timestampMs = BASE_TIME_MS,
            temp = null,
            hum = null,
            reportedByUid = UID_A,
            timestampValue = ts,
            smokePpm = 450
        )
        assertNotNull(smokeOnly)
        assertEquals(mapOf("smokePpm" to 450), smokeOnly!!.second["values"])

        assertNull(
            CloudMapper.buildTelemetryPayload(
                timestampMs = BASE_TIME_MS,
                temp = null,
                hum = null,
                reportedByUid = UID_A,
                timestampValue = ts,
                smokePpm = -5
            )
        )
        assertNull(
            CloudMapper.buildTelemetryPayload(
                timestampMs = BASE_TIME_MS,
                temp = null,
                hum = null,
                reportedByUid = UID_A,
                timestampValue = ts,
                smokePpm = 10_001
            )
        )
    }

    @Test
    fun buildConfigPayload_supportsSmokeLimitAndSmokeAuto_andValidatesBounds() {
        val ts = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
        val payload = CloudMapper.buildConfigPayload(
            previousRev = 1L,
            tempLimit = 35,
            humLimit = 80,
            coldLimit = 18,
            autoAlarm = true,
            updatedByUid = UID_A,
            timestampValue = ts,
            smokeLimit = 350,
            smokeAuto = false
        )
        assertEquals(2L, payload["rev"])
        assertEquals(350, payload["smokeLimit"])
        assertEquals(false, payload["smokeAuto"])

        for (badSmokeLimit in listOf(49, 5001, -10)) {
            try {
                CloudMapper.buildConfigPayload(
                    previousRev = 1L,
                    tempLimit = 35,
                    humLimit = 80,
                    coldLimit = 18,
                    autoAlarm = true,
                    updatedByUid = UID_A,
                    timestampValue = ts,
                    smokeLimit = badSmokeLimit,
                    smokeAuto = true
                )
                fail("Expected IllegalArgumentException for smokeLimit=$badSmokeLimit")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun parseConfigValues_fallsBackToDefaultsForLegacyRecords_andRejectsOutOfRangeSmokeLimit() {
        val legacy = RtdbDeviceRepository.parseConfigValues(
            rawRev = 3L,
            rawTempLimit = 35,
            rawHumLimit = 80,
            rawColdLimit = 18,
            rawAutoAlarm = true,
            rawUpdatedBy = UID_A,
            rawUpdatedAt = BASE_TIME_MS
        )
        assertNotNull(legacy)
        assertEquals(300, legacy!!.smokeLimit)
        assertEquals(true, legacy.smokeAuto)

        val modern = RtdbDeviceRepository.parseConfigValues(
            rawRev = 4L,
            rawTempLimit = 35,
            rawHumLimit = 80,
            rawColdLimit = 18,
            rawAutoAlarm = true,
            rawUpdatedBy = UID_A,
            rawUpdatedAt = BASE_TIME_MS,
            rawSmokeLimit = 450,
            rawSmokeAuto = false
        )
        assertNotNull(modern)
        assertEquals(450, modern!!.smokeLimit)
        assertEquals(false, modern.smokeAuto)

        assertNull(
            RtdbDeviceRepository.parseConfigValues(
                rawRev = 4L,
                rawTempLimit = 35,
                rawHumLimit = 80,
                rawColdLimit = 18,
                rawAutoAlarm = true,
                rawUpdatedBy = UID_A,
                rawUpdatedAt = BASE_TIME_MS,
                rawSmokeLimit = 49,
                rawSmokeAuto = true
            )
        )
        assertNull(
            RtdbDeviceRepository.parseConfigValues(
                rawRev = 4L,
                rawTempLimit = 35,
                rawHumLimit = 80,
                rawColdLimit = 18,
                rawAutoAlarm = true,
                rawUpdatedBy = UID_A,
                rawUpdatedAt = BASE_TIME_MS,
                rawSmokeLimit = 5001,
                rawSmokeAuto = true
            )
        )
        assertNull(
            RtdbDeviceRepository.parseConfigValues(
                rawRev = 4L,
                rawTempLimit = 35,
                rawHumLimit = 80,
                rawColdLimit = 18,
                rawAutoAlarm = true,
                rawUpdatedBy = UID_A,
                rawUpdatedAt = BASE_TIME_MS,
                rawSmokeLimit = 300,
                rawSmokeAuto = "true"
            )
        )
    }

    @Test
    fun parseGatewayStateAndTelemetryValues_parsesSmokeFieldsSafely() {
        val state = RtdbDeviceRepository.parseGatewayStateValues(
            rawOnline = true,
            rawReportedBy = UID_A,
            rawReceivedAt = BASE_TIME_MS,
            rawDeviceTs = BASE_TIME_MS - 500L,
            rawTemp = 28,
            rawHum = 60,
            rawMotion = false,
            rawLight = true,
            rawDoor = false,
            rawAlarm = false,
            rawMonitoring = true,
            rawMode = "HOME",
            rawSmokePpm = 320,
            rawSmokeAlert = true,
            rawWindow = true,
            rawFan = true
        )
        assertNotNull(state)
        assertEquals(320, state!!.smokePpm)
        assertEquals(true, state.smokeAlert)
        assertEquals(true, state.window)
        assertEquals(true, state.fan)

        val telemetrySmokeOnly = RtdbDeviceRepository.parseTelemetryValues(
            recordId = "202611261500_g",
            rawTemp = null,
            rawHum = null,
            rawReceivedAt = BASE_TIME_MS,
            rawDeviceTs = BASE_TIME_MS - 100L,
            rawSmokePpm = 280
        )
        assertNotNull(telemetrySmokeOnly)
        assertEquals(280, telemetrySmokeOnly!!.smokePpm)
        assertNull(telemetrySmokeOnly.temp)
        assertNull(telemetrySmokeOnly.hum)
    }

    @Test
    fun cloudGatewaySync_pushesSmokeStateChanges_andConfigRevOnSmokeLimitChange() = runTest {
        val repo = FakeCloudDeviceRepository(initialUid = UID_A)
        val dev = CloudDevice("dev_smoke_01", "ESP Smoke", "esp32-smarthome", DeviceStatus.ACTIVE, 100L, UID_A)
        repo.seedDevice(HOME_A, dev)
        repo.seedConfig(
            HOME_A,
            dev.id,
            CloudConfigState(
                rev = 1L,
                tempLimit = 35,
                humLimit = 80,
                coldLimit = 18,
                autoAlarm = true,
                smokeLimit = 300,
                smokeAuto = true
            )
        )

        fun now(): Long = BASE_TIME_MS + testScheduler.currentTime

        val initialState = SmartHomeState(
            esp32Online = true,
            lastSeen = now(),
            temperature = 27,
            humidity = 60,
            smokeLimit = 300,
            smokeAutoVentilation = true,
            hardware = HardwareStatus(
                profile = "mq2-ventilation",
                hasMq2 = true,
                smokePpm = 120,
                smokeAlert = false,
                hasWindow = true,
                windowOpen = false,
                hasFan = true,
                fanOn = false,
                smokeAutoEnabled = true,
                smokeThresholdPpm = 300
            )
        )

        val inputFlow = MutableStateFlow(initialState.toGatewayInput())
        val sync = CloudGatewaySync(
            scope = backgroundScope,
            cloudStateFlow = MutableStateFlow(ownerCloudState()),
            gatewayInputFlow = inputFlow,
            deviceRepository = repo,
            timeProvider = { now() }
        )
        runCurrent()

        assertEquals(1, repo.publishGatewayCalls.get())
        assertEquals(0, repo.publishConfigCalls.get())
        @Suppress("UNCHECKED_CAST")
        val initialValues = repo.getLatestGatewayState(HOME_A, dev.id)?.get("values") as Map<String, Any>
        assertEquals(120, initialValues["smokePpm"])
        assertEquals(false, initialValues["smokeAlert"])
        assertEquals(false, initialValues["window"])
        assertEquals(false, initialValues["fan"])

        // Change smokePpm, smokeAlert, windowOpen, fanOn and smokeLimit -> state debounces 2s, config pushes immediately with rev+1
        advanceTimeBy(2_000L)
        val hazardState = initialState.copy(
            lastSeen = now(),
            smokeLimit = 400,
            hardware = initialState.hardware!!.copy(
                smokePpm = 460,
                smokeAlert = true,
                windowOpen = true,
                fanOn = true
            )
        )
        inputFlow.value = hazardState.toGatewayInput()
        runCurrent()

        assertEquals(2, repo.publishGatewayCalls.get())
        @Suppress("UNCHECKED_CAST")
        val hazardValues = repo.getLatestGatewayState(HOME_A, dev.id)?.get("values") as Map<String, Any>
        assertEquals(460, hazardValues["smokePpm"])
        assertEquals(true, hazardValues["smokeAlert"])
        assertEquals(true, hazardValues["window"])
        assertEquals(true, hazardValues["fan"])

        assertEquals(1, repo.publishConfigCalls.get())
        val updatedConfig = repo.getConfig(HOME_A, dev.id)
        assertNotNull(updatedConfig)
        assertEquals(2L, updatedConfig!!.rev)
        assertEquals(400, updatedConfig.smokeLimit)
        assertEquals(true, updatedConfig.smokeAuto)
        assertEquals(GatewaySyncPhase.OK, sync.gatewaySync.value.phase)
    }
}

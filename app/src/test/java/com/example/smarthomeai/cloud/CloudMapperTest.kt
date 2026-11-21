package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.DeviceEvent
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Locale

class CloudMapperTest {

    private val timestampSentinel = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
    private val sampleUid = "uA_owner_1"

    @Test
    fun toCloudStream_hashesEsp32StreamIdWithHyphenAndLongLengthTo16LowercaseHex() {
        val rawFirmwareStream = "a1b2c3d4e5f6-98765432"
        val cloudStream = CloudMapper.toCloudStream(rawFirmwareStream)

        val expected = MessageDigest.getInstance("SHA-256")
            .digest(rawFirmwareStream.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
            .take(16)

        assertEquals(16, cloudStream.length)
        assertTrue(Regex("^[0-9a-f]{16}$").matches(cloudStream))
        assertTrue(RtdbPaths.isValidCloudStream(cloudStream))
        assertEquals(expected, cloudStream)
        assertEquals(cloudStream, CloudMapper.toCloudStream("  $rawFirmwareStream  "))

        for (blank in listOf("", "   ", "\t\n")) {
            try {
                CloudMapper.toCloudStream(blank)
                fail("Expected IllegalArgumentException for blank rawStream")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun buildDeviceRegistrationPayload_matchesRulesAndRejectsDeviceKeyToken() {
        val payload = CloudMapper.buildDeviceRegistrationPayload(
            createdByUid = sampleUid,
            timestampValue = timestampSentinel
        )

        assertEquals(setOf("name", "model", "status", "createdAt", "createdBy"), payload.keys)
        assertEquals("ESP32 Smart Home", payload["name"])
        assertEquals("esp32-smarthome", payload["model"])
        assertEquals("active", payload["status"])
        assertEquals(timestampSentinel, payload["createdAt"])
        assertEquals(sampleUid, payload["createdBy"])

        for (badName in listOf("", "   ", "X".repeat(61), "My deviceKey ESP", "DEVICEKEY")) {
            try {
                CloudMapper.buildDeviceRegistrationPayload(sampleUid, timestampSentinel, name = badName)
                fail("Expected IllegalArgumentException for device name='$badName'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun buildGatewayStatePayload_whitelists8Fields_respectsTempHumBounds_andOmitsDeviceTsWhenZero() {
        val validPayload = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = true,
                lastSeen = 1795305570000L,
                light = true,
                door = false,
                alarm = true,
                motion = true,
                monitoring = true,
                mode = HomeMode.AWAY,
                temp = -49,
                hum = 0
            ),
            reportedByUid = sampleUid,
            timestampValue = timestampSentinel
        )

        assertEquals(
            setOf("online", "reportedBy", "receivedAt", "values", "deviceTs"),
            validPayload.keys
        )
        assertEquals(true, validPayload["online"])
        assertEquals(sampleUid, validPayload["reportedBy"])
        assertEquals(timestampSentinel, validPayload["receivedAt"])
        assertEquals(1795305570000L, validPayload["deviceTs"])

        @Suppress("UNCHECKED_CAST")
        val values = validPayload["values"] as Map<String, Any>
        assertEquals(
            setOf("light", "door", "alarm", "motion", "monitoring", "mode", "temp", "hum"),
            values.keys
        )
        assertEquals("AWAY", values["mode"])
        assertEquals(-49, values["temp"])
        assertEquals(0, values["hum"])

        // Upper inclusive/exclusive bounds: temp=149 valid, hum=100 valid; out-of-range omitted; deviceTs=0 omitted
        val upperOk = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = false,
                lastSeen = 0L,
                mode = HomeMode.SLEEP,
                temp = 149,
                hum = 100
            ),
            reportedByUid = sampleUid,
            timestampValue = timestampSentinel
        )
        assertFalse(upperOk.containsKey("deviceTs"))
        @Suppress("UNCHECKED_CAST")
        val upperValues = upperOk["values"] as Map<String, Any>
        assertEquals(149, upperValues["temp"])
        assertEquals(100, upperValues["hum"])
        assertEquals("SLEEP", upperValues["mode"])

        val outOfRange = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(
                online = true,
                lastSeen = -1L,
                mode = "HOME",
                temp = -50,
                hum = 101
            ),
            reportedByUid = sampleUid,
            timestampValue = timestampSentinel
        )
        assertFalse(outOfRange.containsKey("deviceTs"))
        @Suppress("UNCHECKED_CAST")
        val outValues = outOfRange["values"] as Map<String, Any>
        assertEquals(setOf("light", "door", "alarm", "motion", "monitoring", "mode"), outValues.keys)

        val tempTooHighHumNegative = CloudMapper.buildGatewayStatePayload(
            snapshot = GatewaySnapshot(online = true, lastSeen = 1000L, temp = 150, hum = -1),
            reportedByUid = sampleUid,
            timestampValue = timestampSentinel
        )
        @Suppress("UNCHECKED_CAST")
        val strippedValues = tempTooHighHumNegative["values"] as Map<String, Any>
        assertFalse(strippedValues.containsKey("temp"))
        assertFalse(strippedValues.containsKey("hum"))

        // Verify forbidden CD-2 / secret tokens never exist anywhere in serialized payload
        val serialized = validPayload.toString()
        for (forbidden in listOf("raining", "clothesRetracted", "rainRetractEnabled", "obstacle", "distanceCm", "esp32Ip", "deviceKey")) {
            assertFalse("Unexpected forbidden key '$forbidden'", serialized.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun buildConfigPayload_computesNextRevAndEnforcesRuleRanges() {
        val initial = CloudMapper.buildConfigPayload(
            previousRev = null,
            tempLimit = 0,
            humLimit = 100,
            coldLimit = -10,
            autoAlarm = true,
            updatedByUid = sampleUid,
            timestampValue = timestampSentinel
        )
        assertEquals(
            setOf("rev", "updatedBy", "updatedAt", "tempLimit", "humLimit", "coldLimit", "autoAlarm"),
            initial.keys
        )
        assertEquals(1L, initial["rev"])
        assertEquals(0, initial["tempLimit"])
        assertEquals(100, initial["humLimit"])
        assertEquals(-10, initial["coldLimit"])
        assertEquals(true, initial["autoAlarm"])

        val next = CloudMapper.buildConfigPayload(
            previousRev = 4L,
            tempLimit = 80,
            humLimit = 0,
            coldLimit = 40,
            autoAlarm = false,
            updatedByUid = sampleUid,
            timestampValue = timestampSentinel
        )
        assertEquals(5L, next["rev"])

        val invalidConfigs = listOf(
            Triple(-1, 50, 10),
            Triple(81, 50, 10),
            Triple(35, -1, 10),
            Triple(35, 101, 10),
            Triple(35, 50, -11),
            Triple(35, 50, 41)
        )
        for ((t, h, c) in invalidConfigs) {
            try {
                CloudMapper.buildConfigPayload(1L, t, h, c, true, sampleUid, timestampSentinel)
                fail("Expected IllegalArgumentException for temp=$t, hum=$h, cold=$c")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun buildTelemetryPayload_usesUtcKeyAndFiltersInvalidTempOrHum() {
        val sample = SensorSample(timestamp = 1795305570000L, temperature = 29, humidity = 65)
        val built = CloudMapper.buildTelemetryPayload(sample, sampleUid, timestampSentinel)
        assertNotNull(built)
        val (recordId, payload) = built!!

        assertEquals("202611212359_g", recordId)
        assertEquals(setOf("source", "reportedBy", "receivedAt", "deviceTs", "values"), payload.keys)
        assertEquals("gateway", payload["source"])
        assertEquals(sampleUid, payload["reportedBy"])
        assertEquals(timestampSentinel, payload["receivedAt"])
        assertEquals(1795305570000L, payload["deviceTs"])
        assertEquals(mapOf("temp" to 29, "hum" to 65), payload["values"])

        // Partial valid sample keeps only valid metric
        val onlyTemp = CloudMapper.buildTelemetryPayload(1795305605000L, temp = 25, hum = 101, sampleUid, timestampSentinel)
        assertNotNull(onlyTemp)
        assertEquals("202611220000_g", onlyTemp!!.first)
        assertEquals(mapOf("temp" to 25), onlyTemp.second["values"])

        val onlyHum = CloudMapper.buildTelemetryPayload(1795305605000L, temp = -50, hum = 55, sampleUid, timestampSentinel)
        assertNotNull(onlyHum)
        assertEquals(mapOf("hum" to 55), onlyHum!!.second["values"])

        // Both invalid or null returns null
        assertNull(CloudMapper.buildTelemetryPayload(1795305605000L, temp = 150, hum = -1, sampleUid, timestampSentinel))
        assertNull(CloudMapper.buildTelemetryPayload(1795305605000L, temp = null, hum = null, sampleUid, timestampSentinel))
    }

    @Test
    fun buildEventPayload_andClassifySeverity_coverAllSeverityTiersAndRules() {
        val deviceEvent = DeviceEvent(
            id = "a1b2c3d4e5f6-98765432:42",
            seq = 42L,
            stream = "a1b2c3d4e5f6-98765432",
            boot = 3L,
            incident = 7L,
            kind = "motion_alarm",
            timestamp = 1795305570000L,
            uptime = 12345L
        )
        val (eventId, payload) = CloudMapper.buildEventPayload(
            input = GatewayEventInput.fromDeviceEvent(deviceEvent),
            reportedByUid = sampleUid,
            timestampValue = timestampSentinel
        )

        val expectedStream = CloudMapper.toCloudStream("a1b2c3d4e5f6-98765432")
        assertEquals("g_${expectedStream}_42", eventId)
        assertTrue(RtdbPaths.isValidGatewayEventId(eventId))
        assertEquals(
            setOf("type", "severity", "stream", "seq", "source", "reportedBy", "receivedAt", "detail", "deviceTs"),
            payload.keys
        )
        assertEquals("motion_alarm", payload["type"])
        assertEquals("critical", payload["severity"])
        assertEquals(expectedStream, payload["stream"])
        assertEquals(42L, payload["seq"])
        assertEquals("gateway", payload["source"])
        assertEquals(sampleUid, payload["reportedBy"])
        assertEquals(timestampSentinel, payload["receivedAt"])
        assertEquals("boot=3;incident=7", payload["detail"])
        assertEquals(1795305570000L, payload["deviceTs"])

        // When timestamp == 0L, deviceTs is omitted
        val (_, noTsPayload) = CloudMapper.buildEventPayload(
            input = GatewayEventInput(stream = "a1b2c3d4e5f6-98765432", seq = 0L, kind = "restarted", timestamp = 0L, boot = 1L, incident = 0L),
            reportedByUid = sampleUid,
            timestampValue = timestampSentinel
        )
        assertFalse(noTsPayload.containsKey("deviceTs"))
        assertEquals("info", noTsPayload["severity"])
        assertEquals(0L, noTsPayload["seq"])

        // Severity mapping checks
        for (critical in listOf("alarm_started", "motion_alarm", "proximity_alarm")) {
            assertEquals(EventSeverity.CRITICAL, CloudMapper.classifySeverity(critical))
        }
        val warnKinds = listOf(
            "rain_started", "servo_overdue", "servo_blocked", "exit_waiting",
            "motion_entry", "motion_waiting", "alarm_silent", "clothes_rain_retract",
            "proximity_detected", "motion_detected"
        )
        for (warn in warnKinds) {
            assertEquals(EventSeverity.WARN, CloudMapper.classifySeverity(warn))
        }
        for (info in listOf("restarted", "armed", "home_selected", "clothes_retracted", "custom_event")) {
            assertEquals(EventSeverity.INFO, CloudMapper.classifySeverity(info))
        }

        // Reject deviceKey token in event kind
        try {
            CloudMapper.buildEventPayload(
                input = GatewayEventInput(stream = "stream-1", seq = 1L, kind = "leak_deviceKey_here"),
                reportedByUid = sampleUid,
                timestampValue = timestampSentinel
            )
            fail("Expected IllegalArgumentException when event kind contains deviceKey")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun deviceRepositoryPureEvaluators_implementD1ResolutionAndSafeParsing() {
        assertNull(RtdbDeviceRepository.evaluateDevicesSnapshot(emptyList()))

        val revoked1 = CloudDevice("dev_rev_02", "Old 2", "esp32", DeviceStatus.REVOKED, 100L, sampleUid)
        val revoked2 = CloudDevice("dev_rev_01", "Old 1", "esp32", DeviceStatus.REVOKED, 200L, sampleUid)
        val revokedOnly = RtdbDeviceRepository.evaluateDevicesSnapshot(listOf(revoked1, revoked2))
        assertEquals(DeviceResolveResult.RevokedOnly(listOf("dev_rev_01", "dev_rev_02")), revokedOnly)

        val activeLater = CloudDevice("dev_act_01", "Act 1", "esp32", DeviceStatus.ACTIVE, 500L, sampleUid)
        val activeEarlierB = CloudDevice("dev_act_03", "Act 3", "esp32", DeviceStatus.ACTIVE, 200L, sampleUid)
        val activeEarlierA = CloudDevice("dev_act_02", "Act 2", "esp32", DeviceStatus.ACTIVE, 200L, sampleUid)
        val multiActive = RtdbDeviceRepository.evaluateDevicesSnapshot(
            listOf(revoked1, activeLater, activeEarlierB, activeEarlierA)
        )
        assertEquals(DeviceResolveResult.Active(activeEarlierA, hasMultipleActive = true), multiActive)

        val singleActive = RtdbDeviceRepository.evaluateDevicesSnapshot(listOf(revoked1, activeLater))
        assertEquals(DeviceResolveResult.Active(activeLater, hasMultipleActive = false), singleActive)

        // Safe parsing rejects malformed types without throwing
        assertNull(RtdbDeviceRepository.parseDeviceEntry("short", "ESP", "esp32", "active", 1L, sampleUid))
        assertNull(RtdbDeviceRepository.parseDeviceEntry("dev_valid1", "ESP", "esp32", mapOf("s" to "active"), 1L, sampleUid))
        assertNull(RtdbDeviceRepository.parseConfigValues("badRev", 35, 80, 18, true, sampleUid, 1L))
        assertNotNull(RtdbDeviceRepository.parseConfigValues(2L, 35, 80, 18, true, sampleUid, 100L))
        assertNull(RtdbDeviceRepository.parseEventCursorEntry("bad-stream", 10L))
        assertNull(RtdbDeviceRepository.parseEventCursorEntry("0123456789abcdef", -1L))
        assertEquals("0123456789abcdef" to 10L, RtdbDeviceRepository.parseEventCursorEntry("0123456789abcdef", 10))
    }
}

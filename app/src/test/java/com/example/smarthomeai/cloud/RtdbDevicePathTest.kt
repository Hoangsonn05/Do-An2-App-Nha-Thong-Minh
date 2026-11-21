package com.example.smarthomeai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

class RtdbDevicePathTest {

    @Test
    fun buildsExpectedDeviceAndDeviceDataPaths() {
        val homeId = "home_A001"
        val deviceId = "dev_00001"
        val recordId = "202611211341_g"
        val eventId = "g_0123456789abcdef_42"

        assertEquals("homes/home_A001/devices", RtdbPaths.homeDevicesPath(homeId))
        assertEquals("homes/home_A001/devices/dev_00001", RtdbPaths.homeDevicePath(homeId, deviceId))
        assertEquals(
            "homes/home_A001/deviceData/dev_00001/state/gateway",
            RtdbPaths.deviceGatewayStatePath(homeId, deviceId)
        )
        assertEquals(
            "homes/home_A001/deviceData/dev_00001/config",
            RtdbPaths.deviceConfigPath(homeId, deviceId)
        )
        assertEquals(
            "homes/home_A001/deviceData/dev_00001/telemetry",
            RtdbPaths.deviceTelemetryPath(homeId, deviceId)
        )
        assertEquals(
            "homes/home_A001/deviceData/dev_00001/telemetry/202611211341_g",
            RtdbPaths.deviceTelemetryRecordPath(homeId, deviceId, recordId)
        )
        assertEquals(
            "homes/home_A001/deviceData/dev_00001/events",
            RtdbPaths.deviceEventsPath(homeId, deviceId)
        )
        assertEquals(
            "homes/home_A001/deviceData/dev_00001/events/g_0123456789abcdef_42",
            RtdbPaths.deviceEventPath(homeId, deviceId, eventId)
        )
    }

    @Test
    fun telemetryRecordId_usesUtcEvenAcrossMidnightBoundaryAndNonUtcHostTimezone() {
        // 2026-11-21T23:59:30Z -> 1795305570000L; 2026-11-22T00:00:05Z -> 1795305605000L
        val beforeMidnightUtcMs = 1795305570000L
        val afterMidnightUtcMs = 1795305605000L

        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            assertEquals("202611212359_g", RtdbPaths.telemetryRecordId(beforeMidnightUtcMs))
            assertEquals("202611220000_g", RtdbPaths.telemetryRecordId(afterMidnightUtcMs))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun telemetryRecordId_rejectsNonPositiveTimestampsAndInvalidIds() {
        for (badTs in listOf(0L, -1L, -100_000L)) {
            try {
                RtdbPaths.telemetryRecordId(badTs)
                fail("Expected IllegalArgumentException for epochMs=$badTs")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }

        val badRecordIds = listOf(
            "202611212359_d",
            "20261121235_g",
            "2026112123590_g",
            "202611212359",
            "20261121235a_g",
            "202611212359_g/extra"
        )
        for (badId in badRecordIds) {
            assertFalse("Expected '$badId' to be rejected", RtdbPaths.isValidTelemetryRecordId(badId))
            try {
                RtdbPaths.deviceTelemetryRecordPath("home_A001", "dev_00001", badId)
                fail("Expected IllegalArgumentException for recordId='$badId'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun eventIdAndCloudStream_enforceRulesRegexAndSeqBounds() {
        assertEquals("g_a1B2c3D4e5F60718_0", RtdbPaths.eventId("a1B2c3D4e5F60718", 0L))
        assertEquals("g_stream1_9999999999", RtdbPaths.eventId("stream1", 9_999_999_999L))

        val badStreams = listOf(
            "",
            "abc-123",
            "abc_123",
            "a".repeat(17),
            "stream/1",
            "stream.1"
        )
        for (bad in badStreams) {
            assertFalse("Expected cloudStream '$bad' to be rejected", RtdbPaths.isValidCloudStream(bad))
            try {
                RtdbPaths.eventId(bad, 1L)
                fail("Expected IllegalArgumentException for cloudStream='$bad'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }

        for (badSeq in listOf(-1L, -100L, 10_000_000_000L)) {
            assertFalse("Expected seq '$badSeq' to be rejected", RtdbPaths.isValidEventSeq(badSeq))
            try {
                RtdbPaths.eventId("0123456789abcdef", badSeq)
                fail("Expected IllegalArgumentException for seq=$badSeq")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }

        val badEventIds = listOf(
            "d_0123456789abcdef_1",
            "g_abc-123_1",
            "g_0123456789abcdef_01",
            "g_${"a".repeat(17)}_1",
            "g_0123456789abcdef_10000000000",
            "g_0123456789abcdef"
        )
        for (badId in badEventIds) {
            assertFalse("Expected eventId '$badId' to be rejected", RtdbPaths.isValidGatewayEventId(badId))
            try {
                RtdbPaths.deviceEventPath("home_A001", "dev_00001", badId)
                fail("Expected IllegalArgumentException for eventId='$badId'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun devicePaths_rejectInvalidHomeOrDeviceIds() {
        val badIds = listOf("short", "a".repeat(65), "dev/12345", "dev.12345", "dev#12345", "dev\$12345", "dev[12345")
        for (badId in badIds) {
            assertFalse(RtdbPaths.isValidEntityId(badId))
            try {
                RtdbPaths.deviceGatewayStatePath("home_A001", badId)
                fail("Expected IllegalArgumentException for deviceId='$badId'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
            try {
                RtdbPaths.homeDevicesPath(badId)
                fail("Expected IllegalArgumentException for homeId='$badId'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(RtdbPaths.isValidEntityId("dev_00001"))
    }
}

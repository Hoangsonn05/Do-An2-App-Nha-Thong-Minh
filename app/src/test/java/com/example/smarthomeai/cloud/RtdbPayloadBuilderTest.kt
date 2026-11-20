package com.example.smarthomeai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class RtdbPayloadBuilderTest {

    private val timestampSentinel = mapOf(".sv" to "timestamp")

    @Test
    fun buildHomeMetaPayload_trimsName_matchesB0Schema_andAssertsSchemaVersionEquals1() {
        val payload = RtdbPayloadBuilder.buildHomeMetaPayload(
            name = "  Nha Chinh  ",
            ownerUid = "uA_owner_1",
            timestampValue = timestampSentinel
        )

        assertEquals(setOf("name", "ownerUid", "createdAt", "schemaVersion"), payload.keys)
        assertEquals("Nha Chinh", payload["name"])
        assertEquals("uA_owner_1", payload["ownerUid"])
        assertEquals(timestampSentinel, payload["createdAt"])
        // F10: explicitly verify schemaVersion value == 1 (not just key existence)
        assertEquals(1, payload["schemaVersion"])
    }

    @Test
    fun buildOwnerMemberPayload_matchesB0Schema() {
        val payload = RtdbPayloadBuilder.buildOwnerMemberPayload(
            ownerUid = "uA_owner_1",
            timestampValue = timestampSentinel
        )

        assertEquals(setOf("role", "addedAt", "addedBy"), payload.keys)
        assertEquals("owner", payload["role"])
        assertEquals(timestampSentinel, payload["addedAt"])
        assertEquals("uA_owner_1", payload["addedBy"])
    }

    @Test
    fun buildUserHomeRoleValue_matchesWireContract() {
        assertEquals("owner", RtdbPayloadBuilder.buildUserHomeRoleValue(CloudRole.OWNER))
        assertEquals("member", RtdbPayloadBuilder.buildUserHomeRoleValue(CloudRole.MEMBER))
        assertEquals(CloudRole.OWNER, CloudRole.fromWire("owner"))
        assertEquals(CloudRole.MEMBER, CloudRole.fromWire("member"))
        assertEquals(null, CloudRole.fromWire("admin"))
    }

    @Test
    fun validateAndNormalizeHomeName_rejectsBlankOverlongAndDeviceKeyToken() {
        val invalidNames = listOf(
            "",
            "    ",
            "\t\n",
            "X".repeat(61),
            "My deviceKey home",
            "MY DEVICEKEY HOME"
        )
        for (bad in invalidNames) {
            try {
                RtdbPayloadBuilder.buildHomeMetaPayload(bad, "uA_owner_1", timestampSentinel)
                fail("Expected IllegalArgumentException for name='$bad'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun buildPayloads_rejectForbiddenRtdbCharsInUid() {
        val badUids = listOf("uA/1", "uA.1", "uA#1", "uA\$1", "uA[1", "uA]1")
        for (badUid in badUids) {
            try {
                RtdbPayloadBuilder.buildHomeMetaPayload("Valid Home", badUid, timestampSentinel)
                fail("Expected IllegalArgumentException for uid='$badUid'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
            try {
                RtdbPayloadBuilder.buildOwnerMemberPayload(badUid, timestampSentinel)
                fail("Expected IllegalArgumentException for uid='$badUid'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun serializedCloudPayloads_neverLeakSampleDeviceKey() {
        val sampleLanDeviceKey = "SUPER_SECRET_ESP32_KEY_987654"
        val meta = RtdbPayloadBuilder.buildHomeMetaPayload("Living Home", "uA_owner_1", timestampSentinel)
        val member = RtdbPayloadBuilder.buildOwnerMemberPayload("uA_owner_1", timestampSentinel)
        val combined = "$meta|$member"
        assertFalse(combined.contains(sampleLanDeviceKey))
        assertFalse(combined.contains("deviceKey", ignoreCase = true))
    }
}

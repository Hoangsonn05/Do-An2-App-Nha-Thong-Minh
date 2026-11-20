package com.example.smarthomeai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for MIN-02: safe DataSnapshot raw-value parsing in RtdbCloudRepository.
 * Verifies malformed/unexpected RTDB value types never throw DatabaseException or ClassCastException
 * and produce InvalidData or skipped elements according to contract.
 */
class RtdbSnapshotParsingTest {

    @Test
    fun parseHomeMetaValues_validStrings_returnsFound() {
        val result = RtdbCloudRepository.parseHomeMetaValues(
            homeId = "home_A001",
            rawName = "Nha Chinh",
            rawOwnerUid = "uA"
        )
        assertEquals(HomeMetaFetchResult.Found("home_A001", "Nha Chinh", "uA"), result)
    }

    @Test
    fun parseHomeMetaValues_whenNameIsMap_returnsInvalidData() {
        val result = RtdbCloudRepository.parseHomeMetaValues(
            homeId = "home_A001",
            rawName = mapOf("nested" to "bad"),
            rawOwnerUid = "uA"
        )
        assertTrue(result is HomeMetaFetchResult.Failed)
        assertEquals(CloudError.InvalidData, (result as HomeMetaFetchResult.Failed).error)

        // Verify merger surfaces InvalidData rather than crashing
        val merged = RtdbHomeMerger.mergeHomes(
            currentUid = "uA",
            indexedHomeIds = listOf("home_A001"),
            metaResults = mapOf("home_A001" to result)
        )
        assertTrue(merged is CloudResult.Err)
        assertEquals(CloudError.InvalidData, (merged as CloudResult.Err).error)
    }

    @Test
    fun parseHomeMetaValues_whenOwnerUidHasWrongType_returnsInvalidData() {
        val wrongTypes: List<Any?> = listOf(
            12345L,
            mapOf("uid" to "uA"),
            listOf("uA"),
            true,
            "",
            "   ",
            null
        )
        for (badOwner in wrongTypes) {
            val result = RtdbCloudRepository.parseHomeMetaValues(
                homeId = "home_A001",
                rawName = "Nha Chinh",
                rawOwnerUid = badOwner
            )
            assertTrue(
                "Expected Failed(InvalidData) for ownerUid=$badOwner, got $result",
                result is HomeMetaFetchResult.Failed && result.error == CloudError.InvalidData
            )
        }
    }

    @Test
    fun parseMemberValues_whenAddedAtIsString_parsesMemberWithNullAddedAt() {
        val member = RtdbCloudRepository.parseMemberValues(
            uid = "uB",
            rawRole = "member",
            rawAddedAt = "1700000000000",
            rawAddedBy = "uA"
        )
        assertEquals(
            CloudMember(uid = "uB", role = CloudRole.MEMBER, addedAt = null, addedBy = "uA"),
            member
        )
    }

    @Test
    fun parseMemberValues_whenRoleIsMap_skipsElement() {
        val member = RtdbCloudRepository.parseMemberValues(
            uid = "uB",
            rawRole = mapOf("role" to "member"),
            rawAddedAt = 1700000000000L,
            rawAddedBy = "uA"
        )
        assertNull(member)
    }

    @Test
    fun parseMemberValues_whenRoleIsWrongTypeOrUnknown_skipsElement() {
        assertNull(RtdbCloudRepository.parseMemberValues("uB", 123L, 1000L, "uA"))
        assertNull(RtdbCloudRepository.parseMemberValues("uB", true, 1000L, "uA"))
        assertNull(RtdbCloudRepository.parseMemberValues("uB", "admin", 1000L, "uA"))
        assertNull(RtdbCloudRepository.parseMemberValues("uB", null, 1000L, "uA"))
        assertNull(RtdbCloudRepository.parseMemberValues(null, "member", 1000L, "uA"))
        assertNull(RtdbCloudRepository.parseMemberValues("  ", "member", 1000L, "uA"))
    }

    @Test
    fun parseMemberValues_whenAddedByIsMap_setsAddedByToNullWithoutCrash() {
        val member = RtdbCloudRepository.parseMemberValues(
            uid = "uA",
            rawRole = "owner",
            rawAddedAt = 1700000000000L,
            rawAddedBy = mapOf("uid" to "uA")
        )
        assertEquals(
            CloudMember(uid = "uA", role = CloudRole.OWNER, addedAt = 1700000000000L, addedBy = null),
            member
        )
    }

    @Test
    fun safeLong_acceptsNumbersAndRejectsNonNumbersAndNonFinite() {
        assertEquals(1700000000000L, RtdbCloudRepository.safeLong(1700000000000L))
        assertEquals(42L, RtdbCloudRepository.safeLong(42))
        assertEquals(7L, RtdbCloudRepository.safeLong(7.toShort()))
        assertEquals(3L, RtdbCloudRepository.safeLong(3.toByte()))
        assertEquals(1000L, RtdbCloudRepository.safeLong(1000.0))
        assertNull(RtdbCloudRepository.safeLong(Double.NaN))
        assertNull(RtdbCloudRepository.safeLong(Double.POSITIVE_INFINITY))
        assertNull(RtdbCloudRepository.safeLong(Float.NEGATIVE_INFINITY))
        assertNull(RtdbCloudRepository.safeLong("1000"))
        assertNull(RtdbCloudRepository.safeLong(mapOf("ts" to 1000)))
        assertNull(RtdbCloudRepository.safeLong(true))
        assertNull(RtdbCloudRepository.safeLong(null))
    }
}

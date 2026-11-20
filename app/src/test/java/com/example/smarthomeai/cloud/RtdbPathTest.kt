package com.example.smarthomeai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RtdbPathTest {

    @Test
    fun buildsExpectedPathsMatchingB0Schema() {
        assertEquals("userHomes/uA_123", RtdbPaths.userHomesPath("uA_123"))
        assertEquals("userHomes/uA_123/home_A001", RtdbPaths.userHomePath("uA_123", "home_A001"))
        assertEquals("homes/home_A001/meta", RtdbPaths.homeMetaPath("home_A001"))
        assertEquals("homes/home_A001/members", RtdbPaths.homeMembersPath("home_A001"))
        assertEquals("homes/home_A001/members/uA_123", RtdbPaths.homeMemberPath("home_A001", "uA_123"))
    }

    @Test
    fun entityIdValidation_enforces8To64AllowedChars() {
        assertTrue(RtdbPaths.isValidEntityId("home_A001"))
        assertTrue(RtdbPaths.isValidEntityId("-N1234567890abcdef_Z"))
        assertTrue(RtdbPaths.isValidEntityId("A".repeat(8)))
        assertTrue(RtdbPaths.isValidEntityId("A".repeat(64)))

        assertFalse(RtdbPaths.isValidEntityId("1"))
        assertFalse(RtdbPaths.isValidEntityId("1234567"))
        assertFalse(RtdbPaths.isValidEntityId("A".repeat(65)))
        assertFalse(RtdbPaths.isValidEntityId("home with space"))
    }

    @Test
    fun uidAndSegments_rejectAllForbiddenRtdbCharacters() {
        val forbiddenSamples = listOf(
            "uid/slash",
            "uid.dot",
            "uid#hash",
            "uid\$dollar",
            "uid[open",
            "uid]close",
            "",
            "   ",
            " leadingSpace",
            "trailingSpace "
        )

        for (badUid in forbiddenSamples) {
            assertFalse("Expected uid '$badUid' to be rejected", RtdbPaths.isValidUid(badUid))
            try {
                RtdbPaths.userHomesPath(badUid)
                fail("Expected IllegalArgumentException for uid '$badUid'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }

        val forbiddenHomeIds = listOf(
            "home/A001",
            "home.A001",
            "home#A001",
            "home\$A001",
            "home[A001",
            "home]A001"
        )
        for (badHomeId in forbiddenHomeIds) {
            assertFalse("Expected homeId '$badHomeId' to be rejected", RtdbPaths.isValidEntityId(badHomeId))
            try {
                RtdbPaths.homeMetaPath(badHomeId)
                fail("Expected IllegalArgumentException for homeId '$badHomeId'")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}

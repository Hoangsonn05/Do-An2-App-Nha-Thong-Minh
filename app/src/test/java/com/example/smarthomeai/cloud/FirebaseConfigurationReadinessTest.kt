package com.example.smarthomeai.cloud

import com.google.firebase.database.DatabaseException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM configuration & Auth-RTDB compatibility readiness tests for P1.1-B2.
 *
 * Strictly local / zero production RTDB contact:
 * - Verifies missing FirebaseApp, malformed/non-HTTP RTDB URLs, whitespace trimming,
 *   lazy database caching, invalid/blank UID rejection across all 3 CloudRepository ops,
 *   and Auth/Offline error mapping consistency.
 */
class FirebaseConfigurationReadinessTest {

    @Test
    fun missingFirebaseApp_returnsUnavailableAcrossAllRepositoryOperations() = runTest {
        var dbFactoryCalls = 0
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { app -> app?.options?.databaseUrl },
            uidResolver = { app -> if (app != null) "uShouldNotBeRead" else null },
            databaseResolver = {
                dbFactoryCalls++
                null
            }
        )

        assertFalse(provider.isConfigured)
        assertNull(provider.databaseUrl)
        assertNull(provider.uid)
        assertNull(provider.databaseOrNull())
        assertEquals(0, dbFactoryCalls)

        val repo = RtdbCloudRepository(provider)
        assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
        assertEquals(CloudResult.Unavailable, repo.createHome("Nha B2"))
        assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
        assertEquals(0, dbFactoryCalls)
    }

    @Test
    fun malformedAndNonHttpDatabaseUrls_areRejectedWithoutInstantiatingDatabase() = runTest {
        val badUrls = listOf(
            "https:///only-path",
            "ws://127.0.0.1:9000",
            "wss://demo-smarthomeai.firebaseio.com",
            "file:///tmp/rtdb.json",
            "javascript:alert(1)",
            "http://127.0.0.1:abc",
            "https://demo host.firebasedatabase.app"
        )
        for (badUrl in badUrls) {
            assertFalse("Expected invalid RTDB URL: $badUrl", FirebaseProvider.isValidDatabaseUrl(badUrl))

            val provider = FirebaseProvider(
                context = null,
                appResolver = { null },
                databaseUrlResolver = { badUrl },
                uidResolver = { "uA" },
                databaseResolver = { error("Database factory must not be called for invalid URL: $badUrl") }
            )
            assertFalse(provider.isConfigured)
            assertNull(provider.databaseUrl)
            assertNull(provider.databaseOrNull())
        }
    }

    @Test
    fun validDatabaseUrlWithOuterWhitespace_isTrimmedCleanly() {
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { "  https://demo-smarthomeai.asia-southeast1.firebasedatabase.app  " },
            uidResolver = { "uA" },
            databaseResolver = { null }
        )

        assertTrue(provider.isConfigured)
        assertEquals(
            "https://demo-smarthomeai.asia-southeast1.firebasedatabase.app",
            provider.databaseUrl
        )
    }

    @Test
    fun providerDoesNotEagerlyInstantiateDatabaseOnConstructionOrMetadataRead() {
        var dbFactoryCalls = 0
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { "http://127.0.0.1:9000?ns=demo-smarthomeai-default-rtdb" },
            uidResolver = { "uA" },
            databaseResolver = {
                dbFactoryCalls++
                null
            }
        )

        assertTrue(provider.isConfigured)
        assertEquals("uA", provider.uid)
        assertEquals(0, dbFactoryCalls)
    }

    @Test
    fun databaseExceptionFromSdk_mapsAuthAndOfflineMessagesConsistently() {
        assertEquals(
            CloudError.Unauthenticated,
            CloudErrorMapper.mapException(DatabaseException("Auth token expired while accessing RTDB"))
        )
        assertEquals(
            CloudError.Unauthenticated,
            CloudErrorMapper.mapException(DatabaseException("Invalid token supplied"))
        )
        assertEquals(
            CloudError.Offline,
            CloudErrorMapper.mapException(DatabaseException("Client is offline / network disconnected"))
        )
        assertEquals(
            CloudError.PermissionDenied,
            CloudErrorMapper.mapException(DatabaseException("Permission denied by RTDB rules"))
        )
    }

    @Test
    fun invalidOrBlankUid_failsRtdbPathUidContractConsistently() {
        val invalidUids = listOf("", "   ", "bad/uid", "bad.uid", "bad#uid", "bad\$uid", "bad[uid]", " uA ")
        for (badUid in invalidUids) {
            assertFalse("Expected invalid UID: '$badUid'", RtdbPaths.isValidUid(badUid))
        }
        assertTrue(RtdbPaths.isValidUid("firebase_uid_1234567890"))
    }
}

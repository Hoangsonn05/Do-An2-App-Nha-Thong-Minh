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
 * JVM unit tests for MIN-03: FirebaseProvider resilience to missing FirebaseApp,
 * missing/blank URL, invalid URL syntax, and FirebaseDatabase.getInstance(url) exceptions.
 * Zero production RTDB contact.
 */
class FirebaseProviderTest {

    @Test
    fun whenFirebaseNotInitialized_providerAndRepositoryReturnUnavailable() = runTest {
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { null },
            uidResolver = { null },
            databaseResolver = { error("Should not be called when unconfigured") }
        )

        assertNull(provider.databaseUrl)
        assertFalse(provider.isConfigured)
        assertNull(provider.uid)
        assertNull(provider.databaseOrNull())

        val repo = RtdbCloudRepository(provider)
        assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
        assertEquals(CloudResult.Unavailable, repo.createHome("Nha Test"))
        assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
    }

    @Test
    fun whenDatabaseUrlMissingOrBlank_returnsUnavailable() = runTest {
        for (missingUrl in listOf(null, "", "   ")) {
            val provider = FirebaseProvider(
                context = null,
                appResolver = { null },
                databaseUrlResolver = { missingUrl },
                uidResolver = { "uA" },
                databaseResolver = { error("Should not be called for blank/null URL") }
            )

            assertNull(provider.databaseUrl)
            assertFalse(provider.isConfigured)
            assertNull(provider.databaseOrNull())

            val repo = RtdbCloudRepository(provider)
            assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
            assertEquals(CloudResult.Unavailable, repo.createHome("Nha Test"))
            assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
        }
    }

    @Test
    fun whenDatabaseUrlInvalidSyntax_doesNotCrashAndReturnsUnavailable() = runTest {
        val badUrls = listOf(
            "not-a-url",
            "ftp://invalid.example.com",
            "https://",
            "http://",
            "https://bad host/db",
            "://missing-scheme"
        )
        for (badUrl in badUrls) {
            assertFalse("Expected invalid URL: $badUrl", FirebaseProvider.isValidDatabaseUrl(badUrl))

            val provider = FirebaseProvider(
                context = null,
                appResolver = { null },
                databaseUrlResolver = { badUrl },
                uidResolver = { "uA" },
                databaseResolver = { error("Should not instantiate DB for malformed URL $badUrl") }
            )

            assertNull(provider.databaseUrl)
            assertFalse(provider.isConfigured)
            assertNull(provider.databaseOrNull())

            val repo = RtdbCloudRepository(provider)
            assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
            assertEquals(CloudResult.Unavailable, repo.createHome("Nha Test"))
            assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
        }
    }

    @Test
    fun whenDatabaseFactoryThrowsDatabaseException_runCatchingReturnsNullAndRepoReturnsUnavailable() = runTest {
        var calls = 0
        val provider = FirebaseProvider(
            context = null,
            appResolver = { null },
            databaseUrlResolver = { "https://demo-invalid.example.com/non-root-path" },
            uidResolver = { "uA" },
            databaseResolver = {
                calls++
                throw DatabaseException("Invalid Firebase Database url specified")
            }
        )

        assertEquals("https://demo-invalid.example.com/non-root-path", provider.databaseUrl)
        assertTrue(provider.isConfigured)
        assertNull(provider.databaseOrNull())
        assertEquals(1, calls)

        val repo = RtdbCloudRepository(provider)
        assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
        assertEquals(CloudResult.Unavailable, repo.createHome("Nha Test"))
        assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
    }

    @Test
    fun isValidDatabaseUrl_acceptsHttpsAndLocalEmulatorHttpUrls() {
        assertTrue(FirebaseProvider.isValidDatabaseUrl("https://demo-smarthomeai-default-rtdb.firebaseio.com"))
        assertTrue(FirebaseProvider.isValidDatabaseUrl("https://demo-test.asia-southeast1.firebasedatabase.app"))
        assertTrue(FirebaseProvider.isValidDatabaseUrl("http://10.0.2.2:9000?ns=demo-smarthomeai-default-rtdb"))
        assertTrue(FirebaseProvider.isValidDatabaseUrl("http://127.0.0.1:9000?ns=demo-smarthomeai-default-rtdb"))
    }
}

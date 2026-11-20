package com.example.smarthomeai.cloud

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Android instrumentation integration tests for P1.1-B1 (Part A3, MIN-02, MIN-03).
 *
 * STRICT PRODUCTION ISOLATION:
 * - Never touches the default FirebaseApp or appaintm production RTDB.
 * - Uses only dedicated secondary FirebaseApp instances configured strictly for
 *   local RTDB Emulator (10.0.2.2:9000, project demo-smarthomeai) or offline mode.
 */
@RunWith(AndroidJUnit4::class)
class CloudInitializationIntegrationTest {

    private fun isLocalEmulatorReachable(): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("10.0.2.2", 9000), 500)
            }
            true
        }.getOrDefault(false)
    }

    @Test
    fun firebaseProvider_retainsApplicationContextNotWrapperOrActivityContext() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapperContext = object : ContextWrapper(targetContext) {}

        val provider = FirebaseProvider(wrapperContext)

        assertSame(targetContext.applicationContext, provider.appContext)
        assertNotSame(wrapperContext, provider.appContext)
    }

    @Test
    fun firebaseProvider_whenFirebaseNotInitialized_returnsUnavailable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = FirebaseProvider(
            context = context,
            appResolver = { null },
            databaseUrlResolver = { null },
            uidResolver = { null },
            databaseResolver = { null }
        )

        assertFalse(provider.isConfigured)
        assertNull(provider.databaseOrNull())

        val repo = RtdbCloudRepository(provider)
        assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
        assertEquals(CloudResult.Unavailable, repo.createHome("Nha Test"))
        assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
    }

    @Test
    fun firebaseProvider_withInvalidSdkUrl_catchesDatabaseExceptionAndReturnsUnavailable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (app, _) = offlineIsolatedDatabase()

        // Real FirebaseDatabase.getInstance(app, invalidUrl) throws DatabaseException;
        // FirebaseProvider must catch it and return null -> CloudResult.Unavailable without crashing.
        val provider = FirebaseProvider(
            context = context,
            appResolver = { app },
            databaseUrlResolver = { "https://invalid-test.example.com/subpath/not-root" },
            uidResolver = { "uTest" },
            databaseResolver = { url ->
                runCatching { FirebaseDatabase.getInstance(app, url) }.getOrNull()
            }
        )

        assertNull(provider.databaseOrNull())
        val repo = RtdbCloudRepository(provider)
        assertEquals(CloudResult.Unavailable, repo.observeHomes().first())
        assertEquals(CloudResult.Unavailable, repo.createHome("Nha Bad URL"))
        assertEquals(CloudResult.Unavailable, repo.observeMembers("home_A001").first())
    }

    @Test
    fun rtdbRepository_withInitializedIsolatedAppAndNoAuth_returnsUnauthenticated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (app, db) = offlineIsolatedDatabase()
        val provider = FirebaseProvider(
            context = context,
            appResolver = { app },
            databaseUrlResolver = { app.options.databaseUrl },
            uidResolver = { null },
            databaseResolver = { db }
        )

        assertTrue(provider.isConfigured)
        assertNotNull(provider.databaseOrNull())

        val repo = RtdbCloudRepository(provider)
        val homesRes = repo.observeHomes().first()
        val createRes = repo.createHome("Nha Unauth")
        val membersRes = repo.observeMembers("home_A001").first()

        assertTrue(homesRes is CloudResult.Err && homesRes.error == CloudError.Unauthenticated)
        assertTrue(createRes is CloudResult.Err && createRes.error == CloudError.Unauthenticated)
        assertTrue(membersRes is CloudResult.Err && membersRes.error == CloudError.Unauthenticated)
    }

    @Test
    fun rtdbRepository_whenOffline_createHomeReturnsTimeoutCleanly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (app, db) = offlineIsolatedDatabase()
        val provider = FirebaseProvider(
            context = context,
            appResolver = { app },
            databaseUrlResolver = { app.options.databaseUrl },
            uidResolver = { "uOffline" },
            databaseResolver = { db }
        )

        val repo = RtdbCloudRepository(provider, timeoutMs = 300L)
        val result = repo.createHome("Nha Offline")

        assertTrue("Expected Timeout error, got $result", result is CloudResult.Err && result.error == CloudError.Timeout)
    }

    @Test
    fun rtdbRepository_whenCoroutineCancelled_propagatesCancellationException() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (app, db) = offlineIsolatedDatabase()
        val provider = FirebaseProvider(
            context = context,
            appResolver = { app },
            databaseUrlResolver = { app.options.databaseUrl },
            uidResolver = { "uCancel" },
            databaseResolver = { db }
        )

        val repo = RtdbCloudRepository(provider, timeoutMs = 10_000L)
        var returnedResult: CloudResult<String>? = null
        var sawCancellation = false

        val job = launch {
            try {
                returnedResult = repo.createHome("Nha Cancel")
            } catch (e: CancellationException) {
                sawCancellation = true
                throw e
            }
        }

        delay(150L)
        job.cancelAndJoin()

        assertTrue("Expected CancellationException to propagate", sawCancellation)
        assertNull("Expected createHome not to swallow cancellation into CloudResult", returnedResult)
    }

    @Test
    fun rtdbRepository_whenCollectorsStop_detachesListenersCleanly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (app, db) = offlineIsolatedDatabase()
        val provider = FirebaseProvider(
            context = context,
            appResolver = { app },
            databaseUrlResolver = { app.options.databaseUrl },
            uidResolver = { "uDetach" },
            databaseResolver = { db }
        )

        val repo = RtdbCloudRepository(provider, timeoutMs = 5_000L)

        val homesJob = launch {
            repo.observeHomes().collect { }
        }
        val membersJob = launch {
            repo.observeMembers("home_A001").collect { }
        }

        delay(150L)
        withTimeout(3_000L) {
            homesJob.cancelAndJoin()
            membersJob.cancelAndJoin()
        }

        assertTrue(homesJob.isCancelled)
        assertTrue(membersJob.isCancelled)
    }

    @Test
    fun rtdbRepository_againstLocalEmulator_mapsPermissionDeniedWhenUnauthTokenHitsRules() = runBlocking {
        assumeTrue("Local RTDB Emulator (10.0.2.2:9000) not running", isLocalEmulatorReachable())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val (app, db) = onlineEmulatorDatabase()
        val provider = FirebaseProvider(
            context = context,
            appResolver = { app },
            databaseUrlResolver = { app.options.databaseUrl },
            uidResolver = { "uEmuNoToken" },
            databaseResolver = { db }
        )

        val repo = RtdbCloudRepository(provider, timeoutMs = 5_000L)
        val homesResult = withTimeout(5_000L) {
            repo.observeHomes().first()
        }
        val membersResult = withTimeout(5_000L) {
            repo.observeMembers("home_A001").first()
        }

        assertTrue(
            "Expected PermissionDenied from local RTDB Emulator rules, got $homesResult",
            homesResult is CloudResult.Err && homesResult.error == CloudError.PermissionDenied
        )
        assertTrue(
            "Expected PermissionDenied from local RTDB Emulator rules, got $membersResult",
            membersResult is CloudResult.Err && membersResult.error == CloudError.PermissionDenied
        )
    }

    companion object {
        private const val EMULATOR_DUMMY_URL = "https://demo-smarthomeai-default-rtdb.firebaseio.com"

        @Volatile
        private var offlinePair: Pair<FirebaseApp, FirebaseDatabase>? = null

        @Volatile
        private var onlinePair: Pair<FirebaseApp, FirebaseDatabase>? = null

        private fun getOrInitApp(name: String): FirebaseApp {
            val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            return runCatching { FirebaseApp.getInstance(name) }.getOrElse {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000001")
                    .setApiKey("fake-local-emulator-api-key")
                    .setProjectId("demo-smarthomeai")
                    .setDatabaseUrl(EMULATOR_DUMMY_URL)
                    .build()
                FirebaseApp.initializeApp(context, options, name)
            }
        }

        @Synchronized
        private fun offlineIsolatedDatabase(): Pair<FirebaseApp, FirebaseDatabase> {
            offlinePair?.let { return it }
            val app = getOrInitApp("b1-isolated-offline")
            val db = FirebaseDatabase.getInstance(app, EMULATOR_DUMMY_URL)
            db.useEmulator("10.0.2.2", 9000)
            db.goOffline()
            return (app to db).also { offlinePair = it }
        }

        @Synchronized
        private fun onlineEmulatorDatabase(): Pair<FirebaseApp, FirebaseDatabase> {
            onlinePair?.let { return it }
            val app = getOrInitApp("b1-isolated-online")
            val db = FirebaseDatabase.getInstance(app, EMULATOR_DUMMY_URL)
            db.useEmulator("10.0.2.2", 9000)
            db.goOnline()
            return (app to db).also { onlinePair = it }
        }
    }
}

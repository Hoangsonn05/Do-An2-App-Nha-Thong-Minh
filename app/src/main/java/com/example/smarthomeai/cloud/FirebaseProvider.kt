package com.example.smarthomeai.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.net.URI

/**
 * Lazy Firebase RTDB provider (F4, N3, N4, MIN-03, B3.1).
 *
 * - Stores applicationContext (never retains an Activity reference).
 * - Checks FirebaseApp.getApps(context).isNotEmpty() before touching Firebase SDK.
 * - Reads database URL dynamically from FirebaseApp.getInstance().options.databaseUrl (no hardcoded URL).
 * - Wraps FirebaseDatabase.getInstance(url) with runCatching so malformed/invalid URLs return null (-> Unavailable).
 * - Never calls setPersistenceEnabled(...) (leaves SDK default false to prevent DatabaseException).
 * - Never initializes or modifies Firebase configuration.
 */
class FirebaseProvider internal constructor(
    context: Context?,
    private val appResolver: (Context?) -> FirebaseApp?,
    private val databaseUrlResolver: (FirebaseApp?) -> String?,
    private val uidResolver: (FirebaseApp?) -> String?,
    private val databaseResolver: (String) -> FirebaseDatabase?,
    private val authUidFlowResolver: ((FirebaseApp?) -> Flow<String?>)? = null
) {
    internal val appContext: Context? = context?.applicationContext ?: context

    @Volatile
    private var cachedDatabase: FirebaseDatabase? = null

    constructor(context: Context) : this(
        context = context,
        appResolver = ::defaultAppResolver,
        databaseUrlResolver = ::defaultDatabaseUrlResolver,
        uidResolver = ::defaultUidResolver,
        databaseResolver = ::defaultDatabaseResolver,
        authUidFlowResolver = ::defaultAuthUidFlowResolver
    )

    private fun appOrNull(): FirebaseApp? = appResolver(appContext)

    val databaseUrl: String?
        get() = databaseUrlResolver(appOrNull())
            ?.trim()
            ?.takeIf { isValidDatabaseUrl(it) }

    val isConfigured: Boolean
        get() = databaseUrl != null

    val uid: String?
        get() = uidResolver(appOrNull())

    fun observeAuthUid(): Flow<String?> {
        val app = appOrNull()
        val resolver = authUidFlowResolver
        return if (resolver != null) {
            resolver(app)
        } else {
            flowOf(uidResolver(app))
        }
    }

    fun databaseOrNull(): FirebaseDatabase? {
        val url = databaseUrl ?: return null
        cachedDatabase?.let { return it }
        return synchronized(this) {
            cachedDatabase ?: runCatching { databaseResolver(url) }.getOrNull()?.also {
                cachedDatabase = it
            }
        }
    }

    internal companion object {
        private fun defaultAppResolver(ctx: Context?): FirebaseApp? =
            kotlin.runCatching {
                if (ctx != null && FirebaseApp.getApps(ctx).isNotEmpty()) {
                    FirebaseApp.getInstance()
                } else {
                    null
                }
            }.getOrNull()

        private fun defaultDatabaseUrlResolver(app: FirebaseApp?): String? =
            app?.options?.databaseUrl?.takeIf { it.isNotBlank() }

        private fun defaultUidResolver(app: FirebaseApp?): String? =
            if (app != null) {
                kotlin.runCatching {
                    FirebaseAuth.getInstance(app).currentUser?.uid?.takeIf { it.isNotBlank() }
                }.getOrNull()
            } else {
                null
            }

        private fun defaultAuthUidFlowResolver(app: FirebaseApp?): Flow<String?> {
            if (app == null) return flowOf(null)
            return callbackFlow {
                val auth = runCatching { FirebaseAuth.getInstance(app) }.getOrNull()
                if (auth == null) {
                    trySend(null)
                    close()
                    return@callbackFlow
                }
                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                    trySend(firebaseAuth.currentUser?.uid?.takeIf { it.isNotBlank() })
                }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.distinctUntilChanged()
        }

        private fun defaultDatabaseResolver(url: String): FirebaseDatabase? =
            kotlin.runCatching { FirebaseDatabase.getInstance(url) }.getOrNull()

        fun isValidDatabaseUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val trimmed = url.trim()
            if (trimmed.any { it.isWhitespace() }) return false
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "https" && scheme != "http") return false
            val host = uri.host ?: return false
            return host.isNotBlank()
        }
    }
}

/**
 * FirebaseAuth-backed CloudSession implementation residing strictly inside the cloud/ package.
 * Never triggers lazy FirebaseDatabase instantiation when unavailable or signed out.
 */
class FirebaseCloudSession(
    private val provider: FirebaseProvider
) : CloudSession {
    override fun observeSession(): Flow<CloudSessionState> {
        if (!provider.isConfigured) {
            return flowOf(CloudSessionState.Unavailable)
        }
        return provider.observeAuthUid()
            .map { rawUid ->
                if (!provider.isConfigured) {
                    CloudSessionState.Unavailable
                } else if (rawUid != null && RtdbPaths.isValidUid(rawUid)) {
                    CloudSessionState.SignedIn(rawUid)
                } else {
                    CloudSessionState.SignedOut
                }
            }
            .distinctUntilChanged()
    }
}

/**
 * Factory creating shared CloudSession + CloudRepository backed by one lazy FirebaseProvider.
 * Keeps all Firebase SDK wiring inside cloud/ so SmartHomeViewModel never imports Firebase SDK.
 */
object CloudFoundationFactory {
    fun create(context: Context): CloudComponents {
        val provider = FirebaseProvider(context)
        return CloudComponents(
            session = FirebaseCloudSession(provider),
            repository = RtdbCloudRepository(provider),
            deviceRepository = RtdbDeviceRepository(provider)
        )
    }
}

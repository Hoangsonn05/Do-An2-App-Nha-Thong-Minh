package com.example.smarthomeai.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.net.URI

/**
 * Lazy Firebase RTDB provider (F4, N3, N4, MIN-03).
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
    private val databaseResolver: (String) -> FirebaseDatabase?
) {
    internal val appContext: Context? = context?.applicationContext ?: context

    @Volatile
    private var cachedDatabase: FirebaseDatabase? = null

    constructor(context: Context) : this(
        context = context,
        appResolver = ::defaultAppResolver,
        databaseUrlResolver = ::defaultDatabaseUrlResolver,
        uidResolver = ::defaultUidResolver,
        databaseResolver = ::defaultDatabaseResolver
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
                kotlin.runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
            } else {
                null
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

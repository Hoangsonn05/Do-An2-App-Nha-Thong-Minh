package com.example.smarthomeai.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Lazy Firebase RTDB provider (F4, N3, N4).
 *
 * - Checks FirebaseApp.getApps(context).isNotEmpty() before touching Firebase SDK.
 * - Reads database URL dynamically from FirebaseApp.getInstance().options.databaseUrl (no hardcoded URL).
 * - Never calls setPersistenceEnabled(...) (leaves SDK default false to prevent DatabaseException).
 * - Never initializes or modifies Firebase configuration.
 */
class FirebaseProvider(private val context: Context) {
    @Volatile
    private var cachedDatabase: FirebaseDatabase? = null

    private fun appOrNull(): FirebaseApp? =
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            runCatching { FirebaseApp.getInstance() }.getOrNull()
        } else {
            null
        }

    val databaseUrl: String?
        get() = appOrNull()?.options?.databaseUrl?.takeIf { it.isNotBlank() }

    val isConfigured: Boolean
        get() = databaseUrl != null

    val uid: String?
        get() = if (appOrNull() != null) {
            runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        } else {
            null
        }

    fun databaseOrNull(): FirebaseDatabase? {
        val url = databaseUrl ?: return null
        cachedDatabase?.let { return it }
        return synchronized(this) {
            cachedDatabase ?: FirebaseDatabase.getInstance(url).also {
                cachedDatabase = it
            }
        }
    }
}

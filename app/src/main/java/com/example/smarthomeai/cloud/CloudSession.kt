package com.example.smarthomeai.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin representation of Cloud Auth session state (B3.1 / B2-F5).
 * Must not import any Firebase SDK types.
 */
sealed class CloudSessionState {
    data object Unavailable : CloudSessionState()
    data object SignedOut : CloudSessionState()
    data class SignedIn(val uid: String) : CloudSessionState()
}

/**
 * Pure Kotlin abstraction for observing Cloud authentication session transitions.
 * Implementations bind FirebaseAuth inside the cloud/ package so ViewModel stays SDK-free.
 */
interface CloudSession {
    fun observeSession(): Flow<CloudSessionState>
}

/**
 * Pure Kotlin bundle injected into ViewModel so ViewModel never touches Firebase SDK classes.
 */
data class CloudComponents(
    val session: CloudSession,
    val repository: CloudRepository
)

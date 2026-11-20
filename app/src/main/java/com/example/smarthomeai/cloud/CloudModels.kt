package com.example.smarthomeai.cloud

/**
 * Default timeout in milliseconds for single-shot suspend Cloud operations (e.g., createHome, one-shot meta fetch).
 * Continuous callbackFlow observers are NEVER wrapped with this timeout.
 */
const val CLOUD_OPERATION_TIMEOUT_MS: Long = 10_000L

enum class CloudRole(val wireValue: String) {
    OWNER("owner"),
    MEMBER("member");

    companion object {
        fun fromWire(value: String?): CloudRole? = entries.firstOrNull { it.wireValue == value }
    }
}

data class CloudHome(
    val id: String,
    val name: String,
    val role: CloudRole
)

data class CloudMember(
    val uid: String,
    val role: CloudRole,
    val addedAt: Long? = null,
    val addedBy: String? = null
)

enum class CloudError {
    Unauthenticated,
    PermissionDenied,
    NotFound,
    Conflict,
    Offline,
    Timeout,
    InvalidData,
    Unknown
}

sealed class CloudResult<out T> {
    data class Ok<T>(val data: T) : CloudResult<T>()
    data class Err(val error: CloudError, val message: String? = null) : CloudResult<Nothing>()
    data object Unavailable : CloudResult<Nothing>()
}

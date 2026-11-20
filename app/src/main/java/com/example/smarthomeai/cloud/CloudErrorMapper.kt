package com.example.smarthomeai.cloud

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Maps Firebase RTDB error codes and exceptions to domain CloudError values.
 * Provides an Int overload (mapDatabaseErrorCode) so JVM unit tests remain independent
 * of DatabaseError's package-private constructor (F4).
 */
object CloudErrorMapper {
    // Standard Firebase RTDB DatabaseError integer codes
    const val CODE_DATA_STALE = -1
    const val CODE_OPERATION_FAILED = -2
    const val CODE_PERMISSION_DENIED = -3
    const val CODE_DISCONNECTED = -4
    const val CODE_EXPIRED_TOKEN = -6
    const val CODE_INVALID_TOKEN = -7
    const val CODE_MAX_RETRIES = -8
    const val CODE_OVERRIDDEN_BY_SET = -9
    const val CODE_UNAVAILABLE = -10
    const val CODE_USER_CODE_EXCEPTION = -11
    const val CODE_NETWORK_ERROR = -24
    const val CODE_WRITE_CANCELED = -25
    const val CODE_UNKNOWN_ERROR = -999

    fun mapDatabaseErrorCode(code: Int): CloudError = when (code) {
        CODE_PERMISSION_DENIED -> CloudError.PermissionDenied
        CODE_EXPIRED_TOKEN, CODE_INVALID_TOKEN -> CloudError.Unauthenticated
        CODE_DISCONNECTED, CODE_UNAVAILABLE, CODE_NETWORK_ERROR -> CloudError.Offline
        CODE_OVERRIDDEN_BY_SET, CODE_WRITE_CANCELED -> CloudError.Conflict
        else -> CloudError.Unknown
    }

    fun mapDatabaseError(error: DatabaseError): CloudError =
        mapDatabaseErrorCode(error.code)

    /**
     * Maps a Throwable to CloudError.
     * Converts ONLY TimeoutCancellationException to CloudError.Timeout;
     * rethrows all other CancellationExceptions so structured concurrency is never broken (F4).
     */
    fun mapException(throwable: Throwable): CloudError {
        if (throwable is TimeoutCancellationException) {
            return CloudError.Timeout
        }
        if (throwable is CancellationException) {
            throw throwable
        }
        if (throwable is IllegalArgumentException) {
            return CloudError.InvalidData
        }
        if (throwable is DatabaseException) {
            val msg = throwable.message.orEmpty().lowercase()
            return when {
                msg.contains("permission denied") || msg.contains("permission_denied") ->
                    CloudError.PermissionDenied
                msg.contains("network") || msg.contains("disconnected") || msg.contains("offline") ->
                    CloudError.Offline
                msg.contains("expired") || msg.contains("invalid token") ->
                    CloudError.Unauthenticated
                else -> CloudError.Unknown
            }
        }
        return CloudError.Unknown
    }
}

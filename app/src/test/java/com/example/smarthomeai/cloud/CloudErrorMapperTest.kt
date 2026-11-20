package com.example.smarthomeai.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CloudErrorMapperTest {

    @Test
    fun mapDatabaseErrorCode_mapsAllKnownFirebaseErrorCodes() {
        assertEquals(
            CloudError.PermissionDenied,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_PERMISSION_DENIED)
        )
        assertEquals(
            CloudError.Unauthenticated,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_EXPIRED_TOKEN)
        )
        assertEquals(
            CloudError.Unauthenticated,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_INVALID_TOKEN)
        )
        assertEquals(
            CloudError.Offline,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_DISCONNECTED)
        )
        assertEquals(
            CloudError.Offline,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_UNAVAILABLE)
        )
        assertEquals(
            CloudError.Offline,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_NETWORK_ERROR)
        )
        assertEquals(
            CloudError.Conflict,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_OVERRIDDEN_BY_SET)
        )
        assertEquals(
            CloudError.Conflict,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_WRITE_CANCELED)
        )
        assertEquals(
            CloudError.Unknown,
            CloudErrorMapper.mapDatabaseErrorCode(CloudErrorMapper.CODE_OPERATION_FAILED)
        )
        assertEquals(
            CloudError.Unknown,
            CloudErrorMapper.mapDatabaseErrorCode(-12345)
        )
    }

    @Test
    fun mapException_convertsTimeoutCancellationExceptionToTimeout() = runTest {
        var caught: Throwable? = null
        try {
            withTimeout(1L) {
                delay(1_000L)
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertEquals(CloudError.Timeout, CloudErrorMapper.mapException(caught!!))
    }

    @Test
    fun mapException_rethrowsNonTimeoutCancellationException() {
        val cancel = CancellationException("Parent scope cancelled")
        try {
            CloudErrorMapper.mapException(cancel)
            fail("Expected CancellationException to be rethrown, never swallowed")
        } catch (e: CancellationException) {
            assertEquals("Parent scope cancelled", e.message)
        }
    }

    @Test
    fun mapException_mapsIllegalArgumentAndGenericErrors() {
        assertEquals(CloudError.InvalidData, CloudErrorMapper.mapException(IllegalArgumentException("bad")))
        assertEquals(CloudError.Unknown, CloudErrorMapper.mapException(RuntimeException("boom")))
    }
}

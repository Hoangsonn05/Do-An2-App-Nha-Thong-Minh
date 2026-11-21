package com.example.smarthomeai.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM unit tests for MIN-04: bounded parallel home metadata fetch, aggregate timeout,
 * collector cancellation propagation, and partial-failure preservation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RtdbHomeMetaFetcherTest {

    @Test
    fun fetchAllBounded_capsConcurrencyAndRunsInParallel() = runTest {
        val homeIds = (1..16).map { "home_PAR_%04d".format(it) }
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)

        val results = RtdbHomeMetaFetcher.fetchAllBounded(
            homeIds = homeIds,
            maxConcurrency = 4,
            aggregateTimeoutMs = 10_000L
        ) { homeId ->
            val current = inFlight.incrementAndGet()
            peakInFlight.accumulateAndGet(current, Math::max)
            try {
                delay(100L)
                HomeMetaFetchResult.Found(homeId, "Name $homeId", "uA")
            } finally {
                inFlight.decrementAndGet()
            }
        }

        assertEquals(16, results.size)
        assertTrue("Expected peak concurrency <= 4, got ${peakInFlight.get()}", peakInFlight.get() <= 4)
        assertTrue("Expected true parallel execution (> 1), got ${peakInFlight.get()}", peakInFlight.get() > 1)
        // 16 tasks * 100ms at concurrency 4 -> ~400ms (much faster than sequential 1600ms)
        assertTrue("Expected virtual elapsed <= 600ms, got $currentTime", currentTime <= 600L)
    }

    @Test
    fun fetchAllBounded_appliesAggregateTimeoutAndPreservesCompletedHomes() = runTest {
        val homeIds = listOf("home_FAST01", "home_FAST02", "home_SLOW01", "home_SLOW02", "home_SLOW03")
        val cancelledCount = AtomicInteger(0)

        val results = RtdbHomeMetaFetcher.fetchAllBounded(
            homeIds = homeIds,
            maxConcurrency = 4,
            aggregateTimeoutMs = 1_000L
        ) { homeId ->
            if (homeId.startsWith("home_FAST")) {
                delay(50L)
                HomeMetaFetchResult.Found(homeId, "Fast $homeId", "uA")
            } else {
                try {
                    delay(60_000L)
                    HomeMetaFetchResult.Found(homeId, "Never", "uA")
                } catch (e: CancellationException) {
                    cancelledCount.incrementAndGet()
                    throw e
                }
            }
        }

        // Aggregate timeout is 1_000ms total (not 5 * 1_000ms = 5_000ms)
        assertEquals(1_000L, currentTime)
        assertTrue("Expected hanging requests to be cancelled", cancelledCount.get() >= 2)
        assertTrue(results["home_FAST01"] is HomeMetaFetchResult.Found)
        assertTrue(results["home_FAST02"] is HomeMetaFetchResult.Found)
        for (slowId in listOf("home_SLOW01", "home_SLOW02", "home_SLOW03")) {
            val res = results[slowId]
            assertTrue("Expected Timeout for $slowId, got $res", res is HomeMetaFetchResult.Failed && res.error == CloudError.Timeout)
        }

        // Valid fast homes are preserved by mergeHomesDetailed().toCloudResult()
        val report = RtdbHomeMerger.mergeHomesDetailed("uA", homeIds, results)
        val cloudResult = report.toCloudResult()
        assertTrue(cloudResult is CloudResult.Ok)
        assertEquals(listOf("home_FAST01", "home_FAST02"), (cloudResult as CloudResult.Ok).data.map { it.id })
        assertEquals(3, report.failedHomes.size)
        assertTrue(report.failedHomes.values.all { it == CloudError.Timeout })
    }

    @Test
    fun fetchAllBounded_whenCollectorCancelled_cancelsInFlightAndPropagatesCancellationException() = runTest {
        val homeIds = listOf("home_CANC01", "home_CANC02", "home_CANC03")
        val started = AtomicInteger(0)
        val cancelled = AtomicInteger(0)
        val neverFinishes = CompletableDeferred<Unit>()
        var sawCancellation = false

        val job = launch {
            try {
                RtdbHomeMetaFetcher.fetchAllBounded(
                    homeIds = homeIds,
                    maxConcurrency = 4,
                    aggregateTimeoutMs = 10_000L
                ) {
                    started.incrementAndGet()
                    try {
                        neverFinishes.await()
                        HomeMetaFetchResult.NotFound
                    } catch (e: CancellationException) {
                        cancelled.incrementAndGet()
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                sawCancellation = true
                throw e
            }
        }

        runCurrent()
        assertEquals(3, started.get())
        job.cancelAndJoin()

        assertTrue("Expected CancellationException to propagate", sawCancellation)
        assertEquals("Expected all 3 in-flight requests to be cancelled", 3, cancelled.get())
    }

    @Test
    fun mergeHomesDetailed_isolatesPerHomeFailureWithoutLosingOtherValidHomes() {
        val report = RtdbHomeMerger.mergeHomesDetailed(
            currentUid = "uA",
            indexedHomeIds = listOf("home_OK_001", "home_DENY01", "home_BAD_01", "home_TIME01", "home_OK_002"),
            metaResults = mapOf(
                "home_OK_001" to HomeMetaFetchResult.Found("home_OK_001", "Nha 1", "uA"),
                "home_DENY01" to HomeMetaFetchResult.PermissionDenied,
                "home_BAD_01" to HomeMetaFetchResult.Failed(CloudError.InvalidData, "Malformed"),
                "home_TIME01" to HomeMetaFetchResult.Failed(CloudError.Timeout, "Timed out"),
                "home_OK_002" to HomeMetaFetchResult.Found("home_OK_002", "Nha 2", "uOther")
            )
        )

        val result = report.toCloudResult()
        assertTrue(result is CloudResult.Ok)
        assertEquals(
            listOf(
                CloudHome("home_OK_001", "Nha 1", CloudRole.OWNER),
                CloudHome("home_OK_002", "Nha 2", CloudRole.MEMBER)
            ),
            (result as CloudResult.Ok).data
        )
        assertEquals(listOf("home_DENY01"), report.revokedOrMissingHomeIds)
        assertEquals(
            mapOf("home_BAD_01" to CloudError.InvalidData, "home_TIME01" to CloudError.Timeout),
            report.failedHomes
        )
    }

    @Test
    fun mergeHomesDetailed_whenAllHomesFailOrUnauth_surfacesExactClassifiedError() {
        val allOffline = RtdbHomeMerger.mergeHomesDetailed(
            currentUid = "uA",
            indexedHomeIds = listOf("home_OFF_01", "home_OFF_02"),
            metaResults = mapOf(
                "home_OFF_01" to HomeMetaFetchResult.Failed(CloudError.Offline, "No network"),
                "home_OFF_02" to HomeMetaFetchResult.Failed(CloudError.Offline, "No network")
            )
        ).toCloudResult()
        assertEquals(CloudResult.Err(CloudError.Offline, "No network"), allOffline)

        val unauthWithFound = RtdbHomeMerger.mergeHomesDetailed(
            currentUid = "uA",
            indexedHomeIds = listOf("home_OK_001", "home_UNAUTH"),
            metaResults = mapOf(
                "home_OK_001" to HomeMetaFetchResult.Found("home_OK_001", "Nha 1", "uA"),
                "home_UNAUTH" to HomeMetaFetchResult.Failed(CloudError.Unauthenticated, "Expired token")
            )
        ).toCloudResult()
        assertEquals(CloudResult.Err(CloudError.Unauthenticated, "Expired token"), unauthWithFound)
    }
}

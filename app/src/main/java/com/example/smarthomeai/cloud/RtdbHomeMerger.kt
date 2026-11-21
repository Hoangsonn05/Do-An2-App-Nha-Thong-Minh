package com.example.smarthomeai.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Maximum number of concurrent one-shot home metadata fetches in observeHomes() (MIN-04).
 */
const val MAX_CONCURRENT_META_FETCHES: Int = 4

/**
 * Pure Kotlin merge helper for observeHomes() (F4, N7, MIN-04).
 *
 * Contract:
 * - Empty indexedHomeIds -> CloudResult.Ok(emptyList()).
 * - Role is derived from authoritative meta.ownerUid == currentUid (OWNER) else MEMBER.
 * - Per-home PermissionDenied / NotFound entries (stale userHomes index) are isolated and skipped
 *   so remaining valid homes are preserved.
 * - Global/transport failures (e.g., Offline, Timeout, Unknown) surface as CloudResult.Err in mergeHomes(),
 *   while mergeHomesDetailed() preserves partial valid homes alongside per-home error classification.
 */
sealed class HomeMetaFetchResult {
    data class Found(
        val id: String,
        val name: String,
        val ownerUid: String
    ) : HomeMetaFetchResult()

    data object PermissionDenied : HomeMetaFetchResult()
    data object NotFound : HomeMetaFetchResult()
    data class Failed(
        val error: CloudError,
        val message: String? = null
    ) : HomeMetaFetchResult()
}

data class HomeMergeReport(
    val homes: List<CloudHome>,
    val revokedOrMissingHomeIds: List<String> = emptyList(),
    val failedHomes: Map<String, CloudError> = emptyMap(),
    val primaryFailureError: CloudError? = null,
    val primaryFailureMessage: String? = null
) {
    /**
     * Converts merge report to CloudResult while preventing a single failing home
     * from discarding valid homes already resolved (MIN-04).
     * Auth failure (Unauthenticated) always surfaces as Err.
     * When no valid homes could be resolved and a failure occurred, surfaces that classified Err.
     */
    fun toCloudResult(): CloudResult<List<CloudHome>> {
        if (failedHomes.values.any { it == CloudError.Unauthenticated }) {
            return CloudResult.Err(CloudError.Unauthenticated, primaryFailureMessage)
        }
        if (homes.isNotEmpty()) {
            return CloudResult.Ok(homes)
        }
        if (primaryFailureError != null) {
            return CloudResult.Err(primaryFailureError, primaryFailureMessage)
        }
        return CloudResult.Ok(emptyList())
    }
}

object RtdbHomeMerger {
    fun mergeHomes(
        currentUid: String,
        indexedHomeIds: List<String>,
        metaResults: Map<String, HomeMetaFetchResult>
    ): CloudResult<List<CloudHome>> {
        val report = mergeHomesDetailed(currentUid, indexedHomeIds, metaResults)
        if (report.primaryFailureError != null) {
            return CloudResult.Err(report.primaryFailureError, report.primaryFailureMessage)
        }
        return CloudResult.Ok(report.homes)
    }

    fun mergeHomesDetailed(
        currentUid: String,
        indexedHomeIds: List<String>,
        metaResults: Map<String, HomeMetaFetchResult>
    ): HomeMergeReport {
        if (indexedHomeIds.isEmpty()) {
            return HomeMergeReport(homes = emptyList())
        }

        val homes = mutableListOf<CloudHome>()
        val revokedOrMissing = mutableListOf<String>()
        val failedHomes = linkedMapOf<String, CloudError>()
        var primaryFailure: HomeMetaFetchResult.Failed? = null

        for (homeId in indexedHomeIds.distinct()) {
            when (val result = metaResults[homeId]) {
                is HomeMetaFetchResult.Found -> {
                    val derivedRole = if (result.ownerUid == currentUid) {
                        CloudRole.OWNER
                    } else {
                        CloudRole.MEMBER
                    }
                    homes += CloudHome(
                        id = result.id,
                        name = result.name,
                        role = derivedRole
                    )
                }
                HomeMetaFetchResult.PermissionDenied,
                HomeMetaFetchResult.NotFound,
                null -> {
                    // Stale userHomes entry: isolate & skip without failing remaining homes.
                    revokedOrMissing += homeId
                }
                is HomeMetaFetchResult.Failed -> {
                    failedHomes[homeId] = result.error
                    if (primaryFailure == null) {
                        primaryFailure = result
                    }
                }
            }
        }

        return HomeMergeReport(
            homes = homes,
            revokedOrMissingHomeIds = revokedOrMissing,
            failedHomes = failedHomes,
            primaryFailureError = primaryFailure?.error,
            primaryFailureMessage = primaryFailure?.message
        )
    }
}

/**
 * Pure Kotlin bounded-concurrency metadata fetcher with aggregate timeout (MIN-04).
 *
 * - Limits in-flight requests using a Semaphore(maxConcurrency).
 * - Applies a single aggregate timeout across the whole batch so total wait never scales linearly with N.
 * - Cancels all remaining in-flight fetches when the collector is cancelled or aggregate timeout fires.
 * - Propagates non-timeout CancellationException cleanly without swallowing it.
 * - Preserves completed results if a subset of homes times out or fails.
 */
object RtdbHomeMetaFetcher {
    suspend fun fetchAllBounded(
        homeIds: List<String>,
        maxConcurrency: Int = MAX_CONCURRENT_META_FETCHES,
        aggregateTimeoutMs: Long = CLOUD_OPERATION_TIMEOUT_MS,
        fetcher: suspend (String) -> HomeMetaFetchResult
    ): Map<String, HomeMetaFetchResult> {
        val uniqueIds = homeIds.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()

        val permits = maxConcurrency.coerceAtLeast(1)
        val semaphore = Semaphore(permits)
        val results = ConcurrentHashMap<String, HomeMetaFetchResult>(uniqueIds.size)

        val completedWithinBudget = withTimeoutOrNull(aggregateTimeoutMs) {
            coroutineScope {
                uniqueIds.map { homeId ->
                    async {
                        semaphore.withPermit {
                            val fetchResult = try {
                                fetcher(homeId)
                            } catch (e: TimeoutCancellationException) {
                                HomeMetaFetchResult.Failed(
                                    CloudError.Timeout,
                                    e.message ?: "Timed out reading home meta."
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                val mapped = CloudErrorMapper.mapException(t)
                                if (mapped == CloudError.PermissionDenied) {
                                    HomeMetaFetchResult.PermissionDenied
                                } else {
                                    HomeMetaFetchResult.Failed(mapped, t.message)
                                }
                            }
                            results[homeId] = fetchResult
                        }
                    }
                }.awaitAll()
            }
            true
        }

        if (completedWithinBudget == null) {
            // Aggregate timeout reached: in-flight coroutines have been cancelled;
            // keep already-resolved homes and classify unresolved homes as Timeout.
            for (homeId in uniqueIds) {
                results.putIfAbsent(
                    homeId,
                    HomeMetaFetchResult.Failed(
                        CloudError.Timeout,
                        "Timed out reading home metadata."
                    )
                )
            }
        }

        val ordered = LinkedHashMap<String, HomeMetaFetchResult>(uniqueIds.size)
        for (homeId in uniqueIds) {
            ordered[homeId] = results[homeId] ?: HomeMetaFetchResult.Failed(
                CloudError.Timeout,
                "Timed out reading home metadata."
            )
        }
        return ordered
    }
}

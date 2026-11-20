package com.example.smarthomeai.cloud

/**
 * Pure Kotlin merge helper for observeHomes() (F4, N7).
 *
 * Contract:
 * - Empty indexedHomeIds -> CloudResult.Ok(emptyList()).
 * - Role is derived from authoritative meta.ownerUid == currentUid (OWNER) else MEMBER.
 * - Per-home PermissionDenied / NotFound entries (stale userHomes index) are isolated and skipped
 *   so remaining valid homes are preserved.
 * - Global/transport failures (e.g., Offline, Timeout, Unknown) surface as CloudResult.Err.
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

object RtdbHomeMerger {
    fun mergeHomes(
        currentUid: String,
        indexedHomeIds: List<String>,
        metaResults: Map<String, HomeMetaFetchResult>
    ): CloudResult<List<CloudHome>> {
        if (indexedHomeIds.isEmpty()) {
            return CloudResult.Ok(emptyList())
        }

        val homes = mutableListOf<CloudHome>()
        var transportFailure: HomeMetaFetchResult.Failed? = null

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
                }
                is HomeMetaFetchResult.Failed -> {
                    if (transportFailure == null) {
                        transportFailure = result
                    }
                }
            }
        }

        if (transportFailure != null) {
            return CloudResult.Err(transportFailure.error, transportFailure.message)
        }
        return CloudResult.Ok(homes)
    }
}

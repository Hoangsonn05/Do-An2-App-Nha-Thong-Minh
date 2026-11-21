package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.SmartHomeState

/**
 * Pure Kotlin resolver deciding the single authoritative data source for UI display (F3 / D12).
 *
 * Must not import any Firebase SDK types.
 *
 * Clock-skew design note:
 * When [esp32Online] is false, [resolve] compares the Firebase server timestamp (`bestReceivedAt`)
 * against the phone's local LAN timestamp (`localLastSeen`). If the phone clock runs ahead of
 * Firebase server time, `localLastSeen` can exceed `cloudReceivedAt` and cause fallback to choose
 * [DataSource.LOCAL_CACHE] instead of [DataSource.CLOUD]. This is intentional and deterministic:
 * we avoid unverified client-server clock offset compensation.
 */
object DataSourceResolver {
    const val STALE_THRESHOLD_MS: Long = 300_000L

    fun resolve(
        state: SmartHomeState,
        snapshot: CloudDeviceSnapshot?,
        nowMs: Long = System.currentTimeMillis()
    ): ResolvedDataSource = resolve(
        esp32Online = state.esp32Online,
        localLastSeen = state.lastSeen,
        snapshot = snapshot,
        nowMs = nowMs
    )

    fun resolve(
        esp32Online: Boolean,
        localLastSeen: Long,
        snapshot: CloudDeviceSnapshot?,
        nowMs: Long = System.currentTimeMillis()
    ): ResolvedDataSource {
        val validLocalLastSeen = localLastSeen.takeIf { it > 0L }
        if (esp32Online) {
            return ResolvedDataSource(
                source = DataSource.LAN,
                updatedAt = validLocalLastSeen,
                deviceTs = null,
                isStale = false,
                gatewayOnline = true
            )
        }

        val safeLocalLastSeen = validLocalLastSeen ?: 0L
        val cloudReceivedAt = snapshot?.bestReceivedAt ?: 0L
        if (snapshot != null && cloudReceivedAt > 0L && cloudReceivedAt > safeLocalLastSeen) {
            val isStale = ((nowMs - cloudReceivedAt) > STALE_THRESHOLD_MS) ||
                (snapshot.gatewayState?.online == false)
            return ResolvedDataSource(
                source = DataSource.CLOUD,
                updatedAt = cloudReceivedAt,
                deviceTs = snapshot.bestDeviceTs,
                isStale = isStale,
                gatewayOnline = snapshot.gatewayState?.online
            )
        }

        if (validLocalLastSeen != null) {
            return ResolvedDataSource(
                source = DataSource.LOCAL_CACHE,
                updatedAt = validLocalLastSeen,
                deviceTs = null,
                isStale = (nowMs - validLocalLastSeen) > STALE_THRESHOLD_MS,
                gatewayOnline = null
            )
        }

        return ResolvedDataSource(
            source = DataSource.NONE,
            updatedAt = null,
            deviceTs = null,
            isStale = true,
            gatewayOnline = null
        )
    }
}

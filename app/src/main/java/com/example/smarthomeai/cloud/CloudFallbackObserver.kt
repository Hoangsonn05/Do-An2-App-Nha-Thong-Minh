package com.example.smarthomeai.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.EnumMap
import java.util.EnumSet
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Observes Cloud fallback data (gateway state, latest telemetry, recent events) ONLY when
 * LAN is offline ([esp32OnlineFlow] == false after [debounceMs]) and Cloud session is [CloudStatus.READY].
 *
 * Guarantees:
 * - Never touches [com.example.smarthomeai.data.SmartHomeState] or LAN Mutex.
 * - Never registers or writes Cloud devices; uses read-only [CloudDeviceRepository.findActiveDevice].
 * - Immediately cancels all active RTDB listeners and clears [cloudSnapshot] when LAN recovers,
 *   user signs out, Auth UID changes, or home access is lost.
 */
class CloudFallbackObserver(
    private val scope: CoroutineScope,
    private val cloudStateFlow: Flow<CloudUiState>,
    private val esp32OnlineFlow: Flow<Boolean>,
    private val deviceRepository: CloudDeviceRepository,
    private val debounceMs: Long = DEFAULT_FALLBACK_DEBOUNCE_MS,
    private val eventsLimit: Int = DEFAULT_EVENTS_LIMIT,
    private val workContext: CoroutineContext = EmptyCoroutineContext
) {

    private data class FallbackSessionKey(
        val uid: String?,
        val homeId: String?,
        val isReady: Boolean
    )

    private enum class StreamBranch {
        GATEWAY_STATE,
        TELEMETRY,
        EVENTS
    }

    private val _cloudSnapshot = MutableStateFlow<CloudDeviceSnapshot?>(null)
    val cloudSnapshot: StateFlow<CloudDeviceSnapshot?> = _cloudSnapshot.asStateFlow()

    private val retryTrigger = MutableStateFlow(0L)
    private var sessionJob: Job? = null

    init {
        start()
    }

    private fun start() {
        sessionJob?.cancel()
        sessionJob = scope.launch(workContext) {
            try {
                combine(
                    cloudStateFlow.map { it.toFallbackSessionKey() }.distinctUntilChanged(),
                    esp32OnlineFlow.distinctUntilChanged(),
                    retryTrigger
                ) { key, esp32Online, retryCount ->
                    Triple(key, esp32Online, retryCount)
                }.collectLatest { (key, esp32Online, _) ->
                    runFallbackSession(key, esp32Online)
                }
            } finally {
                _cloudSnapshot.value = null
            }
        }
    }

    fun retry() {
        retryTrigger.update { it + 1L }
    }

    fun clear() {
        sessionJob?.cancel()
        sessionJob = null
        _cloudSnapshot.value = null
    }

    private suspend fun runFallbackSession(
        key: FallbackSessionKey,
        esp32Online: Boolean
    ) {
        val uid = key.uid
        val homeId = key.homeId
        if (esp32Online || !key.isReady || uid == null || homeId == null) {
            _cloudSnapshot.value = null
            return
        }

        _cloudSnapshot.value = null
        if (debounceMs > 0L) {
            delay(debounceMs)
        }

        val activeDevice = when (val res = deviceRepository.findActiveDevice(uid, homeId)) {
            is CloudResult.Ok -> res.data
            CloudResult.Unavailable -> {
                _cloudSnapshot.value = null
                return
            }
            is CloudResult.Err -> {
                _cloudSnapshot.value = if (res.error == CloudError.Unauthenticated) {
                    null
                } else {
                    CloudDeviceSnapshot(
                        deviceId = "",
                        loading = false,
                        error = res.error,
                        errorMessage = res.message ?: defaultErrorMessage(res.error)
                    )
                }
                return
            }
        }

        if (activeDevice == null) {
            _cloudSnapshot.value = null
            return
        }

        observeDeviceStreams(uid = uid, homeId = homeId, deviceId = activeDevice.id)
    }

    private suspend fun observeDeviceStreams(
        uid: String,
        homeId: String,
        deviceId: String
    ) {
        coroutineScope {
            val stateLock = Any()
            var gatewayState: GatewayStateSnapshot? = null
            var latestTelemetry: CloudTelemetrySnapshot? = null
            var recentEvents: List<CloudEventRecord> = emptyList()
            val completedBranches = EnumSet.noneOf(StreamBranch::class.java)
            val branchErrors = EnumMap<StreamBranch, Pair<CloudError, String?>>(StreamBranch::class.java)
            var invalidatedByAuth = false
            var streamsJob: Job? = null

            _cloudSnapshot.value = CloudDeviceSnapshot(deviceId = deviceId, loading = true)

            fun publishSnapshotLocked() {
                if (!isActive || invalidatedByAuth) return
                val firstError = branchErrors.entries.firstOrNull()?.value
                val isLoading = completedBranches.size < StreamBranch.entries.size
                _cloudSnapshot.value = CloudDeviceSnapshot(
                    deviceId = deviceId,
                    gatewayState = gatewayState,
                    latestTelemetry = latestTelemetry,
                    recentEvents = recentEvents,
                    loading = isLoading,
                    error = firstError?.first,
                    errorMessage = firstError?.let { (err, msg) -> msg ?: defaultErrorMessage(err) }
                )
            }

            fun <T> handleResult(
                branch: StreamBranch,
                result: CloudResult<T>,
                onOk: (T) -> Unit
            ): Boolean {
                var shouldCancelStreams = false
                synchronized(stateLock) {
                    if (!isActive || invalidatedByAuth) return true
                    when (result) {
                        is CloudResult.Ok -> {
                            onOk(result.data)
                            completedBranches.add(branch)
                            branchErrors.remove(branch)
                            publishSnapshotLocked()
                        }
                        is CloudResult.Err -> {
                            if (result.error == CloudError.Unauthenticated) {
                                invalidatedByAuth = true
                                _cloudSnapshot.value = null
                                shouldCancelStreams = true
                            } else {
                                completedBranches.add(branch)
                                branchErrors[branch] = result.error to result.message
                                publishSnapshotLocked()
                            }
                        }
                        CloudResult.Unavailable -> {
                            invalidatedByAuth = true
                            _cloudSnapshot.value = null
                            shouldCancelStreams = true
                        }
                    }
                }
                if (shouldCancelStreams) {
                    streamsJob?.cancel()
                }
                return shouldCancelStreams
            }

            streamsJob = launch(start = CoroutineStart.LAZY) {
                launch {
                    deviceRepository.observeGatewayState(uid, homeId, deviceId).collect { result ->
                        if (handleResult(StreamBranch.GATEWAY_STATE, result) { gatewayState = it }) {
                            return@collect
                        }
                    }
                }

                launch {
                    deviceRepository.observeLatestTelemetry(uid, homeId, deviceId).collect { result ->
                        if (handleResult(StreamBranch.TELEMETRY, result) { latestTelemetry = it }) {
                            return@collect
                        }
                    }
                }

                launch {
                    deviceRepository.observeRecentEvents(uid, homeId, deviceId, eventsLimit).collect { result ->
                        if (handleResult(StreamBranch.EVENTS, result) { recentEvents = it }) {
                            return@collect
                        }
                    }
                }
            }
            streamsJob.start()
        }
    }

    private fun CloudUiState.toFallbackSessionKey(): FallbackSessionKey {
        val ready = status == CloudStatus.READY &&
            !currentUid.isNullOrBlank() &&
            !selectedHomeId.isNullOrBlank()
        return FallbackSessionKey(
            uid = if (ready) currentUid else null,
            homeId = if (ready) selectedHomeId else null,
            isReady = ready
        )
    }

    private fun defaultErrorMessage(error: CloudError): String = when (error) {
        CloudError.Offline -> "Không có kết nối mạng Cloud để tải dữ liệu dự phòng."
        CloudError.Timeout -> "Kết nối Cloud quá hạn khi tải dữ liệu dự phòng."
        CloudError.PermissionDenied -> "Không có quyền đọc dữ liệu Cloud của nhà này."
        CloudError.Unauthenticated -> "Phiên đăng nhập Cloud không hợp lệ."
        CloudError.InvalidData -> "Dữ liệu Cloud dự phòng không hợp lệ."
        CloudError.NotFound -> "Không tìm thấy dữ liệu thiết bị trên Cloud."
        CloudError.Conflict -> "Xung đột dữ liệu Cloud."
        CloudError.Unknown -> "Lỗi đọc dữ liệu Cloud dự phòng."
    }

    companion object {
        const val DEFAULT_FALLBACK_DEBOUNCE_MS: Long = 5_000L
        const val DEFAULT_EVENTS_LIMIT: Int = 20
    }
}

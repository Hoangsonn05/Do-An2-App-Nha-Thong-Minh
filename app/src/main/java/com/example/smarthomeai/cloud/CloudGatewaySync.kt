package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.LongSensorReadings
import com.example.smarthomeai.data.DeviceEvent
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SensorSample
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.data.effectiveSmokeAuto
import com.example.smarthomeai.data.smokeHazardActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Pure Kotlin snapshot of LAN/local gateway state observed by CloudGatewaySync
 * completely outside SmartHomeViewModel's network Mutex.
 */
data class GatewayInputSnapshot(
    val esp32Online: Boolean = false,
    val lastSeen: Long = 0L,
    val light: Boolean = false,
    val doorOpen: Boolean = false,
    val alarm: Boolean = false,
    val motionDetected: Boolean = false,
    val monitoring: Boolean = false,
    val mode: HomeMode = HomeMode.HOME,
    val temperature: Int? = null,
    val humidity: Int? = null,
    val temperatureLimit: Int = 35,
    val humidityLimit: Int = 80,
    val coldLimit: Int = 18,
    val autoAlarm: Boolean = true,
    val latestSample: SensorSample? = null,
    val recentEvents: List<DeviceEvent> = emptyList(),
    val smokePpm: Int? = null,
    val longSensors: LongSensorReadings? = null,
    val smokeAlert: Boolean? = null,
    val windowOpen: Boolean? = null,
    val fanOn: Boolean? = null,
    val smokeLimit: Int = 300,
    val smokeAuto: Boolean = true
)

fun SmartHomeState.toGatewayInput(): GatewayInputSnapshot = GatewayInputSnapshot(
    esp32Online = esp32Online,
    lastSeen = lastSeen,
    light = light,
    doorOpen = doorOpen,
    alarm = alarm,
    motionDetected = motionDetected,
    monitoring = monitoring,
    mode = mode,
    temperature = temperature,
    humidity = humidity,
    temperatureLimit = temperatureLimit,
    humidityLimit = humidityLimit,
    coldLimit = coldLimit,
    autoAlarm = autoAlarm,
    latestSample = samples.lastOrNull(),
    recentEvents = deviceEvents,
    smokePpm = hardware?.smokePpm,
    longSensors = hardware?.longSensors,
    smokeAlert = if (esp32Online) smokeHazardActive else hardware?.smokeAlert,
    windowOpen = hardware?.windowOpen,
    fanOn = hardware?.fanOn,
    smokeLimit = smokeLimit,
    smokeAuto = effectiveSmokeAuto
)

/**
 * Asynchronous Gateway-to-RTDB sync coordinator (F2b).
 *
 * Guarantees:
 * - Binds strictly to (uid, selectedHomeId, READY && OWNER) via collectLatest so UID switches,
 *   sign-out, home switches, and access revocation cancel all in-flight sync work immediately.
 * - Never touches SmartHomeViewModel's LAN Mutex and never blocks LAN polling or controls.
 * - Resolves/registers an active Cloud device before writing deviceData; blocks cleanly on RevokedOnly.
 * - Enforces D3 (events cursor + max 20/cycle + dedup), D4 (state/gateway debounce 2s + 60s heartbeat
 *   + single online=false transition), D5 (latest UTC telemetry), D6 (config rev + 1-shot conflict retry),
 *   and D7 (bounded backoff on Offline/Timeout, stop auto-retry on hard errors).
 */
class CloudGatewaySync(
    private val scope: CoroutineScope,
    private val cloudStateFlow: Flow<CloudUiState>,
    private val gatewayInputFlow: Flow<GatewayInputSnapshot>,
    private val deviceRepository: CloudDeviceRepository,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val workContext: CoroutineContext = EmptyCoroutineContext
) : CloudDeviceUiBridge {

    private data class SyncSessionKey(
        val uid: String?,
        val homeId: String?,
        val isReadyOwner: Boolean,
        val isSignedOutOrUnavailable: Boolean
    )

    private data class GatewayValues(
        val light: Boolean,
        val doorOpen: Boolean,
        val alarm: Boolean,
        val motionDetected: Boolean,
        val monitoring: Boolean,
        val mode: HomeMode,
        val temp: Int?,
        val hum: Int?,
        val smokePpm: Int?,
        val longSensors: LongSensorReadings?,
        val smokeAlert: Boolean?,
        val windowOpen: Boolean?,
        val fanOn: Boolean?
    )

    private data class ConfigValues(
        val tempLimit: Int,
        val humLimit: Int,
        val coldLimit: Int,
        val autoAlarm: Boolean,
        val smokeLimit: Int,
        val smokeAuto: Boolean
    )

    private data class StampedInput(
        val snapshot: GatewayInputSnapshot,
        val freshAtReceipt: Boolean
    )

    private data class ResolvedDeviceSession(
        val device: CloudDevice,
        val warningMessage: String?
    )

    private data class InitializedDeviceState(
        val rev: Long?,
        val config: ConfigValues?,
        val cursors: Map<String, Long>
    )

    private sealed class CycleOutcome {
        data class Done(val didWrite: Boolean, val pausedOffline: Boolean) : CycleOutcome()
        data class Failed(val error: CloudError, val message: String?) : CycleOutcome()
        data object Unavailable : CycleOutcome()
    }

    private val _gatewaySync = MutableStateFlow(GatewaySyncUiState())
    override val gatewaySync: StateFlow<GatewaySyncUiState> = _gatewaySync.asStateFlow()

    private val retryTrigger = MutableStateFlow(0L)
    private var sessionJob: Job? = null

    init {
        start()
    }

    private fun start() {
        sessionJob?.cancel()
        sessionJob = scope.launch(workContext) {
            combine(
                cloudStateFlow.map { it.toSyncSessionKey() }.distinctUntilChanged(),
                retryTrigger
            ) { key, retryCount ->
                key to retryCount
            }.collectLatest { (key, _) ->
                runSession(key)
            }
        }
    }

    override fun retrySync() {
        retryTrigger.update { it + 1L }
    }

    fun clear() {
        sessionJob?.cancel()
        sessionJob = null
        _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.IDLE)
    }

    private suspend fun runSession(key: SyncSessionKey) {
        if (key.isSignedOutOrUnavailable) {
            _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.IDLE)
            return
        }
        val uid = key.uid
        val homeId = key.homeId
        if (!key.isReadyOwner || uid == null || homeId == null) {
            _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.WAITING_HOME)
            return
        }

        var backoffIndex = 0
        val resolved = resolveDeviceLoop(uid, homeId) { err, msg ->
            if (isRetryableError(err)) {
                val delayMs = BACKOFF_DELAYS_MS[backoffIndex]
                backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_DELAYS_MS.lastIndex)
                _gatewaySync.value = GatewaySyncUiState(
                    phase = GatewaySyncPhase.ERROR,
                    lastError = err,
                    errorMessage = msg ?: defaultErrorMessage(err)
                )
                delay(delayMs)
                true
            } else {
                _gatewaySync.value = GatewaySyncUiState(
                    phase = GatewaySyncPhase.ERROR,
                    lastError = err,
                    errorMessage = msg ?: defaultErrorMessage(err)
                )
                false
            }
        } ?: return

        _gatewaySync.value = GatewaySyncUiState(
            phase = GatewaySyncPhase.SYNCING,
            activeDeviceId = resolved.device.id,
            warningMessage = resolved.warningMessage
        )

        val initialized = initializeDeviceStateLoop(uid, homeId, resolved) { err, msg ->
            if (isRetryableError(err)) {
                val delayMs = BACKOFF_DELAYS_MS[backoffIndex]
                backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_DELAYS_MS.lastIndex)
                _gatewaySync.value = GatewaySyncUiState(
                    phase = GatewaySyncPhase.ERROR,
                    activeDeviceId = resolved.device.id,
                    warningMessage = resolved.warningMessage,
                    lastError = err,
                    errorMessage = msg ?: defaultErrorMessage(err)
                )
                delay(delayMs)
                true
            } else {
                _gatewaySync.value = GatewaySyncUiState(
                    phase = GatewaySyncPhase.ERROR,
                    activeDeviceId = resolved.device.id,
                    warningMessage = resolved.warningMessage,
                    lastError = err,
                    errorMessage = msg ?: defaultErrorMessage(err)
                )
                false
            }
        } ?: return

        // Reset backoff once device resolution and initial metadata reads succeeded.
        backoffIndex = 0
        runSyncLoop(uid, homeId, resolved, initialized)
    }

    private suspend fun resolveDeviceLoop(
        uid: String,
        homeId: String,
        onError: suspend (CloudError, String?) -> Boolean
    ): ResolvedDeviceSession? {
        while (currentCoroutineContext().isActive) {
            _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.REGISTERING)
            when (val res = deviceRepository.resolveOrRegisterDevice(uid, homeId)) {
                is CloudResult.Ok -> {
                    when (val data = res.data) {
                        is DeviceResolveResult.Active -> {
                            val warning = if (data.hasMultipleActive) {
                                "Phát hiện nhiều thiết bị Gateway đang hoạt động; đang dùng \"${data.device.name}\"."
                            } else {
                                null
                            }
                            return ResolvedDeviceSession(data.device, warning)
                        }
                        is DeviceResolveResult.MultipleActiveWarning -> {
                            val warning = "Phát hiện ${data.allActiveDevices.size} thiết bị Gateway đang hoạt động; đang dùng \"${data.primaryDevice.name}\"."
                            return ResolvedDeviceSession(data.primaryDevice, warning)
                        }
                        is DeviceResolveResult.RevokedOnly -> {
                            _gatewaySync.value = GatewaySyncUiState(
                                phase = GatewaySyncPhase.BLOCKED,
                                errorMessage = "Thiết bị Cloud đã bị thu hồi"
                            )
                            return null
                        }
                    }
                }
                CloudResult.Unavailable -> {
                    _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.IDLE)
                    return null
                }
                is CloudResult.Err -> {
                    if (!onError(res.error, res.message)) return null
                }
            }
        }
        return null
    }

    private suspend fun initializeDeviceStateLoop(
        uid: String,
        homeId: String,
        resolved: ResolvedDeviceSession,
        onError: suspend (CloudError, String?) -> Boolean
    ): InitializedDeviceState? {
        var loadedRev: Long? = null
        var loadedConfig: ConfigValues? = null
        var configLoaded = false
        while (currentCoroutineContext().isActive && !configLoaded) {
            when (val cfgRes = deviceRepository.readConfig(uid, homeId, resolved.device.id)) {
                is CloudResult.Ok -> {
                    val cfg = cfgRes.data
                    loadedRev = cfg?.rev
                    loadedConfig = cfg?.let {
                        ConfigValues(
                            tempLimit = it.tempLimit,
                            humLimit = it.humLimit,
                            coldLimit = it.coldLimit,
                            autoAlarm = it.autoAlarm,
                            smokeLimit = it.smokeLimit,
                            smokeAuto = it.smokeAuto
                        )
                    }
                    configLoaded = true
                }
                CloudResult.Unavailable -> {
                    _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.IDLE)
                    return null
                }
                is CloudResult.Err -> {
                    if (!onError(cfgRes.error, cfgRes.message)) return null
                }
            }
        }
        if (!configLoaded) return null

        while (currentCoroutineContext().isActive) {
            when (val cursorRes = deviceRepository.readRecentEventCursors(
                expectedUid = uid,
                homeId = homeId,
                deviceId = resolved.device.id,
                limit = INITIAL_CURSOR_LIMIT
            )) {
                is CloudResult.Ok -> {
                    return InitializedDeviceState(
                        rev = loadedRev,
                        config = loadedConfig,
                        cursors = cursorRes.data
                    )
                }
                CloudResult.Unavailable -> {
                    _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.IDLE)
                    return null
                }
                is CloudResult.Err -> {
                    if (!onError(cursorRes.error, cursorRes.message)) return null
                }
            }
        }
        return null
    }

    private suspend fun runSyncLoop(
        uid: String,
        homeId: String,
        resolved: ResolvedDeviceSession,
        initialized: InitializedDeviceState
    ) = coroutineScope {
        val deviceId = resolved.device.id
        val warningMessage = resolved.warningMessage
        val latestStamped = AtomicReference<StampedInput?>(null)
        val wakeup = Channel<Unit>(Channel.CONFLATED)
        val inBackoff = AtomicBoolean(false)
        val stateWriteSeq = AtomicLong(0L)
        val completedCooldownSeq = AtomicLong(0L)
        val completedHeartbeatSeq = AtomicLong(0L)

        var hasReportedOnlineInSession = false
        var lastPublishedValues: GatewayValues? = null
        var lastPublishedLastSeen = 0L
        var lastStateWriteAtMs: Long? = null
        var lastPublishedTelemetryId: String? = null
        var lastKnownConfig: ConfigValues? = initialized.config
        var lastRev: Long? = initialized.rev
        val cursorMap = HashMap<String, Long>(initialized.cursors)
        var lastSyncAt: Long? = null
        var backoffIndex = 0

        var cooldownJob: Job? = null
        var heartbeatJob: Job? = null
        var backoffJob: Job? = null

        val collectorJob = launch {
            gatewayInputFlow.collect { snapshot ->
                val fresh = snapshot.esp32Online &&
                    snapshot.lastSeen > 0L &&
                    (timeProvider() - snapshot.lastSeen <= STATE_FRESHNESS_WINDOW_MS)
                latestStamped.set(StampedInput(snapshot, fresh))
                if (!inBackoff.get()) {
                    wakeup.trySend(Unit)
                }
            }
        }

        fun scheduleStateTimers(seq: Long) {
            cooldownJob?.cancel()
            cooldownJob = launch {
                delay(STATE_MIN_INTERVAL_MS)
                completedCooldownSeq.set(seq)
                if (!inBackoff.get()) {
                    wakeup.trySend(Unit)
                }
            }
            heartbeatJob?.cancel()
            heartbeatJob = launch {
                delay(HEARTBEAT_INTERVAL_MS)
                completedHeartbeatSeq.set(seq)
                if (!inBackoff.get()) {
                    wakeup.trySend(Unit)
                }
            }
        }

        fun cancelStateTimers() {
            cooldownJob?.cancel()
            cooldownJob = null
            heartbeatJob?.cancel()
            heartbeatJob = null
        }

        suspend fun syncConfig(input: GatewayInputSnapshot): CloudResult<Boolean> {
            val desired = input.toConfigValues()
            if (lastKnownConfig != null && desired == lastKnownConfig) {
                return CloudResult.Ok(false)
            }
            val payload = try {
                CloudMapper.buildConfigPayload(
                    previousRev = lastRev,
                    tempLimit = desired.tempLimit,
                    humLimit = desired.humLimit,
                    coldLimit = desired.coldLimit,
                    autoAlarm = desired.autoAlarm,
                    updatedByUid = uid,
                    timestampValue = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER,
                    smokeLimit = desired.smokeLimit,
                    smokeAuto = desired.smokeAuto
                )
            } catch (e: IllegalArgumentException) {
                return CloudResult.Err(CloudError.InvalidData, e.message)
            }
            when (val writeRes = deviceRepository.publishConfig(uid, homeId, deviceId, payload)) {
                is CloudResult.Ok -> {
                    lastRev = computeNextRev(lastRev)
                    lastKnownConfig = desired
                    return CloudResult.Ok(true)
                }
                CloudResult.Unavailable -> return CloudResult.Unavailable
                is CloudResult.Err -> {
                    if (writeRes.error != CloudError.PermissionDenied) {
                        return writeRes
                    }
                    // PermissionDenied on config can come from rev conflict with another session; re-read once and retry once.
                    val remoteRev = when (val reread = deviceRepository.readConfig(uid, homeId, deviceId)) {
                        is CloudResult.Ok -> reread.data?.rev
                        CloudResult.Unavailable -> return CloudResult.Unavailable
                        is CloudResult.Err -> return reread
                    }
                    lastRev = remoteRev
                    val retryPayload = try {
                        CloudMapper.buildConfigPayload(
                            previousRev = remoteRev,
                            tempLimit = desired.tempLimit,
                            humLimit = desired.humLimit,
                            coldLimit = desired.coldLimit,
                            autoAlarm = desired.autoAlarm,
                            updatedByUid = uid,
                            timestampValue = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER,
                            smokeLimit = desired.smokeLimit,
                            smokeAuto = desired.smokeAuto
                        )
                    } catch (e: IllegalArgumentException) {
                        return CloudResult.Err(CloudError.InvalidData, e.message)
                    }
                    return when (val retryRes = deviceRepository.publishConfig(uid, homeId, deviceId, retryPayload)) {
                        is CloudResult.Ok -> {
                            lastRev = computeNextRev(remoteRev)
                            lastKnownConfig = desired
                            CloudResult.Ok(true)
                        }
                        CloudResult.Unavailable -> CloudResult.Unavailable
                        is CloudResult.Err -> retryRes
                    }
                }
            }
        }

        suspend fun syncTelemetry(input: GatewayInputSnapshot): CloudResult<Boolean> {
            val sample = input.latestSample ?: return CloudResult.Ok(false)
            if (sample.timestamp <= 0L) return CloudResult.Ok(false)
            val built = try {
                CloudMapper.buildTelemetryPayload(
                    sample = sample,
                    reportedByUid = uid,
                    timestampValue = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
                )
            } catch (e: IllegalArgumentException) {
                return CloudResult.Err(CloudError.InvalidData, e.message)
            }
            val (recordId, payload) = built ?: return CloudResult.Ok(false)
            if (recordId == lastPublishedTelemetryId) {
                return CloudResult.Ok(false)
            }
            return when (val res = deviceRepository.publishTelemetry(uid, homeId, deviceId, recordId, payload)) {
                is CloudResult.Ok -> {
                    lastPublishedTelemetryId = recordId
                    CloudResult.Ok(true)
                }
                CloudResult.Unavailable -> CloudResult.Unavailable
                is CloudResult.Err -> res
            }
        }

        suspend fun syncEvents(input: GatewayInputSnapshot): CloudResult<Boolean> {
            if (input.recentEvents.isEmpty()) return CloudResult.Ok(false)
            val sorted = input.recentEvents.sortedWith(
                compareBy<DeviceEvent> { it.seq }.thenBy { it.stream }
            )
            var pushedCount = 0
            var anyWritten = false
            for (event in sorted) {
                currentCoroutineContext().ensureActive()
                if (pushedCount >= MAX_EVENTS_PER_CYCLE) break
                val cloudStream = try {
                    CloudMapper.toCloudStream(event.stream)
                } catch (e: IllegalArgumentException) {
                    return CloudResult.Err(CloudError.InvalidData, e.message)
                }
                val currentCursor = cursorMap[cloudStream]
                if (currentCursor != null && event.seq <= currentCursor) continue

                val (eventId, payload) = try {
                    CloudMapper.buildEventPayload(
                        input = GatewayEventInput.fromDeviceEvent(event),
                        reportedByUid = uid,
                        timestampValue = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
                    )
                } catch (e: IllegalArgumentException) {
                    return CloudResult.Err(CloudError.InvalidData, e.message)
                }
                when (val res = deviceRepository.publishEvent(uid, homeId, deviceId, eventId, payload)) {
                    is CloudResult.Ok -> {
                        cursorMap[cloudStream] = event.seq
                        pushedCount++
                        anyWritten = true
                    }
                    CloudResult.Unavailable -> return CloudResult.Unavailable
                    is CloudResult.Err -> return res
                }
            }
            return CloudResult.Ok(anyWritten)
        }

        suspend fun executeCycle(stamped: StampedInput): CycleOutcome {
            val input = stamped.snapshot
            if (!input.esp32Online) {
                cancelStateTimers()
                if (!hasReportedOnlineInSession) {
                    return CycleOutcome.Done(didWrite = false, pausedOffline = true)
                }

                when (val teleRes = syncTelemetry(input)) {
                    is CloudResult.Ok -> Unit
                    CloudResult.Unavailable -> return CycleOutcome.Unavailable
                    is CloudResult.Err -> return CycleOutcome.Failed(teleRes.error, teleRes.message)
                }
                when (val evRes = syncEvents(input)) {
                    is CloudResult.Ok -> Unit
                    CloudResult.Unavailable -> return CycleOutcome.Unavailable
                    is CloudResult.Err -> return CycleOutcome.Failed(evRes.error, evRes.message)
                }

                val offlineValues = lastPublishedValues ?: input.toGatewayValues()
                val offlineLastSeen = if (lastPublishedLastSeen > 0L) lastPublishedLastSeen else input.lastSeen
                val offlineSnapshot = GatewaySnapshot(
                    online = false,
                    lastSeen = offlineLastSeen,
                    light = offlineValues.light,
                    door = offlineValues.doorOpen,
                    alarm = offlineValues.alarm,
                    motion = offlineValues.motionDetected,
                    monitoring = offlineValues.monitoring,
                    mode = offlineValues.mode.name,
                    temp = offlineValues.temp,
                    hum = offlineValues.hum,
                    smokePpm = offlineValues.smokePpm,
                    smokeAlert = offlineValues.smokeAlert,
                    window = offlineValues.windowOpen,
                    fan = offlineValues.fanOn,
                    longSensors = offlineValues.longSensors
                )
                val offlinePayload = try {
                    CloudMapper.buildGatewayStatePayload(
                        snapshot = offlineSnapshot,
                        reportedByUid = uid,
                        timestampValue = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
                    )
                } catch (e: IllegalArgumentException) {
                    return CycleOutcome.Failed(CloudError.InvalidData, e.message)
                }
                return when (val offlineRes = deviceRepository.publishGatewayState(uid, homeId, deviceId, offlinePayload)) {
                    is CloudResult.Ok -> {
                        hasReportedOnlineInSession = false
                        lastStateWriteAtMs = null
                        stateWriteSeq.incrementAndGet()
                        CycleOutcome.Done(didWrite = true, pausedOffline = true)
                    }
                    CloudResult.Unavailable -> CycleOutcome.Unavailable
                    is CloudResult.Err -> CycleOutcome.Failed(offlineRes.error, offlineRes.message)
                }
            }

            var anyWritten = false
            val nowMs = timeProvider()
            val currentValues = input.toGatewayValues()
            val isFreshNow = input.lastSeen > 0L && (nowMs - input.lastSeen <= STATE_FRESHNESS_WINDOW_MS)
            val canPublishOnline = stamped.freshAtReceipt || isFreshNow

            if (canPublishOnline) {
                val activeSeq = stateWriteSeq.get()
                val prevWriteAt = lastStateWriteAtMs
                val valuesChanged = !hasReportedOnlineInSession ||
                    lastPublishedValues == null ||
                    currentValues != lastPublishedValues
                val cooldownElapsed = !hasReportedOnlineInSession ||
                    completedCooldownSeq.get() == activeSeq ||
                    (prevWriteAt != null && nowMs - prevWriteAt >= STATE_MIN_INTERVAL_MS)
                val heartbeatDue = hasReportedOnlineInSession && (
                    completedHeartbeatSeq.get() == activeSeq ||
                        (prevWriteAt != null && nowMs - prevWriteAt >= HEARTBEAT_INTERVAL_MS)
                    )

                if ((valuesChanged && cooldownElapsed) || heartbeatDue) {
                    val snapshot = GatewaySnapshot(
                        online = true,
                        lastSeen = input.lastSeen,
                        light = currentValues.light,
                        door = currentValues.doorOpen,
                        alarm = currentValues.alarm,
                        motion = currentValues.motionDetected,
                        monitoring = currentValues.monitoring,
                        mode = currentValues.mode.name,
                        temp = currentValues.temp,
                        hum = currentValues.hum,
                        smokePpm = currentValues.smokePpm,
                        smokeAlert = currentValues.smokeAlert,
                        window = currentValues.windowOpen,
                        fan = currentValues.fanOn,
                        longSensors = currentValues.longSensors
                    )
                    val statePayload = try {
                        CloudMapper.buildGatewayStatePayload(
                            snapshot = snapshot,
                            reportedByUid = uid,
                            timestampValue = CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
                        )
                    } catch (e: IllegalArgumentException) {
                        return CycleOutcome.Failed(CloudError.InvalidData, e.message)
                    }
                    when (val stateRes = deviceRepository.publishGatewayState(uid, homeId, deviceId, statePayload)) {
                        is CloudResult.Ok -> {
                            hasReportedOnlineInSession = true
                            lastPublishedValues = currentValues
                            lastPublishedLastSeen = input.lastSeen
                            lastStateWriteAtMs = timeProvider()
                            val newSeq = stateWriteSeq.incrementAndGet()
                            scheduleStateTimers(newSeq)
                            anyWritten = true
                        }
                        CloudResult.Unavailable -> return CycleOutcome.Unavailable
                        is CloudResult.Err -> return CycleOutcome.Failed(stateRes.error, stateRes.message)
                    }
                }
            }

            when (val cfgRes = syncConfig(input)) {
                is CloudResult.Ok -> if (cfgRes.data) anyWritten = true
                CloudResult.Unavailable -> return CycleOutcome.Unavailable
                is CloudResult.Err -> return CycleOutcome.Failed(cfgRes.error, cfgRes.message)
            }

            when (val teleRes = syncTelemetry(input)) {
                is CloudResult.Ok -> if (teleRes.data) anyWritten = true
                CloudResult.Unavailable -> return CycleOutcome.Unavailable
                is CloudResult.Err -> return CycleOutcome.Failed(teleRes.error, teleRes.message)
            }

            when (val evRes = syncEvents(input)) {
                is CloudResult.Ok -> if (evRes.data) anyWritten = true
                CloudResult.Unavailable -> return CycleOutcome.Unavailable
                is CloudResult.Err -> return CycleOutcome.Failed(evRes.error, evRes.message)
            }

            return CycleOutcome.Done(didWrite = anyWritten, pausedOffline = false)
        }

        try {
            while (isActive) {
                wakeup.receive()
                if (inBackoff.get()) continue
                val stamped = latestStamped.get() ?: continue
                when (val outcome = executeCycle(stamped)) {
                    is CycleOutcome.Done -> {
                        backoffIndex = 0
                        inBackoff.set(false)
                        backoffJob?.cancel()
                        if (outcome.didWrite) {
                            lastSyncAt = timeProvider()
                        }
                        _gatewaySync.value = GatewaySyncUiState(
                            phase = if (outcome.pausedOffline) GatewaySyncPhase.PAUSED_LAN_OFFLINE else GatewaySyncPhase.OK,
                            activeDeviceId = deviceId,
                            lastSyncAt = lastSyncAt,
                            warningMessage = warningMessage
                        )
                    }
                    CycleOutcome.Unavailable -> {
                        cancelStateTimers()
                        collectorJob.cancel()
                        backoffJob?.cancel()
                        _gatewaySync.value = GatewaySyncUiState(phase = GatewaySyncPhase.IDLE)
                        return@coroutineScope
                    }
                    is CycleOutcome.Failed -> {
                        if (isRetryableError(outcome.error)) {
                            val delayMs = BACKOFF_DELAYS_MS[backoffIndex]
                            backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_DELAYS_MS.lastIndex)
                            _gatewaySync.value = GatewaySyncUiState(
                                phase = GatewaySyncPhase.ERROR,
                                activeDeviceId = deviceId,
                                lastSyncAt = lastSyncAt,
                                warningMessage = warningMessage,
                                lastError = outcome.error,
                                errorMessage = outcome.message ?: defaultErrorMessage(outcome.error)
                            )
                            inBackoff.set(true)
                            while (wakeup.tryReceive().isSuccess) {
                                // Drain stale wakeup signals buffered before entering backoff
                            }
                            backoffJob?.cancel()
                            backoffJob = launch {
                                delay(delayMs)
                                inBackoff.set(false)
                                wakeup.trySend(Unit)
                            }
                        } else {
                            cancelStateTimers()
                            collectorJob.cancel()
                            backoffJob?.cancel()
                            _gatewaySync.value = GatewaySyncUiState(
                                phase = GatewaySyncPhase.ERROR,
                                activeDeviceId = deviceId,
                                lastSyncAt = lastSyncAt,
                                warningMessage = warningMessage,
                                lastError = outcome.error,
                                errorMessage = outcome.message ?: defaultErrorMessage(outcome.error)
                            )
                            return@coroutineScope
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            cancelStateTimers()
            backoffJob?.cancel()
            collectorJob.cancel()
            wakeup.close()
        }
    }

    private fun CloudUiState.toSyncSessionKey(): SyncSessionKey {
        val signedOutOrUnavailable = status == CloudStatus.SIGNED_OUT || status == CloudStatus.UNAVAILABLE
        val readyOwner = status == CloudStatus.READY &&
            !currentUid.isNullOrBlank() &&
            !selectedHomeId.isNullOrBlank() &&
            selectedHome?.role == CloudRole.OWNER
        return SyncSessionKey(
            uid = if (readyOwner) currentUid else null,
            homeId = if (readyOwner) selectedHomeId else null,
            isReadyOwner = readyOwner,
            isSignedOutOrUnavailable = signedOutOrUnavailable
        )
    }

    private fun GatewayInputSnapshot.toGatewayValues(): GatewayValues = GatewayValues(
        light = light,
        doorOpen = doorOpen,
        alarm = alarm,
        motionDetected = motionDetected,
        monitoring = monitoring,
        mode = mode,
        temp = temperature?.takeIf { it > CloudMapper.MIN_TEMP_EXCLUSIVE && it < CloudMapper.MAX_TEMP_EXCLUSIVE },
        hum = humidity?.takeIf { it in CloudMapper.MIN_HUM_INCLUSIVE..CloudMapper.MAX_HUM_INCLUSIVE },
        longSensors = longSensors,
        smokePpm = smokePpm?.takeIf { it in CloudMapper.MIN_SMOKE_PPM_INCLUSIVE..CloudMapper.MAX_SMOKE_PPM_INCLUSIVE },
        smokeAlert = smokeAlert,
        windowOpen = windowOpen,
        fanOn = fanOn
    )

    private fun GatewayInputSnapshot.toConfigValues(): ConfigValues = ConfigValues(
        tempLimit = temperatureLimit,
        humLimit = humidityLimit,
        coldLimit = coldLimit,
        autoAlarm = autoAlarm,
        smokeLimit = smokeLimit,
        smokeAuto = smokeAuto
    )

    private fun computeNextRev(previousRev: Long?): Long =
        if (previousRev == null || previousRev < 1L) 1L else previousRev + 1L

    private fun isRetryableError(error: CloudError): Boolean =
        error == CloudError.Offline || error == CloudError.Timeout

    private fun defaultErrorMessage(error: CloudError): String = when (error) {
        CloudError.Offline -> "Không có kết nối mạng Cloud. Đang thử lại…"
        CloudError.Timeout -> "Kết nối Cloud quá hạn. Đang thử lại…"
        CloudError.PermissionDenied -> "Không có quyền đồng bộ thiết bị lên Cloud."
        CloudError.Unauthenticated -> "Phiên đăng nhập Cloud không hợp lệ."
        CloudError.InvalidData -> "Dữ liệu đồng bộ không hợp lệ."
        CloudError.NotFound -> "Không tìm thấy nút dữ liệu thiết bị trên Cloud."
        CloudError.Conflict -> "Xung đột dữ liệu đồng bộ trên Cloud."
        CloudError.Unknown -> "Lỗi đồng bộ Cloud."
    }

    companion object {
        const val STATE_FRESHNESS_WINDOW_MS: Long = 5_000L
        const val STATE_MIN_INTERVAL_MS: Long = 2_000L
        const val HEARTBEAT_INTERVAL_MS: Long = 60_000L
        const val INITIAL_CURSOR_LIMIT: Int = 100
        const val MAX_EVENTS_PER_CYCLE: Int = 20
        val BACKOFF_DELAYS_MS: LongArray = longArrayOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L)
    }
}

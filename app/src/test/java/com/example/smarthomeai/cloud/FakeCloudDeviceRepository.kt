package com.example.smarthomeai.cloud

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory test double for CloudDeviceRepository (src/test ONLY).
 * Enforces expectedUid consistency, D1 active/revoked rules, create-only semantics,
 * config rev monotonicity, and F3 read/observe streams.
 */
class FakeCloudDeviceRepository(
    initialUid: String? = "test_user_uA",
    initialAvailable: Boolean = true
) : CloudDeviceRepository {

    private var available: Boolean = initialAvailable
    private var currentUid: String? = initialUid
    private var nextFailure: Pair<CloudError, String?>? = null
    private var configWriteFailure: Pair<CloudError, String?>? = null
    private var deviceCounter: Int = 1000

    var enforceConfigRevCheck: Boolean = true
    var onBeforePostUidCheck: (suspend () -> Unit)? = null

    private val devicesByHome = linkedMapOf<String, LinkedHashMap<String, CloudDevice>>()
    private val gatewayStates = linkedMapOf<Pair<String, String>, MutableList<Map<String, Any>>>()
    private val configsByDevice = linkedMapOf<Pair<String, String>, CloudConfigState>()
    private val telemetryByDevice = linkedMapOf<Pair<String, String>, LinkedHashMap<String, Map<String, Any>>>()
    private val eventsByDevice = linkedMapOf<Pair<String, String>, LinkedHashMap<String, Map<String, Any>>>()
    private val forcedPermissionDeniedPaths = linkedSetOf<String>()

    private val gatewaySnapshots = linkedMapOf<Pair<String, String>, GatewayStateSnapshot?>()
    private val telemetrySnapshots = linkedMapOf<Pair<String, String>, CloudTelemetrySnapshot?>()
    private val eventRecordLists = linkedMapOf<Pair<String, String>, List<CloudEventRecord>>()

    private val gatewayStateUpdates = linkedMapOf<Pair<String, String>, MutableSharedFlow<CloudResult<GatewayStateSnapshot?>>>()
    private val telemetryUpdates = linkedMapOf<Pair<String, String>, MutableSharedFlow<CloudResult<CloudTelemetrySnapshot?>>>()
    private val eventsUpdates = linkedMapOf<Pair<String, String>, MutableSharedFlow<CloudResult<List<CloudEventRecord>>>>()
    private val uidChanges = MutableSharedFlow<String?>(extraBufferCapacity = 32)

    val resolveCalls = AtomicInteger(0)
    val findActiveDeviceCalls = AtomicInteger(0)
    val observeGatewayStateCalls = AtomicInteger(0)
    val observeLatestTelemetryCalls = AtomicInteger(0)
    val observeRecentEventsCalls = AtomicInteger(0)
    val publishGatewayCalls = AtomicInteger(0)
    val readConfigCalls = AtomicInteger(0)
    val publishConfigCalls = AtomicInteger(0)
    val publishTelemetryCalls = AtomicInteger(0)
    val readEventCursorsCalls = AtomicInteger(0)
    val publishEventCalls = AtomicInteger(0)

    val activeGatewayStateListeners = AtomicInteger(0)
    val activeTelemetryListeners = AtomicInteger(0)
    val activeEventsListeners = AtomicInteger(0)
    val totalActiveListeners: Int
        get() = activeGatewayStateListeners.get() + activeTelemetryListeners.get() + activeEventsListeners.get()

    fun setAvailable(isAvailable: Boolean) {
        available = isAvailable
    }

    fun setSignedInUid(uid: String?) {
        currentUid = uid
        uidChanges.tryEmit(uid)
    }

    fun failNext(error: CloudError, message: String? = null) {
        nextFailure = error to message
    }

    fun failNextConfigWrite(error: CloudError, message: String? = null) {
        configWriteFailure = error to message
    }

    fun denyPathWithoutData(path: String) {
        forcedPermissionDeniedPaths.add(path)
    }

    fun clearDeniedPath(path: String) {
        forcedPermissionDeniedPaths.remove(path)
    }

    fun seedDevice(homeId: String, device: CloudDevice) {
        val map = devicesByHome.getOrPut(homeId) { linkedMapOf() }
        map[device.id] = device
    }

    fun seedConfig(homeId: String, deviceId: String, config: CloudConfigState) {
        configsByDevice[homeId to deviceId] = config
    }

    fun seedTelemetryRecord(homeId: String, deviceId: String, recordId: String, payload: Map<String, Any>) {
        val map = telemetryByDevice.getOrPut(homeId to deviceId) { linkedMapOf() }
        map[recordId] = payload
        refreshTelemetrySnapshot(homeId, deviceId)
    }

    fun seedEventRecord(homeId: String, deviceId: String, eventId: String, payload: Map<String, Any>) {
        val map = eventsByDevice.getOrPut(homeId to deviceId) { linkedMapOf() }
        map[eventId] = payload
        refreshEventsSnapshot(homeId, deviceId)
    }

    fun seedGatewayStateSnapshot(homeId: String, deviceId: String, snapshot: GatewayStateSnapshot?) {
        val key = homeId to deviceId
        gatewaySnapshots[key] = snapshot
        gatewayFlow(key).tryEmit(CloudResult.Ok(snapshot))
    }

    fun seedLatestTelemetrySnapshot(homeId: String, deviceId: String, snapshot: CloudTelemetrySnapshot?) {
        val key = homeId to deviceId
        telemetrySnapshots[key] = snapshot
        telemetryFlow(key).tryEmit(CloudResult.Ok(snapshot))
    }

    fun seedRecentEvents(homeId: String, deviceId: String, events: List<CloudEventRecord>) {
        val key = homeId to deviceId
        val sorted = RtdbDeviceRepository.sortRecentEventsDescending(events)
        eventRecordLists[key] = sorted
        eventsFlow(key).tryEmit(CloudResult.Ok(sorted))
    }

    fun emitGatewayStateError(
        homeId: String,
        deviceId: String,
        error: CloudError,
        message: String? = null
    ) {
        gatewayFlow(homeId to deviceId).tryEmit(
            CloudResult.Err(error, message ?: "Simulated error: $error")
        )
    }

    fun emitTelemetryError(
        homeId: String,
        deviceId: String,
        error: CloudError,
        message: String? = null
    ) {
        telemetryFlow(homeId to deviceId).tryEmit(
            CloudResult.Err(error, message ?: "Simulated error: $error")
        )
    }

    fun emitEventsError(
        homeId: String,
        deviceId: String,
        error: CloudError,
        message: String? = null
    ) {
        eventsFlow(homeId to deviceId).tryEmit(
            CloudResult.Err(error, message ?: "Simulated error: $error")
        )
    }

    private fun gatewayFlow(key: Pair<String, String>): MutableSharedFlow<CloudResult<GatewayStateSnapshot?>> =
        gatewayStateUpdates.getOrPut(key) { MutableSharedFlow(extraBufferCapacity = 64) }

    private fun telemetryFlow(key: Pair<String, String>): MutableSharedFlow<CloudResult<CloudTelemetrySnapshot?>> =
        telemetryUpdates.getOrPut(key) { MutableSharedFlow(extraBufferCapacity = 64) }

    private fun eventsFlow(key: Pair<String, String>): MutableSharedFlow<CloudResult<List<CloudEventRecord>>> =
        eventsUpdates.getOrPut(key) { MutableSharedFlow(extraBufferCapacity = 64) }

    private fun resolveReceivedAt(raw: Any?): Long =
        RtdbDeviceRepository.safeLong(raw)?.takeIf { it > 0L } ?: System.currentTimeMillis()

    private fun refreshTelemetrySnapshot(homeId: String, deviceId: String) {
        val key = homeId to deviceId
        val latest = telemetryByDevice[key]?.entries?.maxByOrNull { it.key } ?: return
        @Suppress("UNCHECKED_CAST")
        val values = latest.value["values"] as? Map<String, Any?>
        val parsed = RtdbDeviceRepository.parseTelemetryValues(
            recordId = latest.key,
            rawTemp = values?.get("temp"),
            rawHum = values?.get("hum"),
            rawReceivedAt = resolveReceivedAt(latest.value["receivedAt"]),
            rawDeviceTs = latest.value["deviceTs"]
        )
        if (parsed != null) {
            telemetrySnapshots[key] = parsed
            telemetryFlow(key).tryEmit(CloudResult.Ok(parsed))
        }
    }

    private fun refreshEventsSnapshot(homeId: String, deviceId: String) {
        val key = homeId to deviceId
        val parsed = eventsByDevice[key]?.entries?.mapNotNull { (id, payload) ->
            RtdbDeviceRepository.parseEventValues(
                id = id,
                rawType = payload["type"],
                rawSeverity = payload["severity"],
                rawStream = payload["stream"],
                rawSeq = payload["seq"],
                rawReceivedAt = resolveReceivedAt(payload["receivedAt"]),
                rawDeviceTs = payload["deviceTs"],
                rawDetail = payload["detail"]
            )
        }.orEmpty()
        val sorted = RtdbDeviceRepository.sortRecentEventsDescending(parsed)
        eventRecordLists[key] = sorted
        eventsFlow(key).tryEmit(CloudResult.Ok(sorted))
    }

    fun getDevices(homeId: String): List<CloudDevice> =
        devicesByHome[homeId]?.values?.toList().orEmpty()

    fun getPublishedGatewayStates(homeId: String, deviceId: String): List<Map<String, Any>> =
        gatewayStates[homeId to deviceId]?.toList().orEmpty()

    fun getLatestGatewayState(homeId: String, deviceId: String): Map<String, Any>? =
        gatewayStates[homeId to deviceId]?.lastOrNull()

    fun getConfig(homeId: String, deviceId: String): CloudConfigState? =
        configsByDevice[homeId to deviceId]

    fun getTelemetryRecords(homeId: String, deviceId: String): Map<String, Map<String, Any>> =
        telemetryByDevice[homeId to deviceId]?.toMap().orEmpty()

    fun getEvents(homeId: String, deviceId: String): Map<String, Map<String, Any>> =
        eventsByDevice[homeId to deviceId]?.toMap().orEmpty()

    private fun preCheck(expectedUid: String): CloudResult.Err? {
        if (!available) return null
        val uid = currentUid
        if (uid == null || !RtdbPaths.isValidUid(uid) || uid != expectedUid) {
            return CloudResult.Err(CloudError.Unauthenticated, "UID mismatch or signed out.")
        }
        nextFailure?.let { (err, msg) ->
            nextFailure = null
            return CloudResult.Err(err, msg ?: "Simulated error: $err")
        }
        return null
    }

    private suspend fun checkPostUid(expectedUid: String): CloudResult.Err? {
        onBeforePostUidCheck?.invoke()
        if (currentUid != expectedUid) {
            return CloudResult.Err(CloudError.Unauthenticated, "UID changed during operation.")
        }
        return null
    }

    override suspend fun resolveOrRegisterDevice(
        expectedUid: String,
        homeId: String
    ): CloudResult<DeviceResolveResult> {
        resolveCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId")
        }
        checkPostUid(expectedUid)?.let { return it }

        val existing = devicesByHome[homeId]?.values?.toList().orEmpty()
        val evaluated = RtdbDeviceRepository.evaluateDevicesSnapshot(existing)
        if (evaluated != null) {
            return CloudResult.Ok(evaluated)
        }

        val newId = "dev_FAKE_${deviceCounter++}"
        val created = CloudDevice(
            id = newId,
            name = CloudMapper.DEFAULT_DEVICE_NAME,
            model = CloudMapper.DEFAULT_DEVICE_MODEL,
            status = DeviceStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            createdBy = expectedUid
        )
        seedDevice(homeId, created)
        return CloudResult.Ok(DeviceResolveResult.Active(created, hasMultipleActive = false))
    }

    override suspend fun findActiveDevice(
        expectedUid: String,
        homeId: String
    ): CloudResult<CloudDevice?> {
        findActiveDeviceCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId")
        }
        checkPostUid(expectedUid)?.let { return it }

        val existing = devicesByHome[homeId]?.values?.toList().orEmpty()
        val evaluated = RtdbDeviceRepository.evaluateDevicesSnapshot(existing)
            ?: return CloudResult.Ok(null)
        return when (evaluated) {
            is DeviceResolveResult.Active -> CloudResult.Ok(evaluated.device)
            is DeviceResolveResult.MultipleActiveWarning -> CloudResult.Ok(evaluated.primaryDevice)
            is DeviceResolveResult.RevokedOnly -> CloudResult.Ok(null)
        }
    }

    override fun observeGatewayState(
        expectedUid: String,
        homeId: String,
        deviceId: String
    ): Flow<CloudResult<GatewayStateSnapshot?>> = observeFakeStream(
        expectedUid = expectedUid,
        homeId = homeId,
        deviceId = deviceId,
        deniedPathProvider = { RtdbPaths.deviceGatewayStatePath(homeId, deviceId) },
        callCounter = observeGatewayStateCalls,
        activeCounter = activeGatewayStateListeners,
        updatesFlowProvider = { gatewayFlow(homeId to deviceId) },
        initialValueProvider = { gatewaySnapshots[homeId to deviceId] }
    )

    override fun observeLatestTelemetry(
        expectedUid: String,
        homeId: String,
        deviceId: String
    ): Flow<CloudResult<CloudTelemetrySnapshot?>> = observeFakeStream(
        expectedUid = expectedUid,
        homeId = homeId,
        deviceId = deviceId,
        deniedPathProvider = { RtdbPaths.deviceTelemetryPath(homeId, deviceId) },
        callCounter = observeLatestTelemetryCalls,
        activeCounter = activeTelemetryListeners,
        updatesFlowProvider = { telemetryFlow(homeId to deviceId) },
        initialValueProvider = { telemetrySnapshots[homeId to deviceId] }
    )

    override fun observeRecentEvents(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        limit: Int
    ): Flow<CloudResult<List<CloudEventRecord>>> {
        val boundedLimit = limit.coerceIn(1, 100)
        return observeFakeStream(
            expectedUid = expectedUid,
            homeId = homeId,
            deviceId = deviceId,
            deniedPathProvider = { RtdbPaths.deviceEventsPath(homeId, deviceId) },
            callCounter = observeRecentEventsCalls,
            activeCounter = activeEventsListeners,
            updatesFlowProvider = { eventsFlow(homeId to deviceId) },
            initialValueProvider = { eventRecordLists[homeId to deviceId].orEmpty().take(boundedLimit) },
            transformOk = { it.take(boundedLimit) }
        )
    }

    private fun <T> observeFakeStream(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        deniedPathProvider: () -> String,
        callCounter: AtomicInteger,
        activeCounter: AtomicInteger,
        updatesFlowProvider: () -> MutableSharedFlow<CloudResult<T>>,
        initialValueProvider: () -> T,
        transformOk: (T) -> T = { it }
    ): Flow<CloudResult<T>> = callbackFlow {
        callCounter.incrementAndGet()
        if (!available) {
            trySend(CloudResult.Unavailable)
            close()
            return@callbackFlow
        }
        preCheck(expectedUid)?.let { err ->
            trySend(err)
            close()
            return@callbackFlow
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            trySend(CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId."))
            close()
            return@callbackFlow
        }
        val deniedPath = deniedPathProvider()
        if (deniedPath in forcedPermissionDeniedPaths) {
            trySend(CloudResult.Err(CloudError.PermissionDenied, "Denied path: $deniedPath"))
            close()
            return@callbackFlow
        }

        activeCounter.incrementAndGet()
        trySend(CloudResult.Ok(transformOk(initialValueProvider())))

        val updatesJob = launch {
            updatesFlowProvider().collect { result ->
                if (currentUid != expectedUid) {
                    trySend(CloudResult.Err(CloudError.Unauthenticated, "UID mismatch during observation."))
                    close()
                    return@collect
                }
                val out = when (result) {
                    is CloudResult.Ok -> CloudResult.Ok(transformOk(result.data))
                    is CloudResult.Err -> result
                    CloudResult.Unavailable -> result
                }
                trySend(out)
            }
        }

        val uidJob = launch {
            uidChanges.collect { newUid ->
                if (newUid != expectedUid) {
                    trySend(CloudResult.Err(CloudError.Unauthenticated, "UID changed during observation."))
                    close()
                }
            }
        }

        awaitClose {
            updatesJob.cancel()
            uidJob.cancel()
            activeCounter.decrementAndGet()
        }
    }

    override suspend fun publishGatewayState(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        payload: Map<String, Any>
    ): CloudResult<Unit> {
        publishGatewayCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId) || payload.isEmpty()) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid gateway state write arguments.")
        }
        val path = RtdbPaths.deviceGatewayStatePath(homeId, deviceId)
        if (path in forcedPermissionDeniedPaths) {
            return CloudResult.Err(CloudError.PermissionDenied, "Denied path: $path")
        }
        checkPostUid(expectedUid)?.let { return it }

        val key = homeId to deviceId
        val list = gatewayStates.getOrPut(key) { mutableListOf() }
        list.add(payload)
        @Suppress("UNCHECKED_CAST")
        val values = payload["values"] as? Map<String, Any?>
        val parsed = RtdbDeviceRepository.parseGatewayStateValues(
            rawOnline = payload["online"],
            rawReportedBy = payload["reportedBy"],
            rawReceivedAt = resolveReceivedAt(payload["receivedAt"]),
            rawDeviceTs = payload["deviceTs"],
            rawTemp = values?.get("temp"),
            rawHum = values?.get("hum"),
            rawMotion = values?.get("motion"),
            rawLight = values?.get("light"),
            rawDoor = values?.get("door"),
            rawAlarm = values?.get("alarm"),
            rawMonitoring = values?.get("monitoring"),
            rawMode = values?.get("mode")
        )
        if (parsed != null) {
            gatewaySnapshots[key] = parsed
            gatewayFlow(key).tryEmit(CloudResult.Ok(parsed))
        }
        return CloudResult.Ok(Unit)
    }

    override suspend fun readConfig(
        expectedUid: String,
        homeId: String,
        deviceId: String
    ): CloudResult<CloudConfigState?> {
        readConfigCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }
        checkPostUid(expectedUid)?.let { return it }
        return CloudResult.Ok(configsByDevice[homeId to deviceId])
    }

    override suspend fun publishConfig(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        payload: Map<String, Any>
    ): CloudResult<Unit> {
        publishConfigCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        configWriteFailure?.let { (err, msg) ->
            configWriteFailure = null
            return CloudResult.Err(err, msg ?: "Simulated config error: $err")
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }
        val parsed = RtdbDeviceRepository.parseConfigValues(
            rawRev = payload["rev"],
            rawTempLimit = payload["tempLimit"],
            rawHumLimit = payload["humLimit"],
            rawColdLimit = payload["coldLimit"],
            rawAutoAlarm = payload["autoAlarm"],
            rawUpdatedBy = payload["updatedBy"],
            rawUpdatedAt = System.currentTimeMillis()
        ) ?: return CloudResult.Err(CloudError.InvalidData, "Invalid config payload.")

        val current = configsByDevice[homeId to deviceId]
        val expectedRev = (current?.rev ?: 0L) + 1L
        if (enforceConfigRevCheck && parsed.rev != expectedRev) {
            return CloudResult.Err(
                CloudError.PermissionDenied,
                "Config rev mismatch: expected $expectedRev, got ${parsed.rev}"
            )
        }
        checkPostUid(expectedUid)?.let { return it }

        configsByDevice[homeId to deviceId] = parsed
        return CloudResult.Ok(Unit)
    }

    override suspend fun publishTelemetry(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        recordId: String,
        payload: Map<String, Any>
    ): CloudResult<CreateOnlyWriteOutcome> {
        publishTelemetryCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId) ||
            !RtdbPaths.isValidEntityId(deviceId) ||
            !RtdbPaths.isValidTelemetryRecordId(recordId) ||
            payload.isEmpty()
        ) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid telemetry arguments.")
        }
        val path = RtdbPaths.deviceTelemetryRecordPath(homeId, deviceId, recordId)
        if (path in forcedPermissionDeniedPaths) {
            return CloudResult.Err(CloudError.PermissionDenied, "Denied path: $path")
        }
        checkPostUid(expectedUid)?.let { return it }

        val map = telemetryByDevice.getOrPut(homeId to deviceId) { linkedMapOf() }
        return if (map.containsKey(recordId)) {
            CloudResult.Ok(CreateOnlyWriteOutcome.ALREADY_EXISTS)
        } else {
            map[recordId] = payload
            refreshTelemetrySnapshot(homeId, deviceId)
            CloudResult.Ok(CreateOnlyWriteOutcome.WRITTEN)
        }
    }

    override suspend fun readRecentEventCursors(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        limit: Int
    ): CloudResult<Map<String, Long>> {
        readEventCursorsCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }
        checkPostUid(expectedUid)?.let { return it }

        val records = eventsByDevice[homeId to deviceId]?.values?.toList().orEmpty()
        val tail = records.takeLast(limit.coerceAtLeast(1))
        val cursors = linkedMapOf<String, Long>()
        for (item in tail) {
            val (stream, seq) = RtdbDeviceRepository.parseEventCursorEntry(
                rawStream = item["stream"],
                rawSeq = item["seq"]
            ) ?: continue
            val prev = cursors[stream]
            if (prev == null || seq > prev) {
                cursors[stream] = seq
            }
        }
        return CloudResult.Ok(cursors)
    }

    override suspend fun publishEvent(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        eventId: String,
        payload: Map<String, Any>
    ): CloudResult<CreateOnlyWriteOutcome> {
        publishEventCalls.incrementAndGet()
        if (!available) return CloudResult.Unavailable
        preCheck(expectedUid)?.let { return it }
        if (!RtdbPaths.isValidEntityId(homeId) ||
            !RtdbPaths.isValidEntityId(deviceId) ||
            !RtdbPaths.isValidGatewayEventId(eventId) ||
            payload.isEmpty()
        ) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid event arguments.")
        }
        val path = RtdbPaths.deviceEventPath(homeId, deviceId, eventId)
        if (path in forcedPermissionDeniedPaths) {
            return CloudResult.Err(CloudError.PermissionDenied, "Denied path: $path")
        }
        checkPostUid(expectedUid)?.let { return it }

        val map = eventsByDevice.getOrPut(homeId to deviceId) { linkedMapOf() }
        return if (map.containsKey(eventId)) {
            CloudResult.Ok(CreateOnlyWriteOutcome.ALREADY_EXISTS)
        } else {
            map[eventId] = payload
            refreshEventsSnapshot(homeId, deviceId)
            CloudResult.Ok(CreateOnlyWriteOutcome.WRITTEN)
        }
    }
}

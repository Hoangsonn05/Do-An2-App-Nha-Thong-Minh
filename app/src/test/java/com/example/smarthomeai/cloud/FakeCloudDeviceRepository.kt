package com.example.smarthomeai.cloud

import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory test double for CloudDeviceRepository (src/test ONLY).
 * Enforces expectedUid consistency, D1 active/revoked rules, create-only semantics, and config rev monotonicity.
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

    val resolveCalls = AtomicInteger(0)
    val publishGatewayCalls = AtomicInteger(0)
    val readConfigCalls = AtomicInteger(0)
    val publishConfigCalls = AtomicInteger(0)
    val publishTelemetryCalls = AtomicInteger(0)
    val readEventCursorsCalls = AtomicInteger(0)
    val publishEventCalls = AtomicInteger(0)

    fun setAvailable(isAvailable: Boolean) {
        available = isAvailable
    }

    fun setSignedInUid(uid: String?) {
        currentUid = uid
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
    }

    fun seedEventRecord(homeId: String, deviceId: String, eventId: String, payload: Map<String, Any>) {
        val map = eventsByDevice.getOrPut(homeId to deviceId) { linkedMapOf() }
        map[eventId] = payload
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

        val list = gatewayStates.getOrPut(homeId to deviceId) { mutableListOf() }
        list.add(payload)
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
            CloudResult.Ok(CreateOnlyWriteOutcome.WRITTEN)
        }
    }
}

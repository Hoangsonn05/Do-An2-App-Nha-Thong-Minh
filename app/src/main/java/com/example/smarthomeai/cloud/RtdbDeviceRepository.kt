package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.LongSensorReadings
import com.example.smarthomeai.data.HomeMode
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Firebase Realtime Database implementation of CloudDeviceRepository (F2a).
 *
 * - Enforces provider configuration, expectedUid consistency pre/post network calls, and RtdbPaths validation.
 * - Wraps all single-shot writes and single-value reads in withTimeout(timeoutMs).
 * - Distinguishes create-only duplicate writes (ALREADY_EXISTS) from true PermissionDenied on telemetry/events.
 * - Parses DataSnapshot values safely without DataSnapshot.getValue(Class) (MIN-02).
 */
class RtdbDeviceRepository(
    private val provider: FirebaseProvider,
    private val timeoutMs: Long = CLOUD_OPERATION_TIMEOUT_MS
) : CloudDeviceRepository {

    private data class AuthorizedContext(
        val db: FirebaseDatabase,
        val uid: String
    )

    private sealed class SnapshotReadResult {
        data class Found(val snapshot: DataSnapshot) : SnapshotReadResult()
        data class Error(val error: CloudError, val message: String?) : SnapshotReadResult()
    }

    override suspend fun resolveOrRegisterDevice(
        expectedUid: String,
        homeId: String
    ): CloudResult<DeviceResolveResult> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId")
        }

        val devicesRef = ctx.db.getReference(RtdbPaths.homeDevicesPath(homeId))
        val readResult = readSingleSnapshot(devicesRef)
        if (readResult is SnapshotReadResult.Error) {
            return CloudResult.Err(readResult.error, readResult.message)
        }
        ensureSameUid(expectedUid)?.let { return it }

        val snapshot = (readResult as SnapshotReadResult.Found).snapshot
        if (snapshot.exists() && snapshot.hasChildren()) {
            val evaluated = parseDevicesSnapshot(snapshot)
                ?: return CloudResult.Err(
                    CloudError.InvalidData,
                    "Malformed device entries under homes/$homeId/devices."
                )
            return CloudResult.Ok(evaluated)
        }

        val newDeviceId = devicesRef.push().key
        if (newDeviceId == null || !RtdbPaths.isValidEntityId(newDeviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Generated deviceId is invalid.")
        }

        val registrationPayload = try {
            CloudMapper.buildDeviceRegistrationPayload(
                createdByUid = ctx.uid,
                timestampValue = ServerValue.TIMESTAMP
            )
        } catch (e: IllegalArgumentException) {
            return CloudResult.Err(CloudError.InvalidData, e.message)
        }

        val writeError = runTimedWrite {
            devicesRef.child(newDeviceId).setValue(registrationPayload).await()
        }
        if (writeError != null) {
            return writeError
        }
        ensureSameUid(expectedUid)?.let { return it }

        val createdDevice = CloudDevice(
            id = newDeviceId,
            name = CloudMapper.DEFAULT_DEVICE_NAME,
            model = CloudMapper.DEFAULT_DEVICE_MODEL,
            status = DeviceStatus.ACTIVE,
            createdAt = null,
            createdBy = ctx.uid
        )
        return CloudResult.Ok(DeviceResolveResult.Active(createdDevice, hasMultipleActive = false))
    }

    override suspend fun findActiveDevice(
        expectedUid: String,
        homeId: String
    ): CloudResult<CloudDevice?> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId: $homeId")
        }

        val devicesRef = ctx.db.getReference(RtdbPaths.homeDevicesPath(homeId))
        val readResult = readSingleSnapshot(devicesRef)
        if (readResult is SnapshotReadResult.Error) {
            return CloudResult.Err(readResult.error, readResult.message)
        }
        ensureSameUid(expectedUid)?.let { return it }

        val snapshot = (readResult as SnapshotReadResult.Found).snapshot
        if (!snapshot.exists() || !snapshot.hasChildren()) {
            return CloudResult.Ok(null)
        }
        val evaluated = parseDevicesSnapshot(snapshot)
            ?: return CloudResult.Err(
                CloudError.InvalidData,
                "Malformed device entries under homes/$homeId/devices."
            )
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
    ): Flow<CloudResult<GatewayStateSnapshot?>> = observeDeviceQuery(
        expectedUid = expectedUid,
        homeId = homeId,
        deviceId = deviceId,
        queryProvider = { db ->
            db.getReference(RtdbPaths.deviceGatewayStatePath(homeId, deviceId))
        }
    ) { snapshot ->
        if (!snapshot.exists()) {
            CloudResult.Ok(null)
        } else {
            val valuesNode = snapshot.child("values")
            val parsed = parseGatewayStateValues(
                rawOnline = snapshot.child("online").value,
                rawReportedBy = snapshot.child("reportedBy").value,
                rawReceivedAt = snapshot.child("receivedAt").value,
                rawDeviceTs = snapshot.child("deviceTs").value,
                rawTemp = valuesNode.child("temp").value,
                rawHum = valuesNode.child("hum").value,
                rawMotion = valuesNode.child("motion").value,
                rawLight = valuesNode.child("light").value,
                rawDoor = valuesNode.child("door").value,
                rawAlarm = valuesNode.child("alarm").value,
                rawMonitoring = valuesNode.child("monitoring").value,
                rawMode = valuesNode.child("mode").value,
                rawSmokePpm = valuesNode.child("smokePpm").value,
                rawSmokeAlert = valuesNode.child("smokeAlert").value,
                rawWindow = valuesNode.child("window").value,
                rawFan = valuesNode.child("fan").value,
                rawLongSensors = valuesNode.child("longSensors").value
            )
            if (parsed != null) {
                CloudResult.Ok(parsed)
            } else {
                CloudResult.Err(
                    CloudError.InvalidData,
                    "Malformed gateway state snapshot for $deviceId."
                )
            }
        }
    }

    override fun observeLatestTelemetry(
        expectedUid: String,
        homeId: String,
        deviceId: String
    ): Flow<CloudResult<CloudTelemetrySnapshot?>> = observeDeviceQuery(
        expectedUid = expectedUid,
        homeId = homeId,
        deviceId = deviceId,
        queryProvider = { db ->
            db.getReference(RtdbPaths.deviceTelemetryPath(homeId, deviceId))
                .orderByKey()
                .limitToLast(1)
        }
    ) { snapshot ->
        if (!snapshot.exists() || !snapshot.hasChildren()) {
            CloudResult.Ok(null)
        } else {
            val latestChild = snapshot.children.lastOrNull()
            if (latestChild == null) {
                CloudResult.Ok(null)
            } else {
                val valuesNode = latestChild.child("values")
                val parsed = parseTelemetryValues(
                    recordId = latestChild.key,
                    rawTemp = valuesNode.child("temp").value,
                    rawHum = valuesNode.child("hum").value,
                    rawReceivedAt = latestChild.child("receivedAt").value,
                    rawDeviceTs = latestChild.child("deviceTs").value,
                    rawSmokePpm = valuesNode.child("smokePpm").value
                )
                if (parsed != null) {
                    CloudResult.Ok(parsed)
                } else {
                    CloudResult.Err(
                        CloudError.InvalidData,
                        "Malformed telemetry snapshot for $deviceId."
                    )
                }
            }
        }
    }

    override fun observeRecentEvents(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        limit: Int
    ): Flow<CloudResult<List<CloudEventRecord>>> {
        val boundedLimit = limit.coerceIn(1, MAX_OBSERVE_EVENTS_LIMIT)
        return observeDeviceQuery(
            expectedUid = expectedUid,
            homeId = homeId,
            deviceId = deviceId,
            queryProvider = { db ->
                db.getReference(RtdbPaths.deviceEventsPath(homeId, deviceId))
                    .orderByChild("receivedAt")
                    .limitToLast(boundedLimit)
            }
        ) { snapshot ->
            if (!snapshot.exists() || !snapshot.hasChildren()) {
                CloudResult.Ok(emptyList())
            } else {
                val parsed = snapshot.children.mapNotNull { child ->
                    parseEventValues(
                        id = child.key,
                        rawType = child.child("type").value,
                        rawSeverity = child.child("severity").value,
                        rawStream = child.child("stream").value,
                        rawSeq = child.child("seq").value,
                        rawReceivedAt = child.child("receivedAt").value,
                        rawDeviceTs = child.child("deviceTs").value,
                        rawDetail = child.child("detail").value
                    )
                }
                if (parsed.isEmpty()) {
                    CloudResult.Err(
                        CloudError.InvalidData,
                        "Malformed event entries for $deviceId."
                    )
                } else {
                    CloudResult.Ok(sortRecentEventsDescending(parsed, boundedLimit))
                }
            }
        }
    }

    override suspend fun publishGatewayState(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        payload: Map<String, Any>
    ): CloudResult<Unit> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }
        if (payload.isEmpty() || containsForbiddenSecretKey(payload)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid gateway state payload.")
        }

        val finalPayload = withServerTimestamp(payload, "receivedAt")
        val ref = ctx.db.getReference(RtdbPaths.deviceGatewayStatePath(homeId, deviceId))
        val writeError = runTimedWrite {
            ref.setValue(finalPayload).await()
        }
        if (writeError != null) {
            return writeError
        }
        ensureSameUid(expectedUid)?.let { return it }
        return CloudResult.Ok(Unit)
    }

    override suspend fun readConfig(
        expectedUid: String,
        homeId: String,
        deviceId: String
    ): CloudResult<CloudConfigState?> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }

        val ref = ctx.db.getReference(RtdbPaths.deviceConfigPath(homeId, deviceId))
        val readResult = readSingleSnapshot(ref)
        if (readResult is SnapshotReadResult.Error) {
            return CloudResult.Err(readResult.error, readResult.message)
        }
        ensureSameUid(expectedUid)?.let { return it }

        val snapshot = (readResult as SnapshotReadResult.Found).snapshot
        if (!snapshot.exists()) {
            return CloudResult.Ok(null)
        }

        val parsed = parseConfigValues(
            rawRev = snapshot.child("rev").value,
            rawTempLimit = snapshot.child("tempLimit").value,
            rawHumLimit = snapshot.child("humLimit").value,
            rawColdLimit = snapshot.child("coldLimit").value,
            rawAutoAlarm = snapshot.child("autoAlarm").value,
            rawUpdatedBy = snapshot.child("updatedBy").value,
            rawUpdatedAt = snapshot.child("updatedAt").value,
            rawSmokeLimit = snapshot.child("smokeLimit").value,
            rawSmokeAuto = snapshot.child("smokeAuto").value
        ) ?: return CloudResult.Err(
            CloudError.InvalidData,
            "Malformed device config snapshot for $deviceId."
        )

        return CloudResult.Ok(parsed)
    }

    override suspend fun publishConfig(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        payload: Map<String, Any>
    ): CloudResult<Unit> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }
        if (payload.isEmpty() || containsForbiddenSecretKey(payload)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid config payload.")
        }

        val finalPayload = withServerTimestamp(payload, "updatedAt")
        val ref = ctx.db.getReference(RtdbPaths.deviceConfigPath(homeId, deviceId))
        val writeError = runTimedWrite {
            ref.setValue(finalPayload).await()
        }
        if (writeError != null) {
            return writeError
        }
        ensureSameUid(expectedUid)?.let { return it }
        return CloudResult.Ok(Unit)
    }

    override suspend fun publishTelemetry(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        recordId: String,
        payload: Map<String, Any>
    ): CloudResult<CreateOnlyWriteOutcome> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId) ||
            !RtdbPaths.isValidEntityId(deviceId) ||
            !RtdbPaths.isValidTelemetryRecordId(recordId)
        ) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid telemetry path identifiers.")
        }
        return writeCreateOnly(
            ctx = ctx,
            expectedUid = expectedUid,
            path = RtdbPaths.deviceTelemetryRecordPath(homeId, deviceId, recordId),
            payload = payload
        )
    }

    override suspend fun readRecentEventCursors(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        limit: Int
    ): CloudResult<Map<String, Long>> {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId.")
        }

        val boundedLimit = limit.coerceIn(1, MAX_CURSOR_LIMIT)
        val eventsQuery = ctx.db.getReference(RtdbPaths.deviceEventsPath(homeId, deviceId))
            .orderByChild("receivedAt")
            .limitToLast(boundedLimit)

        val readResult = readSingleSnapshot(eventsQuery)
        if (readResult is SnapshotReadResult.Error) {
            return CloudResult.Err(readResult.error, readResult.message)
        }
        ensureSameUid(expectedUid)?.let { return it }

        val snapshot = (readResult as SnapshotReadResult.Found).snapshot
        val cursors = LinkedHashMap<String, Long>()
        for (child in snapshot.children) {
            val entry = parseEventCursorEntry(
                rawStream = child.child("stream").value,
                rawSeq = child.child("seq").value
            ) ?: continue
            val (stream, seq) = entry
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
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> return auth
            CloudResult.Unavailable -> return CloudResult.Unavailable
        }
        if (!RtdbPaths.isValidEntityId(homeId) ||
            !RtdbPaths.isValidEntityId(deviceId) ||
            !RtdbPaths.isValidGatewayEventId(eventId)
        ) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid event path identifiers.")
        }
        return writeCreateOnly(
            ctx = ctx,
            expectedUid = expectedUid,
            path = RtdbPaths.deviceEventPath(homeId, deviceId, eventId),
            payload = payload
        )
    }

    private suspend fun writeCreateOnly(
        ctx: AuthorizedContext,
        expectedUid: String,
        path: String,
        payload: Map<String, Any>
    ): CloudResult<CreateOnlyWriteOutcome> {
        if (payload.isEmpty() || containsForbiddenSecretKey(payload)) {
            return CloudResult.Err(CloudError.InvalidData, "Invalid create-only payload.")
        }

        val finalPayload = withServerTimestamp(payload, "receivedAt")
        val ref = ctx.db.getReference(path)
        val writeError = runTimedWrite {
            ref.setValue(finalPayload).await()
        }
        if (writeError == null) {
            ensureSameUid(expectedUid)?.let { return it }
            return CloudResult.Ok(CreateOnlyWriteOutcome.WRITTEN)
        }
        if (writeError.error != CloudError.PermissionDenied) {
            return writeError
        }

        // Create-only RTDB rule rejects overwrites with PermissionDenied; check existence to distinguish duplicate vs true denial.
        ensureSameUid(expectedUid)?.let { return it }
        return when (val existing = readSingleSnapshot(ref)) {
            is SnapshotReadResult.Found -> {
                ensureSameUid(expectedUid)?.let { return it }
                if (existing.snapshot.exists()) {
                    CloudResult.Ok(CreateOnlyWriteOutcome.ALREADY_EXISTS)
                } else {
                    CloudResult.Err(CloudError.PermissionDenied, writeError.message)
                }
            }
            is SnapshotReadResult.Error -> {
                CloudResult.Err(existing.error, existing.message ?: writeError.message)
            }
        }
    }

    private fun requireAuthorizedContext(expectedUid: String): CloudResult<AuthorizedContext> {
        val db = provider.databaseOrNull()
        if (!provider.isConfigured || db == null) {
            return CloudResult.Unavailable
        }
        val currentUid = provider.uid
        if (currentUid == null || !RtdbPaths.isValidUid(currentUid) || currentUid != expectedUid) {
            return CloudResult.Err(
                CloudError.Unauthenticated,
                "User is not signed in or UID does not match expected session."
            )
        }
        return CloudResult.Ok(AuthorizedContext(db, currentUid))
    }

    private fun ensureSameUid(expectedUid: String): CloudResult.Err? {
        return if (provider.uid != expectedUid) {
            CloudResult.Err(
                CloudError.Unauthenticated,
                "Auth UID changed during cloud operation."
            )
        } else {
            null
        }
    }

    private suspend fun runTimedWrite(block: suspend () -> Unit): CloudResult.Err? {
        return try {
            withTimeout(timeoutMs) { block() }
            null
        } catch (e: TimeoutCancellationException) {
            CloudResult.Err(CloudError.Timeout, e.message ?: "Cloud operation timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CloudResult.Err(CloudErrorMapper.mapException(t), t.message)
        }
    }

    private suspend fun readSingleSnapshot(query: Query): SnapshotReadResult {
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (cont.isActive) {
                                cont.resume(SnapshotReadResult.Found(snapshot))
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (cont.isActive) {
                                cont.resume(
                                    SnapshotReadResult.Error(
                                        CloudErrorMapper.mapDatabaseError(error),
                                        error.message
                                    )
                                )
                            }
                        }
                    }
                    query.addListenerForSingleValueEvent(listener)
                    cont.invokeOnCancellation { query.removeEventListener(listener) }
                }
            }
        } catch (e: TimeoutCancellationException) {
            SnapshotReadResult.Error(
                CloudError.Timeout,
                e.message ?: "Cloud operation timed out."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val mapped = CloudErrorMapper.mapException(t)
            val resolved = if (t is DatabaseException && mapped == CloudError.Unknown) {
                CloudError.InvalidData
            } else {
                mapped
            }
            SnapshotReadResult.Error(resolved, t.message)
        }
    }

    private fun withServerTimestamp(payload: Map<String, Any>, key: String): Map<String, Any> {
        if (!payload.containsKey(key)) return payload
        val copy = LinkedHashMap<String, Any>(payload.size)
        copy.putAll(payload)
        copy[key] = ServerValue.TIMESTAMP
        return copy
    }

    private fun parseDevicesSnapshot(snapshot: DataSnapshot): DeviceResolveResult? {
        val devices = snapshot.children.mapNotNull { child ->
            parseDeviceEntry(
                id = child.key,
                rawName = child.child("name").value,
                rawModel = child.child("model").value,
                rawStatus = child.child("status").value,
                rawCreatedAt = child.child("createdAt").value,
                rawCreatedBy = child.child("createdBy").value
            )
        }
        return evaluateDevicesSnapshot(devices)
    }

    private fun <T> observeDeviceQuery(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        queryProvider: (FirebaseDatabase) -> Query,
        parser: (DataSnapshot) -> CloudResult<T>
    ): Flow<CloudResult<T>> = callbackFlow {
        val ctx = when (val auth = requireAuthorizedContext(expectedUid)) {
            is CloudResult.Ok -> auth.data
            is CloudResult.Err -> {
                trySend(auth)
                close()
                return@callbackFlow
            }
            CloudResult.Unavailable -> {
                trySend(CloudResult.Unavailable)
                close()
                return@callbackFlow
            }
        }
        if (!RtdbPaths.isValidEntityId(homeId) || !RtdbPaths.isValidEntityId(deviceId)) {
            trySend(CloudResult.Err(CloudError.InvalidData, "Invalid homeId or deviceId."))
            close()
            return@callbackFlow
        }

        val query = queryProvider(ctx.db)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (provider.uid != expectedUid) {
                    trySend(
                        CloudResult.Err(
                            CloudError.Unauthenticated,
                            "Auth UID changed while observing cloud device data."
                        )
                    )
                    close()
                    return
                }
                val result = try {
                    parser(snapshot)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    val mapped = if (t is DatabaseException) {
                        CloudError.InvalidData
                    } else {
                        CloudErrorMapper.mapException(t)
                    }
                    CloudResult.Err(mapped, t.message)
                }
                trySend(result)
            }

            override fun onCancelled(error: DatabaseError) {
                val result = if (provider.uid != expectedUid) {
                    CloudResult.Err(
                        CloudError.Unauthenticated,
                        "Auth UID changed while observing cloud device data."
                    )
                } else {
                    CloudResult.Err(CloudErrorMapper.mapDatabaseError(error), error.message)
                }
                trySend(result)
                close()
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    private fun containsForbiddenSecretKey(map: Map<*, *>): Boolean {
        for ((k, v) in map) {
            val keyStr = k as? String ?: continue
            if (keyStr.equals(FORBIDDEN_SECRET_KEY, ignoreCase = true)) {
                return true
            }
            if (v is Map<*, *> && containsForbiddenSecretKey(v)) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val MAX_CURSOR_LIMIT: Int = 500
        private const val MAX_OBSERVE_EVENTS_LIMIT: Int = 100
        // Guard constant to reject any accidental map key carrying LAN secret material.
        private const val FORBIDDEN_SECRET_KEY: String = "deviceKey"

        internal fun safeString(raw: Any?): String? = raw as? String

        internal fun safeBoolean(raw: Any?): Boolean? = raw as? Boolean

        internal fun safeLong(raw: Any?): Long? = when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is Short -> raw.toLong()
            is Byte -> raw.toLong()
            is Number -> raw.toDouble().takeIf { it.isFinite() }?.toLong()
            else -> null
        }

        internal fun safeInt(raw: Any?): Int? = when (raw) {
            is Int -> raw
            is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            is Short -> raw.toInt()
            is Byte -> raw.toInt()
            is Number -> raw.toDouble()
                .takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }
                ?.toInt()
            else -> null
        }

        internal fun parseDeviceEntry(
            id: String?,
            rawName: Any?,
            rawModel: Any?,
            rawStatus: Any?,
            rawCreatedAt: Any?,
            rawCreatedBy: Any?
        ): CloudDevice? {
            if (id.isNullOrBlank() || !RtdbPaths.isValidEntityId(id)) return null
            val status = DeviceStatus.fromWire(safeString(rawStatus)) ?: return null
            val name = safeString(rawName) ?: ""
            val model = safeString(rawModel) ?: ""
            val createdAt = safeLong(rawCreatedAt)
            val createdBy = safeString(rawCreatedBy)
            return CloudDevice(
                id = id,
                name = name,
                model = model,
                status = status,
                createdAt = createdAt,
                createdBy = createdBy
            )
        }

        internal fun evaluateDevicesSnapshot(devices: List<CloudDevice>): DeviceResolveResult? {
            if (devices.isEmpty()) return null
            val active = devices.filter { it.status == DeviceStatus.ACTIVE }
            if (active.isNotEmpty()) {
                val sorted = active.sortedWith(
                    compareBy<CloudDevice> { it.createdAt ?: Long.MAX_VALUE }
                        .thenBy { it.id }
                )
                return DeviceResolveResult.Active(
                    device = sorted.first(),
                    hasMultipleActive = sorted.size > 1
                )
            }
            val revokedIds = devices
                .filter { it.status == DeviceStatus.REVOKED }
                .map { it.id }
                .sorted()
            return if (revokedIds.isNotEmpty()) {
                DeviceResolveResult.RevokedOnly(revokedIds)
            } else {
                null
            }
        }

        internal fun parseConfigValues(
            rawRev: Any?,
            rawTempLimit: Any?,
            rawHumLimit: Any?,
            rawColdLimit: Any?,
            rawAutoAlarm: Any?,
            rawUpdatedBy: Any?,
            rawUpdatedAt: Any?,
            rawSmokeLimit: Any? = null,
            rawSmokeAuto: Any? = null
        ): CloudConfigState? {
            val rev = safeLong(rawRev) ?: return null
            val tempLimit = safeInt(rawTempLimit) ?: return null
            val humLimit = safeInt(rawHumLimit) ?: return null
            val coldLimit = safeInt(rawColdLimit) ?: return null
            val autoAlarm = safeBoolean(rawAutoAlarm) ?: return null
            val updatedBy = safeString(rawUpdatedBy)
            val updatedAt = safeLong(rawUpdatedAt)
            val smokeLimit = if (rawSmokeLimit == null) {
                300
            } else {
                safeInt(rawSmokeLimit)
                    ?.takeIf { it in CloudMapper.MIN_CONFIG_SMOKE_LIMIT..CloudMapper.MAX_CONFIG_SMOKE_LIMIT }
                    ?: return null
            }
            val smokeAuto = if (rawSmokeAuto == null) {
                true
            } else {
                safeBoolean(rawSmokeAuto) ?: return null
            }
            return CloudConfigState(
                rev = rev,
                tempLimit = tempLimit,
                humLimit = humLimit,
                coldLimit = coldLimit,
                autoAlarm = autoAlarm,
                updatedBy = updatedBy,
                updatedAt = updatedAt,
                smokeLimit = smokeLimit,
                smokeAuto = smokeAuto
            )
        }

        internal fun parseEventCursorEntry(
            rawStream: Any?,
            rawSeq: Any?
        ): Pair<String, Long>? {
            val stream = safeString(rawStream) ?: return null
            if (!RtdbPaths.isValidCloudStream(stream)) return null
            val seq = safeLong(rawSeq) ?: return null
            if (!RtdbPaths.isValidEventSeq(seq)) return null
            return stream to seq
        }

        internal fun parseGatewayStateValues(
            rawOnline: Any?,
            rawReportedBy: Any?,
            rawReceivedAt: Any?,
            rawDeviceTs: Any?,
            rawTemp: Any?,
            rawHum: Any?,
            rawMotion: Any?,
            rawLight: Any?,
            rawDoor: Any?,
            rawAlarm: Any?,
            rawMonitoring: Any?,
            rawMode: Any?,
            rawSmokePpm: Any? = null,
            rawSmokeAlert: Any? = null,
            rawWindow: Any? = null,
            rawFan: Any? = null,
            rawLongSensors: Any? = null
        ): GatewayStateSnapshot? {
            val online = safeBoolean(rawOnline) ?: return null
            val receivedAt = safeLong(rawReceivedAt)?.takeIf { it > 0L } ?: return null
            val reportedBy = safeString(rawReportedBy)
            val deviceTs = safeLong(rawDeviceTs)?.takeIf { it > 0L }
            val temp = safeInt(rawTemp)
                ?.takeIf { it in (CloudMapper.MIN_TEMP_EXCLUSIVE + 1) until CloudMapper.MAX_TEMP_EXCLUSIVE }
            val hum = safeInt(rawHum)
                ?.takeIf { it in CloudMapper.MIN_HUM_INCLUSIVE..CloudMapper.MAX_HUM_INCLUSIVE }
            val motion = safeBoolean(rawMotion)
            val light = safeBoolean(rawLight)
            val door = safeBoolean(rawDoor)
            val alarm = safeBoolean(rawAlarm)
            val monitoring = safeBoolean(rawMonitoring)
            val mode = safeString(rawMode)?.let { modeStr ->
                runCatching { HomeMode.valueOf(modeStr) }.getOrNull()
            }
            val smokePpm = safeInt(rawSmokePpm)
                ?.takeIf { it in CloudMapper.MIN_SMOKE_PPM_INCLUSIVE..CloudMapper.MAX_SMOKE_PPM_INCLUSIVE }
            val smokeAlert = safeBoolean(rawSmokeAlert)
            val window = safeBoolean(rawWindow)
            val fan = safeBoolean(rawFan)
            return GatewayStateSnapshot(
                online = online,
                reportedBy = reportedBy,
                receivedAt = receivedAt,
                deviceTs = deviceTs,
                temp = temp,
                hum = hum,
                motion = motion,
                light = light,
                door = door,
                alarm = alarm,
                monitoring = monitoring,
                mode = mode,
                smokePpm = smokePpm,
                smokeAlert = smokeAlert,
                window = window,
                fan = fan,
                longSensors = LongSensorReadings.fromGateway(rawLongSensors)
            )
        }

        internal fun parseTelemetryValues(
            recordId: String?,
            rawTemp: Any?,
            rawHum: Any?,
            rawReceivedAt: Any?,
            rawDeviceTs: Any?,
            rawSmokePpm: Any? = null
        ): CloudTelemetrySnapshot? {
            if (recordId.isNullOrBlank() || RtdbPaths.containsForbiddenRtdbChars(recordId)) return null
            val receivedAt = safeLong(rawReceivedAt)?.takeIf { it > 0L } ?: return null
            val temp = safeInt(rawTemp)
                ?.takeIf { it in (CloudMapper.MIN_TEMP_EXCLUSIVE + 1) until CloudMapper.MAX_TEMP_EXCLUSIVE }
            val hum = safeInt(rawHum)
                ?.takeIf { it in CloudMapper.MIN_HUM_INCLUSIVE..CloudMapper.MAX_HUM_INCLUSIVE }
            val smokePpm = safeInt(rawSmokePpm)
                ?.takeIf { it in CloudMapper.MIN_SMOKE_PPM_INCLUSIVE..CloudMapper.MAX_SMOKE_PPM_INCLUSIVE }
            if (temp == null && hum == null && smokePpm == null) return null
            val deviceTs = safeLong(rawDeviceTs)?.takeIf { it > 0L }
            return CloudTelemetrySnapshot(
                recordId = recordId,
                temp = temp,
                hum = hum,
                receivedAt = receivedAt,
                deviceTs = deviceTs,
                smokePpm = smokePpm
            )
        }

        internal fun parseEventValues(
            id: String?,
            rawType: Any?,
            rawSeverity: Any?,
            rawStream: Any?,
            rawSeq: Any?,
            rawReceivedAt: Any?,
            rawDeviceTs: Any?,
            rawDetail: Any?
        ): CloudEventRecord? {
            if (id.isNullOrBlank() || RtdbPaths.containsForbiddenRtdbChars(id)) return null
            val type = safeString(rawType)?.takeIf { it.isNotBlank() } ?: return null
            val severity = safeString(rawSeverity)?.takeIf { it.isNotBlank() } ?: return null
            val stream = safeString(rawStream)?.takeIf { RtdbPaths.isValidCloudStream(it) } ?: return null
            val seq = safeLong(rawSeq)?.takeIf { RtdbPaths.isValidEventSeq(it) } ?: return null
            val receivedAt = safeLong(rawReceivedAt)?.takeIf { it > 0L } ?: return null
            val deviceTs = safeLong(rawDeviceTs)?.takeIf { it > 0L }
            val detail = safeString(rawDetail)
            return CloudEventRecord(
                id = id,
                type = type,
                severity = severity,
                stream = stream,
                seq = seq,
                receivedAt = receivedAt,
                deviceTs = deviceTs,
                detail = detail
            )
        }

        internal fun sortRecentEventsDescending(
            events: List<CloudEventRecord>,
            limit: Int = MAX_OBSERVE_EVENTS_LIMIT
        ): List<CloudEventRecord> = events
            .sortedWith(
                compareByDescending<CloudEventRecord> { it.receivedAt }
                    .thenByDescending { it.seq }
                    .thenByDescending { it.id }
            )
            .take(limit.coerceIn(1, MAX_OBSERVE_EVENTS_LIMIT))
    }
}

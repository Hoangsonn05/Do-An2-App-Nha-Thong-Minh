package com.example.smarthomeai.cloud

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
            val evaluated = evaluateDevicesSnapshot(devices)
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
            rawUpdatedAt = snapshot.child("updatedAt").value
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
            rawUpdatedAt: Any?
        ): CloudConfigState? {
            val rev = safeLong(rawRev) ?: return null
            val tempLimit = safeInt(rawTempLimit) ?: return null
            val humLimit = safeInt(rawHumLimit) ?: return null
            val coldLimit = safeInt(rawColdLimit) ?: return null
            val autoAlarm = safeBoolean(rawAutoAlarm) ?: return null
            val updatedBy = safeString(rawUpdatedBy)
            val updatedAt = safeLong(rawUpdatedAt)
            return CloudConfigState(
                rev = rev,
                tempLimit = tempLimit,
                humLimit = humLimit,
                coldLimit = coldLimit,
                autoAlarm = autoAlarm,
                updatedBy = updatedBy,
                updatedAt = updatedAt
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
    }
}

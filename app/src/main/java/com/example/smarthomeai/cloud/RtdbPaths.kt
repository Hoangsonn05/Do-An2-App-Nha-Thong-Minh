package com.example.smarthomeai.cloud

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure Kotlin RTDB path builder & segment validator aligned with P1.1-B0 §6.2, §7, F10, and F2a.
 */
object RtdbPaths {
    private val ENTITY_ID_REGEX = Regex("^[A-Za-z0-9_-]{8,64}$")
    private val TELEMETRY_RECORD_ID_REGEX = Regex("^[0-9]{12}_g$")
    private val CLOUD_STREAM_REGEX = Regex("^[A-Za-z0-9]{1,16}$")
    private val GATEWAY_EVENT_ID_REGEX = Regex("^g_[A-Za-z0-9]{1,16}_[0-9]{1,10}$")
    private val FORBIDDEN_RTDB_CHARS = charArrayOf('/', '.', '#', '$', '[', ']')
    private const val MAX_UID_LENGTH = 128
    const val MAX_EVENT_SEQ: Long = 9_999_999_999L

    fun isValidEntityId(id: String): Boolean =
        ENTITY_ID_REGEX.matches(id) && !containsForbiddenRtdbChars(id)

    fun isValidUid(uid: String): Boolean {
        if (uid.isBlank() || uid.length > MAX_UID_LENGTH) return false
        if (uid != uid.trim()) return false
        if (containsForbiddenRtdbChars(uid)) return false
        return uid.none { it.isISOControl() }
    }

    fun containsForbiddenRtdbChars(segment: String): Boolean =
        segment.indexOfAny(FORBIDDEN_RTDB_CHARS) >= 0

    fun requireValidEntityId(id: String, label: String = "id"): String {
        require(isValidEntityId(id)) {
            "Invalid RTDB $label: must match ^[A-Za-z0-9_-]{8,64}$ and contain no forbidden characters."
        }
        return id
    }

    fun requireValidUid(uid: String): String {
        require(isValidUid(uid)) {
            "Invalid RTDB uid: must be non-blank, <= $MAX_UID_LENGTH chars, and contain no forbidden RTDB characters (/ . # $ [ ])."
        }
        return uid
    }

    fun userHomesPath(uid: String): String {
        val validUid = requireValidUid(uid)
        return "userHomes/$validUid"
    }

    fun userHomePath(uid: String, homeId: String): String {
        val validUid = requireValidUid(uid)
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "userHomes/$validUid/$validHomeId"
    }

    fun homeMetaPath(homeId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "homes/$validHomeId/meta"
    }

    fun homeMembersPath(homeId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "homes/$validHomeId/members"
    }

    fun homeMemberPath(homeId: String, uid: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validUid = requireValidUid(uid)
        return "homes/$validHomeId/members/$validUid"
    }

    fun isValidTelemetryRecordId(recordId: String): Boolean =
        TELEMETRY_RECORD_ID_REGEX.matches(recordId) && !containsForbiddenRtdbChars(recordId)

    fun requireValidTelemetryRecordId(recordId: String): String {
        require(isValidTelemetryRecordId(recordId)) {
            "Invalid RTDB telemetry recordId: must match ^[0-9]{12}_g$."
        }
        return recordId
    }

    fun telemetryRecordId(epochMs: Long): String {
        require(epochMs > 0L) { "Telemetry epochMs must be positive." }
        val formatter = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val recordId = "${formatter.format(Date(epochMs))}_g"
        return requireValidTelemetryRecordId(recordId)
    }

    fun isValidCloudStream(cloudStream: String): Boolean =
        CLOUD_STREAM_REGEX.matches(cloudStream) && !containsForbiddenRtdbChars(cloudStream)

    fun requireValidCloudStream(cloudStream: String): String {
        require(isValidCloudStream(cloudStream)) {
            "Invalid RTDB cloudStream: must match ^[A-Za-z0-9]{1,16}$."
        }
        return cloudStream
    }

    fun isValidEventSeq(seq: Long): Boolean = seq in 0L..MAX_EVENT_SEQ

    fun requireValidEventSeq(seq: Long): Long {
        require(isValidEventSeq(seq)) {
            "Invalid RTDB event seq: must be within 0..$MAX_EVENT_SEQ."
        }
        return seq
    }

    fun isValidGatewayEventId(eventId: String): Boolean {
        if (!GATEWAY_EVENT_ID_REGEX.matches(eventId) || containsForbiddenRtdbChars(eventId)) {
            return false
        }
        val seqPart = eventId.substringAfterLast('_')
        val seq = seqPart.toLongOrNull() ?: return false
        return isValidEventSeq(seq) && seq.toString() == seqPart
    }

    fun requireValidGatewayEventId(eventId: String): String {
        require(isValidGatewayEventId(eventId)) {
            "Invalid RTDB gateway eventId: must match ^g_[A-Za-z0-9]{1,16}_[0-9]{1,10}$ with canonical seq <= $MAX_EVENT_SEQ."
        }
        return eventId
    }

    fun eventId(cloudStream: String, seq: Long): String {
        val validStream = requireValidCloudStream(cloudStream)
        val validSeq = requireValidEventSeq(seq)
        return "g_${validStream}_$validSeq"
    }

    fun homeDevicesPath(homeId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        return "homes/$validHomeId/devices"
    }

    fun homeDevicePath(homeId: String, deviceId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        return "homes/$validHomeId/devices/$validDeviceId"
    }

    fun deviceGatewayStatePath(homeId: String, deviceId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        return "homes/$validHomeId/deviceData/$validDeviceId/state/gateway"
    }

    fun deviceConfigPath(homeId: String, deviceId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        return "homes/$validHomeId/deviceData/$validDeviceId/config"
    }

    fun deviceTelemetryPath(homeId: String, deviceId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        return "homes/$validHomeId/deviceData/$validDeviceId/telemetry"
    }

    fun deviceTelemetryRecordPath(homeId: String, deviceId: String, recordId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        val validRecordId = requireValidTelemetryRecordId(recordId)
        return "homes/$validHomeId/deviceData/$validDeviceId/telemetry/$validRecordId"
    }

    fun deviceEventsPath(homeId: String, deviceId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        return "homes/$validHomeId/deviceData/$validDeviceId/events"
    }

    fun deviceEventPath(homeId: String, deviceId: String, eventId: String): String {
        val validHomeId = requireValidEntityId(homeId, "homeId")
        val validDeviceId = requireValidEntityId(deviceId, "deviceId")
        val validEventId = requireValidGatewayEventId(eventId)
        return "homes/$validHomeId/deviceData/$validDeviceId/events/$validEventId"
    }
}

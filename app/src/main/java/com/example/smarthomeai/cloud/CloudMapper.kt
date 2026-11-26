package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.SensorSample
import java.security.MessageDigest

/**
 * Pure Kotlin RTDB payload mapper & validator for Gateway deviceData (F2a / CD-2).
 * Accepts timestampValue as Any so production passes ServerValue.TIMESTAMP while JVM tests pass a sentinel.
 * Must not import any Firebase SDK types.
 */
object CloudMapper {
    const val DEFAULT_DEVICE_NAME: String = "ESP32 Smart Home"
    const val DEFAULT_DEVICE_MODEL: String = "esp32-smarthome"
    const val SOURCE_GATEWAY: String = "gateway"

    const val MAX_DEVICE_NAME_LENGTH: Int = 60
    const val MAX_DEVICE_MODEL_LENGTH: Int = 40
    const val MAX_MODE_LENGTH: Int = 16
    const val MAX_EVENT_TYPE_LENGTH: Int = 32
    const val MAX_EVENT_DETAIL_LENGTH: Int = 120
    const val CLOUD_STREAM_LENGTH: Int = 16

    const val MIN_TEMP_EXCLUSIVE: Int = -50
    const val MAX_TEMP_EXCLUSIVE: Int = 150
    const val MIN_HUM_INCLUSIVE: Int = 0
    const val MAX_HUM_INCLUSIVE: Int = 100
    const val MIN_SMOKE_PPM_INCLUSIVE: Int = 0
    const val MAX_SMOKE_PPM_INCLUSIVE: Int = 10_000

    const val MIN_CONFIG_TEMP_LIMIT: Int = 0
    const val MAX_CONFIG_TEMP_LIMIT: Int = 80
    const val MIN_CONFIG_HUM_LIMIT: Int = 0
    const val MAX_CONFIG_HUM_LIMIT: Int = 100
    const val MIN_CONFIG_COLD_LIMIT: Int = -10
    const val MAX_CONFIG_COLD_LIMIT: Int = 40
    const val MIN_CONFIG_SMOKE_LIMIT: Int = 50
    const val MAX_CONFIG_SMOKE_LIMIT: Int = 5_000

    val SERVER_TIMESTAMP_PLACEHOLDER: Map<String, String> = mapOf(".sv" to "timestamp")

    private const val FORBIDDEN_SECRET_TOKEN: String = "deviceKey"
    private const val HEX_DIGITS: String = "0123456789abcdef"

    private val CRITICAL_EVENT_KINDS = setOf(
        "alarm_started",
        "motion_alarm",
        "proximity_alarm",
        "smoke_detected",
        "smoke_auto_vent"
    )

    private val WARN_EVENT_KINDS = setOf(
        "rain_started",
        "servo_overdue",
        "servo_blocked",
        "exit_waiting",
        "motion_entry",
        "motion_waiting",
        "alarm_silent",
        "clothes_rain_retract",
        "proximity_detected",
        "motion_detected"
    )

    fun validateNoSecretToken(value: String, fieldName: String) {
        require(!value.contains(FORBIDDEN_SECRET_TOKEN, ignoreCase = true)) {
            "$fieldName contains a forbidden secret token."
        }
    }

    /**
     * Deterministically maps firmware streamId (which contains '-' and may exceed 16 chars)
     * to a 16-char lowercase hex SHA-256 prefix matching Rules regex ^[A-Za-z0-9]{1,16}$.
     */
    fun toCloudStream(rawStream: String): String {
        val trimmed = rawStream.trim()
        require(trimmed.isNotEmpty()) { "rawStream must not be blank." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(trimmed.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val unsigned = byte.toInt() and 0xFF
            sb.append(HEX_DIGITS[unsigned ushr 4])
            sb.append(HEX_DIGITS[unsigned and 0x0F])
        }
        return sb.substring(0, CLOUD_STREAM_LENGTH)
    }

    fun classifySeverity(kind: String): EventSeverity = when (kind.trim()) {
        in CRITICAL_EVENT_KINDS -> EventSeverity.CRITICAL
        in WARN_EVENT_KINDS -> EventSeverity.WARN
        else -> EventSeverity.INFO
    }

    fun buildDeviceRegistrationPayload(
        createdByUid: String,
        timestampValue: Any,
        name: String = DEFAULT_DEVICE_NAME,
        model: String = DEFAULT_DEVICE_MODEL,
        status: DeviceStatus = DeviceStatus.ACTIVE
    ): Map<String, Any> {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= MAX_DEVICE_NAME_LENGTH) {
            "Device name must be 1..$MAX_DEVICE_NAME_LENGTH characters."
        }
        validateNoSecretToken(cleanName, "Device name")

        val cleanModel = model.trim()
        require(cleanModel.isNotEmpty() && cleanModel.length <= MAX_DEVICE_MODEL_LENGTH) {
            "Device model must be 1..$MAX_DEVICE_MODEL_LENGTH characters."
        }
        validateNoSecretToken(cleanModel, "Device model")

        val validUid = RtdbPaths.requireValidUid(createdByUid)
        return linkedMapOf(
            "name" to cleanName,
            "model" to cleanModel,
            "status" to status.wireValue,
            "createdAt" to timestampValue,
            "createdBy" to validUid
        )
    }

    /**
     * Builds state/gateway payload restricted strictly to the 8 whitelisted keys under Rules (CD-2).
     * Never includes clothesline, rain, obstacle, distance, IP, or deviceKey fields.
     */
    fun buildGatewayStatePayload(
        snapshot: GatewaySnapshot,
        reportedByUid: String,
        timestampValue: Any
    ): Map<String, Any> {
        val validUid = RtdbPaths.requireValidUid(reportedByUid)
        val cleanMode = snapshot.mode.trim()
        require(cleanMode.isNotEmpty() && cleanMode.length <= MAX_MODE_LENGTH) {
            "Gateway mode must be 1..$MAX_MODE_LENGTH characters."
        }
        validateNoSecretToken(cleanMode, "Gateway mode")

        val values = LinkedHashMap<String, Any>(12).apply {
            put("light", snapshot.light)
            put("door", snapshot.door)
            put("alarm", snapshot.alarm)
            put("motion", snapshot.motion)
            put("monitoring", snapshot.monitoring)
            put("mode", cleanMode)
            snapshot.temp
                ?.takeIf { it > MIN_TEMP_EXCLUSIVE && it < MAX_TEMP_EXCLUSIVE }
                ?.let { put("temp", it) }
            snapshot.hum
                ?.takeIf { it in MIN_HUM_INCLUSIVE..MAX_HUM_INCLUSIVE }
                ?.let { put("hum", it) }
            snapshot.smokePpm
                ?.takeIf { it in MIN_SMOKE_PPM_INCLUSIVE..MAX_SMOKE_PPM_INCLUSIVE }
                ?.let { put("smokePpm", it) }
            snapshot.smokeAlert?.let { put("smokeAlert", it) }
            snapshot.window?.let { put("window", it) }
            snapshot.fan?.let { put("fan", it) }
        }

        return LinkedHashMap<String, Any>(5).apply {
            put("online", snapshot.online)
            put("reportedBy", validUid)
            put("receivedAt", timestampValue)
            put("values", values)
            if (snapshot.lastSeen > 0L) {
                put("deviceTs", snapshot.lastSeen)
            }
        }
    }

    fun buildConfigPayload(
        previousRev: Long?,
        tempLimit: Int,
        humLimit: Int,
        coldLimit: Int,
        autoAlarm: Boolean,
        updatedByUid: String,
        timestampValue: Any
    ): Map<String, Any> = buildBaseConfigPayload(
        previousRev = previousRev,
        tempLimit = tempLimit,
        humLimit = humLimit,
        coldLimit = coldLimit,
        autoAlarm = autoAlarm,
        updatedByUid = updatedByUid,
        timestampValue = timestampValue
    )

    fun buildConfigPayload(
        previousRev: Long?,
        tempLimit: Int,
        humLimit: Int,
        coldLimit: Int,
        autoAlarm: Boolean,
        updatedByUid: String,
        timestampValue: Any,
        smokeLimit: Int = 300,
        smokeAuto: Boolean = true
    ): Map<String, Any> {
        require(smokeLimit in MIN_CONFIG_SMOKE_LIMIT..MAX_CONFIG_SMOKE_LIMIT) {
            "smokeLimit must be within $MIN_CONFIG_SMOKE_LIMIT..$MAX_CONFIG_SMOKE_LIMIT."
        }
        return buildBaseConfigPayload(
            previousRev = previousRev,
            tempLimit = tempLimit,
            humLimit = humLimit,
            coldLimit = coldLimit,
            autoAlarm = autoAlarm,
            updatedByUid = updatedByUid,
            timestampValue = timestampValue
        ).apply {
            put("smokeLimit", smokeLimit)
            put("smokeAuto", smokeAuto)
        }
    }

    private fun buildBaseConfigPayload(
        previousRev: Long?,
        tempLimit: Int,
        humLimit: Int,
        coldLimit: Int,
        autoAlarm: Boolean,
        updatedByUid: String,
        timestampValue: Any
    ): LinkedHashMap<String, Any> {
        val validUid = RtdbPaths.requireValidUid(updatedByUid)
        require(tempLimit in MIN_CONFIG_TEMP_LIMIT..MAX_CONFIG_TEMP_LIMIT) {
            "tempLimit must be within $MIN_CONFIG_TEMP_LIMIT..$MAX_CONFIG_TEMP_LIMIT."
        }
        require(humLimit in MIN_CONFIG_HUM_LIMIT..MAX_CONFIG_HUM_LIMIT) {
            "humLimit must be within $MIN_CONFIG_HUM_LIMIT..$MAX_CONFIG_HUM_LIMIT."
        }
        require(coldLimit in MIN_CONFIG_COLD_LIMIT..MAX_CONFIG_COLD_LIMIT) {
            "coldLimit must be within $MIN_CONFIG_COLD_LIMIT..$MAX_CONFIG_COLD_LIMIT."
        }
        val nextRev = if (previousRev == null || previousRev < 1L) 1L else previousRev + 1L
        return linkedMapOf(
            "rev" to nextRev,
            "updatedBy" to validUid,
            "updatedAt" to timestampValue,
            "tempLimit" to tempLimit,
            "humLimit" to humLimit,
            "coldLimit" to coldLimit,
            "autoAlarm" to autoAlarm
        )
    }

    fun buildTelemetryPayload(
        sample: SensorSample,
        reportedByUid: String,
        timestampValue: Any
    ): Pair<String, Map<String, Any>>? = buildTelemetryPayload(
        timestampMs = sample.timestamp,
        temp = sample.temperature,
        hum = sample.humidity,
        reportedByUid = reportedByUid,
        timestampValue = timestampValue,
        smokePpm = sample.smokePpm
    )

    fun buildTelemetryPayload(
        timestampMs: Long,
        temp: Int?,
        hum: Int?,
        reportedByUid: String,
        timestampValue: Any,
        smokePpm: Int? = null
    ): Pair<String, Map<String, Any>>? {
        require(timestampMs > 0L) { "Telemetry timestamp must be positive." }
        val validUid = RtdbPaths.requireValidUid(reportedByUid)

        val values = LinkedHashMap<String, Any>(3).apply {
            temp?.takeIf { it > MIN_TEMP_EXCLUSIVE && it < MAX_TEMP_EXCLUSIVE }?.let { put("temp", it) }
            hum?.takeIf { it in MIN_HUM_INCLUSIVE..MAX_HUM_INCLUSIVE }?.let { put("hum", it) }
            smokePpm?.takeIf { it in MIN_SMOKE_PPM_INCLUSIVE..MAX_SMOKE_PPM_INCLUSIVE }?.let { put("smokePpm", it) }
        }
        if (values.isEmpty()) return null

        val recordId = RtdbPaths.telemetryRecordId(timestampMs)
        val payload = linkedMapOf<String, Any>(
            "source" to SOURCE_GATEWAY,
            "reportedBy" to validUid,
            "receivedAt" to timestampValue,
            "deviceTs" to timestampMs,
            "values" to values
        )
        return recordId to payload
    }

    fun buildEventPayload(
        input: GatewayEventInput,
        reportedByUid: String,
        timestampValue: Any
    ): Pair<String, Map<String, Any>> {
        val validUid = RtdbPaths.requireValidUid(reportedByUid)
        val cloudStream = toCloudStream(input.stream)
        val validSeq = RtdbPaths.requireValidEventSeq(input.seq)
        val eventId = RtdbPaths.eventId(cloudStream, validSeq)

        val type = input.kind.trim().take(MAX_EVENT_TYPE_LENGTH)
        require(type.isNotEmpty()) { "Event type must not be blank." }
        validateNoSecretToken(type, "Event type")

        val severity = classifySeverity(type).wireValue
        val detail = "boot=${input.boot};incident=${input.incident}".take(MAX_EVENT_DETAIL_LENGTH)
        validateNoSecretToken(detail, "Event detail")

        val payload = LinkedHashMap<String, Any>(9).apply {
            put("type", type)
            put("severity", severity)
            put("stream", cloudStream)
            put("seq", validSeq)
            put("source", SOURCE_GATEWAY)
            put("reportedBy", validUid)
            put("receivedAt", timestampValue)
            put("detail", detail)
            if (input.timestamp > 0L) {
                put("deviceTs", input.timestamp)
            }
        }
        return eventId to payload
    }
}

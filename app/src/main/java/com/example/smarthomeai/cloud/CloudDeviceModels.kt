package com.example.smarthomeai.cloud

import com.example.smarthomeai.data.DeviceEvent
import com.example.smarthomeai.data.HomeMode

/**
 * Pure Kotlin models for Gateway device registration and RTDB deviceData operations (F2a).
 * Must not import any Firebase SDK types.
 */
enum class DeviceStatus(val wireValue: String) {
    ACTIVE("active"),
    REVOKED("revoked");

    companion object {
        fun fromWire(value: String?): DeviceStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

data class CloudDevice(
    val id: String,
    val name: String,
    val model: String,
    val status: DeviceStatus,
    val createdAt: Long? = null,
    val createdBy: String? = null
)

sealed class DeviceResolveResult {
    data class Active(
        val device: CloudDevice,
        val hasMultipleActive: Boolean = false
    ) : DeviceResolveResult()

    data class RevokedOnly(
        val revokedIds: List<String>
    ) : DeviceResolveResult()

    data class MultipleActiveWarning(
        val primaryDevice: CloudDevice,
        val allActiveDevices: List<CloudDevice>
    ) : DeviceResolveResult()
}

data class GatewaySnapshot(
    val online: Boolean,
    val lastSeen: Long = 0L,
    val light: Boolean = false,
    val door: Boolean = false,
    val alarm: Boolean = false,
    val motion: Boolean = false,
    val monitoring: Boolean = false,
    val mode: String = HomeMode.HOME.name,
    val temp: Int? = null,
    val hum: Int? = null,
    val smokePpm: Int? = null,
    val smokeAlert: Boolean? = null,
    val window: Boolean? = null,
    val fan: Boolean? = null
) {
    constructor(
        online: Boolean,
        lastSeen: Long = 0L,
        light: Boolean = false,
        door: Boolean = false,
        alarm: Boolean = false,
        motion: Boolean = false,
        monitoring: Boolean = false,
        mode: HomeMode,
        temp: Int? = null,
        hum: Int? = null,
        smokePpm: Int? = null,
        smokeAlert: Boolean? = null,
        window: Boolean? = null,
        fan: Boolean? = null
    ) : this(
        online = online,
        lastSeen = lastSeen,
        light = light,
        door = door,
        alarm = alarm,
        motion = motion,
        monitoring = monitoring,
        mode = mode.name,
        temp = temp,
        hum = hum,
        smokePpm = smokePpm,
        smokeAlert = smokeAlert,
        window = window,
        fan = fan
    )
}

data class GatewayEventInput(
    val stream: String,
    val seq: Long,
    val kind: String,
    val timestamp: Long = 0L,
    val boot: Long = 0L,
    val incident: Long = 0L
) {
    companion object {
        fun fromDeviceEvent(event: DeviceEvent): GatewayEventInput = GatewayEventInput(
            stream = event.stream,
            seq = event.seq,
            kind = event.kind,
            timestamp = event.timestamp,
            boot = event.boot,
            incident = event.incident
        )
    }
}

data class CloudConfigState(
    val rev: Long,
    val tempLimit: Int,
    val humLimit: Int,
    val coldLimit: Int,
    val autoAlarm: Boolean,
    val updatedBy: String? = null,
    val updatedAt: Long? = null,
    val smokeLimit: Int = 300,
    val smokeAuto: Boolean = true
)

enum class EventSeverity(val wireValue: String) {
    INFO("info"),
    WARN("warn"),
    CRITICAL("critical")
}

enum class CreateOnlyWriteOutcome {
    WRITTEN,
    ALREADY_EXISTS
}

enum class DataSource {
    LAN,
    CLOUD,
    LOCAL_CACHE,
    NONE
}

data class GatewayStateSnapshot(
    val online: Boolean,
    val reportedBy: String? = null,
    val receivedAt: Long,
    val deviceTs: Long? = null,
    val temp: Int? = null,
    val hum: Int? = null,
    val motion: Boolean? = null,
    val light: Boolean? = null,
    val door: Boolean? = null,
    val alarm: Boolean? = null,
    val monitoring: Boolean? = null,
    val mode: HomeMode? = null,
    val smokePpm: Int? = null,
    val smokeAlert: Boolean? = null,
    val window: Boolean? = null,
    val fan: Boolean? = null
)

data class CloudTelemetrySnapshot(
    val recordId: String,
    val temp: Int? = null,
    val hum: Int? = null,
    val receivedAt: Long,
    val deviceTs: Long? = null,
    val smokePpm: Int? = null
)

data class CloudEventRecord(
    val id: String,
    val type: String,
    val severity: String,
    val stream: String,
    val seq: Long,
    val receivedAt: Long,
    val deviceTs: Long? = null,
    val detail: String? = null
)

data class CloudDeviceSnapshot(
    val deviceId: String,
    val gatewayState: GatewayStateSnapshot? = null,
    val latestTelemetry: CloudTelemetrySnapshot? = null,
    val recentEvents: List<CloudEventRecord> = emptyList(),
    val loading: Boolean = false,
    val error: CloudError? = null,
    val errorMessage: String? = null
) {
    val bestReceivedAt: Long?
        get() = maxOf(
            gatewayState?.receivedAt ?: 0L,
            latestTelemetry?.receivedAt ?: 0L,
            recentEvents.maxOfOrNull { it.receivedAt } ?: 0L
        ).takeIf { it > 0L }

    val bestDeviceTs: Long?
        get() = gatewayState?.deviceTs?.takeIf { it > 0L }
            ?: latestTelemetry?.deviceTs?.takeIf { it > 0L }
}

data class ResolvedDataSource(
    val source: DataSource,
    val updatedAt: Long? = null,
    val deviceTs: Long? = null,
    val isStale: Boolean,
    val gatewayOnline: Boolean? = null
)

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
    val hum: Int? = null
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
        hum: Int? = null
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
        hum = hum
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
    val updatedAt: Long? = null
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

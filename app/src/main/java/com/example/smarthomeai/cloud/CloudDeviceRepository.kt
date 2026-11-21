package com.example.smarthomeai.cloud

/**
 * Pure Kotlin repository contract for Gateway device registration and RTDB deviceData sync (F2a).
 * Must not import any Firebase SDK types.
 */
interface CloudDeviceRepository {
    suspend fun resolveOrRegisterDevice(
        expectedUid: String,
        homeId: String
    ): CloudResult<DeviceResolveResult>

    suspend fun publishGatewayState(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        payload: Map<String, Any>
    ): CloudResult<Unit>

    suspend fun readConfig(
        expectedUid: String,
        homeId: String,
        deviceId: String
    ): CloudResult<CloudConfigState?>

    suspend fun publishConfig(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        payload: Map<String, Any>
    ): CloudResult<Unit>

    suspend fun publishTelemetry(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        recordId: String,
        payload: Map<String, Any>
    ): CloudResult<CreateOnlyWriteOutcome>

    suspend fun readRecentEventCursors(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        limit: Int = 100
    ): CloudResult<Map<String, Long>>

    suspend fun publishEvent(
        expectedUid: String,
        homeId: String,
        deviceId: String,
        eventId: String,
        payload: Map<String, Any>
    ): CloudResult<CreateOnlyWriteOutcome>
}

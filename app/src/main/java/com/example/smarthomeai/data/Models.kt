package com.example.smarthomeai.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DeviceType(val key: String, val label: String) {
    LIGHT("light", "Đèn phòng khách"), DOOR("door", "Cửa chính"), ALARM("alarm", "Còi cảnh báo")
}
enum class HomeMode(val label: String, val description: String) {
    HOME("Ở nhà", "Điều khiển bình thường. Cảm biến không tự kích hoạt còi."), AWAY("Ra ngoài", "Chờ 20 giây rồi tắt đèn, đóng cửa và bật giám sát."), SLEEP("Đi ngủ", "Tắt đèn, đóng cửa và bật giám sát ngay.")
}
enum class CommandPhase { SENDING, SUCCESS, ERROR }
data class DeviceCommand(val phase: CommandPhase, val message: String)
data class LogItem(val kind: String, val title: String, val time: String)
data class FaceRecord(val name: String, val time: String, val known: Boolean)
enum class AlertType { MOTION, STRANGER, DOOR, LOGIN, ENVIRONMENT, SMOKE }
data class AlertItem(
    val type: AlertType, val title: String, val time: String,
    val id: String = UUID.randomUUID().toString(), val timestamp: Long = System.currentTimeMillis(),
    val reviewed: Boolean = false, val imagePath: String? = null, val incidentKey: String? = null
)
data class SensorSample(val timestamp: Long, val temperature: Int, val humidity: Int, val smokePpm: Int? = null)
data class SmartHomeState(
    val temperature: Int? = null, val humidity: Int? = null, val motionDetected: Boolean = false,
    val light: Boolean = false, val doorOpen: Boolean = false, val alarm: Boolean = false,
    val logs: List<LogItem> = emptyList(), val faces: List<FaceRecord> = emptyList(),
    val liveSamples: List<SensorSample> = emptyList(),
    val alerts: List<AlertItem> = emptyList(), val samples: List<SensorSample> = emptyList(),
    val esp32Online: Boolean = false, val esp32Ip: String = "192.168.1.50", val lastSeen: Long = 0,
    val recognizedName: String = "Chưa nhận diện", val recognizedAccuracy: Int = 0,
    val registeredUsers: List<String> = emptyList(), val autoAlarm: Boolean = true,
    val notifyEnabled: Boolean = true, val userEmail: String? = null,
    val mode: HomeMode = HomeMode.HOME, val modeBusy: Boolean = false, val modeMessage: String? = null,
    val monitoring: Boolean = false, val nightLightEnabled: Boolean = false, val doorReminderEnabled: Boolean = false,
    val rulesSupported: Boolean = false, val ruleBusy: Boolean = false, val clockReady: Boolean = false,
    val commands: Map<DeviceType, DeviceCommand> = emptyMap(), val connectionMessage: String? = null,
    val environmentAlerts: Boolean = true, val temperatureLimit: Int = 35, val humidityLimit: Int = 80,
    val coldLimit: Int = 18,
    val deviceKey: String = "", val security: SecurityStatus? = null,
    val deviceEvents: List<DeviceEvent> = emptyList(), val eventStream: String = "", val eventCursor: Long = 0,
    val syncMessage: String? = null, val hardware: HardwareStatus? = null,
    val clothesBusy: Boolean = false, val clothesMessage: String? = null, val faceDoorMessage: String? = null,
    val alarmSettingsBusy: Boolean = false, val alarmSettingsMessage: String? = null,
    val smokeAlertsEnabled: Boolean = true,
    val smokeLimit: Int = DEFAULT_SMOKE_LIMIT_PPM,
    val smokeAutoVentilation: Boolean = true,
    val ventilationBusy: Boolean = false,
    val ventilationMessage: String? = null
) {
    fun deviceOn(type: DeviceType) = when(type) {
        DeviceType.LIGHT -> light
        DeviceType.DOOR -> doorOpen
        DeviceType.ALARM -> alarm
    }
}
fun nowString(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale("vi", "VN")).format(Date(timestamp))
fun timeOnly(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale("vi", "VN")).format(Date(timestamp))
data class SensorData(
    val temp: Int?, val hum: Int?, val motion: Boolean, val light: Boolean?, val door: Boolean?, val alarm: Boolean?,
    val nightLight: Boolean? = null, val doorReminder: Boolean? = null, val doorOverdue: Boolean = false, val clockReady: Boolean = false,
    val security: SecurityStatus? = null, val hardware: HardwareStatus? = null
)

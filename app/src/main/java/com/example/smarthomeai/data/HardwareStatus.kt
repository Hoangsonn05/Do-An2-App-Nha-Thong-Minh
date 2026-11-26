package com.example.smarthomeai.data

import kotlin.math.round
import org.json.JSONObject

/** Actual capabilities reported by the document-specific ESP32 firmware. */
data class HardwareStatus(
    val profile: String, val authRequired: Boolean = true, val hasBuzzer: Boolean = true,
    val obstacle: Boolean? = null, val raining: Boolean? = null, val distanceCm: Float? = null,
    val hasClothesline: Boolean = false, val clothesRetracted: Boolean? = null, val rainRetractEnabled: Boolean? = null,
    val securityProfile: String? = null, val alarmOutput: Boolean? = null, val alarmSource: String? = null,
    val proximityAlarmEnabled: Boolean? = null, val proximityCm: Float = 10f,
    val autoCloseSeconds: Int = 5, val autoCloseRemaining: Int? = null,
    val alarmSettings: AlarmSettings? = null,
    val hasMq2: Boolean = false,
    val smokePpm: Int? = null,
    val smokeAlert: Boolean? = null,
    val hasWindow: Boolean = false,
    val windowOpen: Boolean? = null,
    val hasFan: Boolean = false,
    val fanOn: Boolean? = null,
    val smokeAutoEnabled: Boolean? = null,
    val smokeThresholdPpm: Int = DEFAULT_SMOKE_LIMIT_PPM
) {
    // Rules in the user's supplied firmware. They are not configurable over HTTP.
    val usesDoorRules get() = profile == "dht11-leds"
    val instantModes get() = securityProfile=="instant-20s"
    val nearObject: Boolean? get() = if(usesDoorRules) distanceCm?.let { it in 2f..proximityCm } else null
    val doorBlocked get() = obstacle == true || (usesDoorRules && distanceCm?.let { it in 2f..DOOR_CLEARANCE_CM } == true) || ((instantModes || securityProfile == "demo-two-modes") && distanceCm == null)
    companion object {
        const val PROXIMITY_CM=10f
        const val DOOR_CLEARANCE_CM=10f
        const val AUTO_CLOSE_SECONDS=5
        private val MQ2_VENTILATION_KEYS = setOf(
            "has_mq2", "smoke_ppm", "smoke_alert",
            "has_window", "window_open",
            "has_fan", "fan_on",
            "smoke_auto_enabled", "smoke_threshold_ppm"
        )
        fun parse(json: JSONObject): HardwareStatus? {
            val rawProfile=json.optString("hardware_profile","")
            if(rawProfile.isBlank() && MQ2_VENTILATION_KEYS.none { json.has(it) }) return null
            val profile = rawProfile.ifBlank { "mq2-ventilation" }
            fun flag(key:String):Boolean? = if(json.has(key) && !json.isNull(key)) json.getInt(key)==1 else null
            val distance=json.optDouble("distance_cm",Double.NaN).toFloat().takeIf { it.isFinite() && it in 2f..400f }
            val smokePpm = if (json.has("smoke_ppm") && !json.isNull("smoke_ppm")) {
                val raw = json.opt("smoke_ppm")
                val value = when (raw) {
                    is Number -> raw.toDouble()
                    is String -> raw.toDoubleOrNull()
                    else -> null
                }
                value?.takeIf { it.isFinite() }?.let { round(it).toInt() }?.takeIf { it in 0..MAX_SMOKE_PPM }
            } else null
            val smokeAlert = flag("smoke_alert")
            val hasMq2 = flag("has_mq2") ?: (smokePpm != null || smokeAlert != null)
            val windowOpen = flag("window_open")
            val hasWindow = flag("has_window") ?: (windowOpen != null)
            val fanOn = flag("fan_on")
            val hasFan = flag("has_fan") ?: (fanOn != null)
            val smokeAutoEnabled = flag("smoke_auto_enabled")
            val smokeThresholdPpm = if (json.has("smoke_threshold_ppm") && !json.isNull("smoke_threshold_ppm")) {
                json.optInt("smoke_threshold_ppm", -1)
                    .takeIf { it in MIN_WIRE_SMOKE_LIMIT_PPM..MAX_WIRE_SMOKE_LIMIT_PPM }
                    ?: DEFAULT_SMOKE_LIMIT_PPM
            } else DEFAULT_SMOKE_LIMIT_PPM
            return HardwareStatus(profile,flag("auth_required") ?: true,flag("has_buzzer") ?: true,flag("obstacle"),flag("raining"),distance,
                flag("has_clothesline") ?: false,flag("clothes_retracted"),flag("rain_retract_enabled"),
                json.optString("security_profile","").takeIf {it.isNotBlank()},flag("alarm_active"),json.optString("alarm_source","").takeIf {it.isNotBlank()},
                flag("proximity_alarm_enabled"),json.optDouble("ultrasonic_alarm_cm",10.0).toFloat().takeIf {it.isFinite() && it in 2f..400f} ?: 10f,
                json.optInt("door_auto_close_seconds",5).coerceIn(1,120),if(json.isNull("door_auto_close_remaining")) null else json.optInt("door_auto_close_remaining").coerceIn(0,120),AlarmSettings.parse(json),
                hasMq2,smokePpm,smokeAlert,hasWindow,windowOpen,hasFan,fanOn,smokeAutoEnabled,smokeThresholdPpm)
        }
    }
}

fun SmartHomeState.deviceLabel(type: DeviceType): String =
    if(hardware?.profile=="dht11-leds") when(type) {
        DeviceType.LIGHT -> "LED xanh"; DeviceType.DOOR -> "Servo cửa"; DeviceType.ALARM -> if(hardware.hasBuzzer) "Còi & LED vàng" else "LED vàng báo động"
    } else type.label

val SmartHomeState.proximityAlert get() = hardware?.nearObject == true && hardware.proximityAlarmEnabled!=false
val SmartHomeState.alarmActive get() = hardware?.alarmOutput ?: (alarm || proximityAlert)
val SmartHomeState.effectiveSmokePpm: Int? get() = hardware?.smokePpm
val SmartHomeState.effectiveSmokeAuto: Boolean get() = hardware?.smokeAutoEnabled ?: smokeAutoVentilation
val SmartHomeState.smokeHazardActive: Boolean get() = esp32Online && (hardware?.smokeAlert == true || (hardware?.smokePpm != null && hardware.smokePpm >= smokeLimit))
val SmartHomeState.ventilationSafetyLocked: Boolean get() = smokeHazardActive && effectiveSmokeAuto

fun alarmCommandFeedback(manualOn: Boolean, nearObject: Boolean, durationSeconds: Int = 60) = when {
    !manualOn && nearObject -> "Đã dừng thử. Cảm biến vẫn phát hiện vật gần nên cảnh báo tiếp tục."
    manualOn -> "ESP32 đã bật thử báo động, tự dừng tối đa sau $durationSeconds giây."
    else -> "ESP32 đã dừng thử báo động."
}

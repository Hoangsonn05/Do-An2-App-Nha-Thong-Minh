package com.example.smarthomeai.data

import org.json.JSONObject

enum class SecurityPhase(val label: String) {
    DISARMED("Đang ở nhà"), EXIT_DELAY("Đang chờ bạn rời nhà"), ARMED("Đang bảo vệ"),
    ENTRY_DELAY("Có chuyển động · cần xác nhận"), ALARM("Có sự cố cần xử lý"), NIGHT("Đang ở chế độ Đi ngủ")
}

data class SecurityStatus(
    val mode: HomeMode, val phase: SecurityPhase, val remaining: Int, val stream: String,
    val boot: Long, val incident: Long, val exitSeconds: Int = 30, val entrySeconds: Int = 20,
    val holdSeconds: Int = 30, val warmup: Int = 0, val lightReason: String = "manual",
    val instantAlarm: Boolean = false, val exitBlocked: Boolean = false,
    val demoModes: Boolean = false
) {
    val protecting get() = if (demoModes) mode == HomeMode.AWAY && phase != SecurityPhase.EXIT_DELAY
        else (mode == HomeMode.AWAY && (!instantAlarm || phase!=SecurityPhase.EXIT_DELAY || exitBlocked)) || (mode == HomeMode.SLEEP && instantAlarm)
    val needsAttention get() = phase == SecurityPhase.ENTRY_DELAY || phase == SecurityPhase.ALARM
    val incidentKey get() = if(incident > 0) "$stream:$boot:$incident" else null
    companion object {
        fun parse(json: JSONObject): SecurityStatus? {
            if(json.optInt("firmware", 0) < 3) return null
            return runCatching {
                val mode = HomeMode.valueOf(json.getString("mode"))
                val phase = SecurityPhase.valueOf(json.getString("phase"))
                val profile = json.optString("security_profile")
                val demo = profile == "demo-two-modes"
                val instant = demo || profile == "instant-20s"
                require(when(mode) { HomeMode.HOME -> phase == SecurityPhase.DISARMED; HomeMode.AWAY -> phase in setOf(SecurityPhase.EXIT_DELAY, SecurityPhase.ARMED, SecurityPhase.ENTRY_DELAY, SecurityPhase.ALARM); HomeMode.SLEEP -> phase == SecurityPhase.NIGHT || (instant && phase in setOf(SecurityPhase.ARMED,SecurityPhase.ALARM)) })
                SecurityStatus(mode, phase, json.optInt("remaining").coerceIn(0,120), json.getString("stream_id").also { require(it.isNotBlank()) },
                    json.getLong("boot"), json.optLong("incident"), if (demo) 0 else json.optInt("exit_seconds",30).coerceIn(5,120),
                    if(instant) 0 else json.optInt("entry_seconds",20).coerceIn(5,120), json.optInt("hold_seconds",30).coerceIn(5,120),
                    json.optInt("warmup").coerceIn(0,60),json.optString("light_reason","manual"),instant,json.optInt("exit_blocked")==1,demo)
            }.getOrNull()
        }
    }
}

data class DeviceEvent(val id: String, val seq: Long, val stream: String, val boot: Long,
                       val incident: Long, val kind: String, val timestamp: Long, val uptime: Long) {
    val incidentKey get() = if(incident > 0) "$stream:$boot:$incident" else null
    val timeLabel get() = if(timestamp > 0) nowString(timestamp) else "Sau khởi động #$boot: ${uptime / 1000}s · chưa có giờ"
    val title get() = when(kind) {
        "restarted" -> "ESP32 khởi động lại"
        "exit_started" -> "Ra ngoài · bắt đầu thời gian rời nhà"
        "away_selected" -> "Chuyển sang Ra ngoài"
        "motion_home" -> "Phát hiện chuyển động trước cửa · Ở nhà"
        "motion_waiting" -> "Phát hiện chuyển động trước cửa · Chờ bật bảo vệ"
        "armed" -> "Đã bật bảo vệ trên ESP32"
        "motion_entry" -> "PIR phát hiện chuyển động · bắt đầu chờ xác nhận"
        "alarm_started" -> "Báo động đã bật"
        "exit_waiting" -> "Chưa bật bảo vệ · Kiểm tra vùng cửa"
        "motion_alarm" -> "Cảm biến chuyển động kích hoạt báo động"
        "proximity_alarm" -> "Vật gần kích hoạt báo động"
        "siren_stopped" -> "Báo động tự dừng khi hết thời gian"
        "motion_detected" -> "Phát hiện chuyển động · báo động tự động đang tắt"
        "proximity_detected" -> "Phát hiện vật gần · báo động tự động đang tắt"
        "alarm_silent" -> "Đã ghi nhận sự cố · không bật còi và LED vàng"
        "alarm_muted" -> "Đã dừng báo động tự động · giữ sự cố chờ xác nhận"
        "alarm_rule_changed" -> "Đã thay đổi thiết lập báo động"
        "acknowledged" -> "Chủ nhà đã xác nhận · kết thúc sự cố"
        "home_selected" -> "Chuyển sang Ở nhà"
        "sleep_selected" -> "Chuyển sang Đi ngủ"
        "light_motion_on" -> "Chuyển động · đèn tự bật"
        "light_auto_off" -> "Không còn chuyển động · đèn tự tắt"
        "light_manual_on" -> "Bật đèn bằng tay"
        "light_manual_off" -> "Tắt đèn bằng tay"
        "servo_open" -> "Đã gửi servo đến vị trí mở"
        "servo_close" -> "Cửa chuyển về vị trí đóng"
        "servo_overdue" -> "Servo giữ vị trí mở quá 2 phút"
        "alarm_manual_on" -> "Bật báo động bằng tay"
        "alarm_manual_off" -> "Tắt báo động bằng tay"
        "servo_blocked" -> "Từ chối đóng servo vì có vật cản"
        "rain_started" -> "Cảm biến phát hiện mưa"
        "rain_cleared" -> "Cảm biến không còn báo mưa"
        "clothes_retracted" -> "Đã đưa servo giàn phơi đến vị trí thu vào"
        "clothes_extended" -> "Đã đưa servo giàn phơi đến vị trí phơi ra"
        "clothes_rain_retract" -> "Có mưa · giàn phơi tự thu vào"
        "clothes_rule_changed" -> "Đã đổi thiết lập tự thu khi mưa"
        "config_changed" -> "Đã thay đổi thời gian tự động hóa"
        else -> "Sự kiện thiết bị: $kind"
    }
}

data class EventBatch(val stream: String, val oldest: Long, val next: Long, val events: List<DeviceEvent>) {
    companion object {
        fun parse(json: JSONObject): EventBatch {
            val stream = json.getString("stream_id")
            val next = json.getLong("next")
            val oldest = json.getLong("oldest")
            require(stream.isNotBlank() && oldest > 0 && next >= oldest)
            val array = json.getJSONArray("events")
            require(array.length() <= 64)
            val events = (0 until array.length()).map { index ->
                val e = array.getJSONObject(index)
                val seq = e.getLong("seq")
                require(seq in oldest until next)
                DeviceEvent("$stream:$seq",seq,stream,e.getLong("boot"),e.optLong("incident"),e.getString("kind"),e.optLong("time"),e.getLong("uptime"))
            }.sortedBy { it.seq }
            require(events.map { it.seq }.distinct().size == events.size)
            return EventBatch(stream,oldest,next,events)
        }
    }
}

fun mergeDeviceEvents(existing: List<DeviceEvent>, incoming: List<DeviceEvent>): List<DeviceEvent> =
    (incoming.asReversed() + existing).distinctBy { it.id }.take(200)

fun mergeIncidentAlerts(existing: List<AlertItem>, events: List<DeviceEvent>, allEvents: List<DeviceEvent>): List<AlertItem> {
    var result = existing
    events.mapNotNull { it.incidentKey }.distinct().forEach { key ->
        val timeline = allEvents.filter { it.incidentKey == key }.sortedBy { it.seq }
        val first = timeline.firstOrNull() ?: return@forEach
        val old = result.firstOrNull { it.incidentKey == key }
        val alarm = timeline.any { it.kind in setOf("alarm_started", "motion_alarm") }
        val resolved = timeline.any { it.kind == "acknowledged" }
        val proximity=timeline.any {it.kind in setOf("proximity_alarm","proximity_detected")}
        val subject=if(proximity) "Vật gần khi giám sát" else "Chuyển động khi giám sát"
        val title = if(resolved) "Sự cố giám sát · đã kết thúc" else if(alarm) "$subject · báo động đã bật" else if(timeline.any { it.kind=="alarm_silent" }) "$subject · báo động tự động đang tắt" else "$subject · chờ xác nhận"
        val escalated = events.any { it.incidentKey == key && it.kind == "alarm_started" }
        val alert = old?.copy(title=title,reviewed=if(escalated) false else old.reviewed)
            ?: AlertItem(AlertType.MOTION,title,first.timeLabel,id=key,timestamp=first.timestamp,incidentKey=key)
        result = listOf(alert) + result.filter { it.id != alert.id }
    }
    return result.take(100)
}

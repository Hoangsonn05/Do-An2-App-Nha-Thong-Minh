package com.example.smarthomeai.data

import org.json.JSONObject

data class AlarmSettings(
    val awayEnabled: Boolean = true,
    val sleepEnabled: Boolean = true,
    val durationSeconds: Int = 60
) {
    fun enabledFor(mode: HomeMode) = when(mode) {
        HomeMode.AWAY -> awayEnabled
        HomeMode.SLEEP -> sleepEnabled
        HomeMode.HOME -> false
    }
    companion object {
        fun parse(json: JSONObject): AlarmSettings? = runCatching {
            fun flag(key: String): Boolean {
                val value = json.getInt(key)
                require(value == 0 || value == 1)
                return value == 1
            }
            val away = flag("auto_alarm_away")
            val sleep = flag("auto_alarm_sleep")
            val seconds = json.getInt("alarm_duration_seconds")
            require(seconds in 5..60)
            AlarmSettings(away, sleep, seconds)
        }.getOrNull()
    }
}

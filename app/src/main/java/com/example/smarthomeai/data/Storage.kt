package com.example.smarthomeai.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("smart_home", Context.MODE_PRIVATE)
    private val db = object : SQLiteOpenHelper(context, "smart_home_history.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE samples (time INTEGER PRIMARY KEY, temp INTEGER NOT NULL, hum INTEGER NOT NULL, smoke_ppm INTEGER)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE samples ADD COLUMN smoke_ppm INTEGER")
            }
        }
    }
    private fun <T> list(key: String, parse: (JSONObject) -> T): List<T> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).mapNotNull { runCatching { parse(a.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())
    fun load() = SmartHomeState(
        light = prefs.getBoolean("light", false), doorOpen = prefs.getBoolean("door", false),
        alarm = prefs.getBoolean("alarm", false), esp32Ip = prefs.getString("ip", "192.168.1.50")!!,
        deviceKey = prefs.getString("device_key", "")!!,
        hardware = runCatching { HardwareStatus.parse(JSONObject(prefs.getString("hardware_json", "{}")!!)) }.getOrNull(),
        eventStream = prefs.getString("event_stream", "")!!, eventCursor = prefs.getLong("event_cursor", 0),
        deviceEvents = list("device_events") {
            DeviceEvent(it.getString("id"), it.getLong("seq"), it.getString("stream"), it.getLong("boot"),
                it.getLong("incident"), it.getString("kind"), it.getLong("timestamp"), it.getLong("uptime"))
        },
        autoAlarm = prefs.getBoolean("auto_alarm", true), notifyEnabled = prefs.getBoolean("notify", true),
        environmentAlerts = prefs.getBoolean("environment_alerts", true),
        coldLimit = prefs.getInt("cold_limit", 18).coerceIn(0, 19),
        temperatureLimit = prefs.getInt("temperature_limit", 35).coerceIn(20, 50),
        humidityLimit = prefs.getInt("humidity_limit", 80).coerceIn(40, 95),
        smokeAlertsEnabled = prefs.getBoolean("smoke_alerts", true),
        smokeLimit = prefs.getInt("smoke_limit", DEFAULT_SMOKE_LIMIT_PPM).coerceIn(MIN_SMOKE_LIMIT_PPM, MAX_SMOKE_LIMIT_PPM),
        smokeAutoVentilation = prefs.getBoolean("smoke_auto_vent", true),
        mode = runCatching { HomeMode.valueOf(prefs.getString("mode", "HOME")!!) }.getOrDefault(HomeMode.HOME),
        monitoring = prefs.getBoolean("monitoring", false),
        lastSeen = prefs.getLong("last_seen", 0),
        temperature = prefs.getInt("temperature", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
        humidity = prefs.getInt("humidity", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
        logs = list("logs") { LogItem(it.getString("k"), it.getString("t"), it.getString("tm")) },
        faces = list("faces") { FaceRecord(it.getString("n"), it.getString("tm"), it.getBoolean("known")) },
        alerts = list("alerts") {
            AlertItem(AlertType.valueOf(it.getString("k")), it.getString("t"), it.getString("tm"),
                id = it.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                timestamp = it.optLong("ts", 0), reviewed = it.optBoolean("reviewed"),
                imagePath = it.optString("image").takeIf(String::isNotBlank), incidentKey = it.optString("incident").takeIf(String::isNotBlank))
        }
    )
    fun save(s: SmartHomeState) {
        fun <T> json(items: List<T>, make: (T) -> JSONObject) = JSONArray().also { a -> items.forEach { a.put(make(it)) } }.toString()
        prefs.edit()
            .putBoolean("light", s.light).putBoolean("door", s.doorOpen).putBoolean("alarm", s.alarm)
            .putString("ip", s.esp32Ip).putBoolean("auto_alarm", s.autoAlarm).putBoolean("notify", s.notifyEnabled)
            .putString("device_key", s.deviceKey).putString("event_stream", s.eventStream).putLong("event_cursor", s.eventCursor)
            .putString("hardware_json", s.hardware?.let { h -> JSONObject().put("hardware_profile",h.profile)
                .put("auth_required",if(h.authRequired) 1 else 0).put("has_buzzer",if(h.hasBuzzer) 1 else 0)
                .put("obstacle",h.obstacle?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("raining",h.raining?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("has_clothesline",if(h.hasClothesline) 1 else 0)
                .put("clothes_retracted",h.clothesRetracted?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("rain_retract_enabled",h.rainRetractEnabled?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("security_profile",h.securityProfile ?: "").put("alarm_source",h.alarmSource ?: "")
                .put("alarm_active",h.alarmOutput?.let {if(it) 1 else 0} ?: JSONObject.NULL)
                .put("proximity_alarm_enabled",h.proximityAlarmEnabled?.let {if(it) 1 else 0} ?: JSONObject.NULL)
                .put("ultrasonic_alarm_cm",h.proximityCm).put("door_auto_close_seconds",h.autoCloseSeconds)
                .put("door_auto_close_remaining",h.autoCloseRemaining ?: JSONObject.NULL)
                .put("distance_cm",h.distanceCm ?: JSONObject.NULL)
                .put("has_mq2",if(h.hasMq2) 1 else 0)
                .put("smoke_ppm",h.smokePpm ?: JSONObject.NULL)
                .put("smoke_alert",h.smokeAlert?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("has_window",if(h.hasWindow) 1 else 0)
                .put("window_open",h.windowOpen?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("has_fan",if(h.hasFan) 1 else 0)
                .put("fan_on",h.fanOn?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("smoke_auto_enabled",h.smokeAutoEnabled?.let { if(it) 1 else 0 } ?: JSONObject.NULL)
                .put("smoke_threshold_ppm",h.smokeThresholdPpm).toString() } ?: "{}")
            .putString("device_events", json(s.deviceEvents) { JSONObject().put("id",it.id).put("seq",it.seq).put("stream",it.stream)
                .put("boot",it.boot).put("incident",it.incident).put("kind",it.kind).put("timestamp",it.timestamp).put("uptime",it.uptime) })
            .putBoolean("environment_alerts", s.environmentAlerts)
            .putInt("cold_limit", s.coldLimit).putInt("temperature_limit", s.temperatureLimit).putInt("humidity_limit", s.humidityLimit)
            .putBoolean("smoke_alerts", s.smokeAlertsEnabled)
            .putInt("smoke_limit", s.smokeLimit.coerceIn(MIN_SMOKE_LIMIT_PPM, MAX_SMOKE_LIMIT_PPM))
            .putBoolean("smoke_auto_vent", s.smokeAutoVentilation)
            .putString("mode", s.mode.name).putBoolean("monitoring", s.monitoring)
            .putLong("last_seen", s.lastSeen).putInt("temperature", s.temperature ?: Int.MIN_VALUE)
            .putInt("humidity", s.humidity ?: Int.MIN_VALUE)
            .putString("logs", json(s.logs) { JSONObject().put("k", it.kind).put("t", it.title).put("tm", it.time) })
            .putString("faces", json(s.faces) { JSONObject().put("n", it.name).put("tm", it.time).put("known", it.known) })
            .putString("alerts", json(s.alerts) { JSONObject().put("k", it.type.name).put("t", it.title).put("tm", it.time)
                .put("id", it.id).put("ts", it.timestamp).put("reviewed", it.reviewed).put("image", it.imagePath ?: "").put("incident",it.incidentKey ?: "") }).apply()
    }
    fun recordSample(sample: SensorSample) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO samples (time, temp, hum, smoke_ppm) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(sample.timestamp, sample.temperature, sample.humidity, sample.smokePpm)
        )
        db.writableDatabase.execSQL("DELETE FROM samples WHERE time < ?", arrayOf(System.currentTimeMillis() - 7 * 86_400_000L))
    }
    fun samples(): List<SensorSample> {
        val result = mutableListOf<SensorSample>()
        db.readableDatabase.rawQuery("SELECT time, temp, hum, smoke_ppm FROM samples WHERE time >= ? ORDER BY time",
            arrayOf((System.currentTimeMillis() - HISTORY_WINDOW).toString())).use { c ->
            while(c.moveToNext()) {
                result += SensorSample(c.getLong(0), c.getInt(1), c.getInt(2), if (c.isNull(3)) null else c.getInt(3))
            }
        }
        return result
    }
}

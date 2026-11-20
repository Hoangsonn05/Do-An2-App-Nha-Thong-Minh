package com.example.smarthomeai.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Esp32Client {
    @Volatile var lastError: String? = null
        private set
    fun validIp(ip: String): Boolean = ip.split('.').let { parts ->
        parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255 }
    }
    fun validKey(key: String) = key.matches(Regex("[A-Za-z0-9_-]{12,64}"))
    private fun request(ip: String, path: String, key: String = "", method: String = "GET"): String? {
        lastError = null
        if(!validIp(ip)) { lastError = "Địa chỉ IP không hợp lệ."; return null }
        if(key.isNotEmpty() && !validKey(key)) { lastError = "Khóa thiết bị cần 12–64 ký tự chữ, số, gạch ngang hoặc gạch dưới."; return null }
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("http://$ip/$path").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 2000; connection.readTimeout = 2000; connection.useCaches = false
            if(key.isNotEmpty()) connection.setRequestProperty("X-Device-Key",key)
            val code = connection.responseCode
            if(code != 200) {
                lastError = when(code) {
                    401,403 -> "Khóa ESP32 chưa đúng. Mở Cài đặt và nhập khóa trùng với firmware."
                    409 -> "ESP32 đang bảo vệ. Hãy xác nhận tắt bảo vệ trước khi điều khiển."
                    412 -> "Đang mưa và tự thu đang bật. Chưa thể đưa giàn phơi ra ngoài."
                    423 -> "Vùng cửa chưa sẵn sàng. Kiểm tra vật cản và cảm biến khoảng cách rồi thử lại."
                    503 -> "ESP32 chưa sẵn sàng. Kiểm tra DEVICE_KEY trong firmware."
                    else -> "ESP32 trả lỗi $code. Kiểm tra firmware và thử lại."
                }; null
            } else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) { lastError = "Không liên lạc được ESP32. Kiểm tra nguồn, Wi-Fi và IP."; null }
        finally { connection?.disconnect() }
    }
    fun sendCommand(ip: String, device: String, on: Boolean, key: String = "") =
        request(ip,"relay?dev=$device&state=${if(on) 1 else 0}",key)?.trim() == "OK"
    fun sendRules(ip: String, night: Boolean, door: Boolean, key: String = "") =
        request(ip,"rules?night=${if(night) 1 else 0}&door=${if(door) 1 else 0}",key)?.trim() == "OK"
    fun sendClothesline(ip: String, retracted: Boolean, key: String = "") =
        request(ip,"clothesline?state=${if(retracted) 1 else 0}",key,"POST")?.trim() == "OK"
    fun sendRainRetraction(ip: String, enabled: Boolean, key: String = "") =
        request(ip,"clothesline/auto?enabled=${if(enabled) 1 else 0}",key,"POST")?.trim() == "OK"
    fun sendMode(ip: String, mode: HomeMode, key: String) = request(ip,"mode?value=${mode.name}",key,"POST")?.trim() == "OK"
    fun sendAlarmSettings(ip: String, settings: AlarmSettings, key: String = ""): Boolean {
        if(settings.durationSeconds !in 5..60) return false
        return request(ip,"alarm/settings?away=${if(settings.awayEnabled) 1 else 0}&sleep=${if(settings.sleepEnabled) 1 else 0}&seconds=${settings.durationSeconds}",key,"POST")?.trim() == "OK"
    }
    fun sendTiming(ip: String, exit: Int, entry: Int, hold: Int, key: String): Boolean {
        if(listOf(exit,entry,hold).any { it !in 5..120 }) return false
        return request(ip,"config?exit=$exit&entry=$entry&hold=$hold",key,"POST")?.trim() == "OK"
    }
    fun syncClock(ip: String, key: String) = request(ip,"clock?epoch=${System.currentTimeMillis()/1000}",key,"POST")?.trim() == "OK"
    fun readEvents(ip: String, after: Long, key: String): EventBatch? = try {
        request(ip,"events?after=${after.coerceAtLeast(0)}",key)?.let { EventBatch.parse(JSONObject(it)) }
    } catch (_: Exception) { lastError = "Không đọc được nhật ký ESP32. Hãy kiểm tra firmware."; null }
    fun readSensor(ip: String, key: String = ""): SensorData? = try {
        val json = request(ip,"sensor",key)?.let(::JSONObject)
        if(json == null || !json.has("motion")) null else {
            fun flag(name: String): Boolean? = if(json.has(name) && !json.isNull(name)) json.getInt(name) == 1 else null
            val security = SecurityStatus.parse(json)
            require(json.optInt("firmware",0) < 3 || security != null)
            SensorData(
                if(json.isNull("temp")) null else json.getDouble("temp").toInt().takeIf { it in -40..125 },
                if(json.isNull("hum")) null else json.getDouble("hum").toInt().takeIf { it in 0..100 },
                flag("motion") ?: false,flag("light"),flag("door"),flag("alarm"),flag("night_light"),flag("door_reminder"),
                flag("door_overdue") ?: false,flag("clock_ready") ?: false,security,HardwareStatus.parse(json))
        }
    } catch (_: Exception) { lastError = "Phản hồi cảm biến không hợp lệ. Kiểm tra firmware ESP32."; null }
}

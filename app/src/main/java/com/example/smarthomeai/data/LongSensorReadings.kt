package com.example.smarthomeai.data

import org.json.JSONObject

data class LongSensorReadings(
    val lightLux: Double? = null,
    val reedClosed: Boolean? = null,
    val rainRaw: Int? = null,
    val rfidReady: Boolean? = null,
    val lcdReady: Boolean? = null
) {
    fun gatewayValues(): Map<String, Any> = buildMap {
        lightLux?.let { put("lightLux", it) }
        reedClosed?.let { put("reedClosed", it) }
        rainRaw?.let { put("rainRaw", it) }
        rfidReady?.let { put("rfidReady", it) }
        lcdReady?.let { put("lcdReady", it) }
    }

    companion object {
        fun fromFirmware(json: JSONObject): LongSensorReadings? {
            fun flag(name: String): Boolean? = when (json.optInt(name, -1)) {
                0 -> false
                1 -> true
                else -> null
            }
            return validated(
                json.optDouble("light_lux"), flag("reed_closed"), json.optInt("rain_raw", -1),
                flag("rfid_ready"), flag("lcd_ready")
            )
        }

        fun fromGateway(values: Any?): LongSensorReadings? {
            val fields = values as? Map<*, *> ?: return null
            val rain = (fields["rainRaw"] as? Number)?.toDouble()
                ?.takeIf { it in 0.0..4095.0 && it % 1 == 0.0 }?.toInt()
            return validated(
                (fields["lightLux"] as? Number)?.toDouble(), fields["reedClosed"] as? Boolean,
                rain, fields["rfidReady"] as? Boolean, fields["lcdReady"] as? Boolean
            )
        }

        private fun validated(
            lux: Double?, reed: Boolean?, rain: Int?, rfid: Boolean?, lcd: Boolean?
        ): LongSensorReadings? {
            val result = LongSensorReadings(
                lux?.takeIf { it.isFinite() && it in 0.0..60000.0 }, reed,
                rain?.takeIf { it in 0..4095 }, rfid, lcd
            )
            return result.takeIf { it != LongSensorReadings() }
        }
    }
}

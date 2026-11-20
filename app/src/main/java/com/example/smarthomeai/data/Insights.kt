package com.example.smarthomeai.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val HISTORY_WINDOW = 7 * 86_400_000L

enum class HistoryRange(val label: String, val duration: Long) {
    LIVE("Trực tiếp",60_000L), DAY("24 giờ", 86_400_000L), WEEK("7 ngày", HISTORY_WINDOW)
}

fun List<SensorSample>.inRange(range: HistoryRange, now: Long = System.currentTimeMillis()) =
    filter { it.timestamp in (now - range.duration)..now }.sortedBy { it.timestamp }

data class SensorSummary(val minimum: Int, val maximum: Int, val average: Double)
fun summarize(samples: List<SensorSample>, temperature: Boolean): SensorSummary? {
    if (samples.isEmpty()) return null
    val values = samples.map { if (temperature) it.temperature else it.humidity }
    return SensorSummary(values.min(), values.max(), values.average())
}

/** Emits once on entering a threshold zone. A normal reading rearms it. */
class EnvironmentEvents {
    private var temperatureZone = 0
    private var humid = false
    private var thresholds: Triple<Int, Int, Int>? = null

    fun evaluate(enabled: Boolean, temperature: Int?, humidity: Int?, hot: Int, cold: Int,
                 humidityLimit: Int): List<String> {
        val settings = Triple(hot, cold, humidityLimit)
        if (!enabled || settings != thresholds) {
            temperatureZone = 0
            humid = false
            thresholds = settings
        }
        if (!enabled) return emptyList()
        return buildList {
            if (temperature != null && temperature in -40..125) {
                val zone = when {
                    temperature >= hot -> 1
                    temperature <= cold -> -1
                    else -> 0
                }
                if (zone != temperatureZone && zone != 0) {
                    add(if (zone > 0) "Phòng quá nóng · ${temperature}°C" else "Phòng quá lạnh · ${temperature}°C")
                }
                temperatureZone = zone
            }
            if (humidity != null && humidity in 0..100) {
                val high = humidity >= humidityLimit
                if (high && !humid) add("Độ ẩm cao · $humidity%")
                humid = high
            }
        }
    }
}

/** UTF-8 BOM lets spreadsheet apps recognize Vietnamese headers. */
fun sensorCsv(samples: List<SensorSample>): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    return buildString {
        append('\uFEFF')
        append("Thời gian,Nhiệt độ (°C),Độ ẩm (%)\r\n")
        samples.sortedBy { it.timestamp }.forEach {
            append(format.format(Date(it.timestamp)))
            append(','); append(it.temperature); append(','); append(it.humidity); append("\r\n")
        }
    }
}

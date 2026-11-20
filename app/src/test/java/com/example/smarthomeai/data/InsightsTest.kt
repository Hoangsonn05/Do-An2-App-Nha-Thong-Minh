package com.example.smarthomeai.data

import org.junit.Assert.*
import org.junit.Test

class InsightsTest {
    @Test fun liveRangeKeepsOnlyTheLatestMinute() {
        val now=120_000L
        val samples=listOf(SensorSample(now-60_001,20,60),SensorSample(now-1000,28,65),SensorSample(now,29,66))
        assertEquals(listOf(28,29),samples.inRange(HistoryRange.LIVE,now).map {it.temperature})
    }
    private val now = 10 * 86_400_000L
    @Test fun dayWindowExcludesOldAndFutureSamples() {
        val list = listOf(SensorSample(now + 1, 30, 70), SensorSample(now, 25, 50),
            SensorSample(now - 86_400_000, 20, 40), SensorSample(now - 86_400_001, 40, 90))
        assertEquals(listOf(20, 25), list.inRange(HistoryRange.DAY, now).map { it.temperature })
    }
    @Test fun weekWindowIncludesSixDayOldReadings() {
        val list = listOf(SensorSample(now - 6 * 86_400_000L, 30, 70), SensorSample(now - HISTORY_WINDOW - 1, 40, 90))
        assertEquals(1, list.inRange(HistoryRange.WEEK, now).size)
    }
    @Test fun emptySummaryHasNoInventedValue() { assertNull(summarize(emptyList(), true)) }
    @Test fun summaryUsesSelectedMeasurement() {
        val list = listOf(SensorSample(1, 20, 40), SensorSample(2, 30, 80))
        assertEquals(SensorSummary(20, 30, 25.0), summarize(list, true))
        assertEquals(SensorSummary(40, 80, 60.0), summarize(list, false))
    }
    @Test fun disabledOrInvalidReadingsDoNotNotify() {
        val gate = EnvironmentEvents()
        assertTrue(gate.evaluate(false, 50, 95, 35, 18, 80).isEmpty())
        assertTrue(gate.evaluate(true, null, null, 35, 18, 80).isEmpty())
        assertTrue(gate.evaluate(true, 200, 101, 35, 18, 80).isEmpty())
    }

    @Test fun hotAndColdNotifyOnlyWhenEnteringTheirZones() {
        val gate = EnvironmentEvents()
        assertEquals(listOf("Phòng quá nóng · 35°C"), gate.evaluate(true, 35, 65, 35, 18, 80))
        assertTrue(gate.evaluate(true, 36, 65, 35, 18, 80).isEmpty())
        assertTrue(gate.evaluate(true, null, 65, 35, 18, 80).isEmpty())
        assertTrue(gate.evaluate(true, 37, 65, 35, 18, 80).isEmpty())
        assertTrue(gate.evaluate(true, 34, 65, 35, 18, 80).isEmpty())
        assertEquals(listOf("Phòng quá nóng · 35°C"), gate.evaluate(true, 35, 65, 35, 18, 80))
        assertEquals(listOf("Phòng quá lạnh · 18°C"), gate.evaluate(true, 18, 65, 35, 18, 80))
        assertTrue(gate.evaluate(true, 17, 65, 35, 18, 80).isEmpty())
    }

    @Test fun humidityHasIndependentThreshold() {
        val gate = EnvironmentEvents()
        assertEquals(1, gate.evaluate(true, 36, 65, 35, 18, 80).size)
        assertEquals(listOf("Độ ẩm cao · 80%"), gate.evaluate(true, 36, 80, 35, 18, 80))
        assertTrue(gate.evaluate(true, 36, 85, 35, 18, 80).isEmpty())
    }

    @Test fun csvUsesUtf8BomAndChronologicalRowsWithTimezone() {
        val csv = sensorCsv(listOf(SensorSample(60_000, 30, 70), SensorSample(0, 20, 50)))
        assertTrue(csv.startsWith("\uFEFFThời gian,Nhiệt độ (°C),Độ ẩm (%)\r\n"))
        val rows = csv.split("\r\n")
        assertTrue(rows[1].endsWith(",20,50"))
        assertTrue(rows[2].endsWith(",30,70"))
        assertTrue(rows[1].substringBefore(',').matches(Regex(".*(Z|[+-]\\d{2}:\\d{2})")))
    }
    @Test fun emptyExportContainsHeaderOnly() { assertEquals(1, sensorCsv(emptyList()).count { it == '\n' }) }
}

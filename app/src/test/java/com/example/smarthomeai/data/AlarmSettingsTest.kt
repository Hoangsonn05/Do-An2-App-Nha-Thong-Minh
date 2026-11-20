package com.example.smarthomeai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlarmSettingsTest {
    private fun settings() = JSONObject("""{"auto_alarm_away":1,"auto_alarm_sleep":0,"alarm_duration_seconds":10}""")
    @Test fun readsIndependentModeSettingsAndTimeout() {
        val value = AlarmSettings.parse(settings())!!
        assertTrue(value.enabledFor(HomeMode.AWAY))
        assertFalse(value.enabledFor(HomeMode.SLEEP))
        assertFalse(value.enabledFor(HomeMode.HOME))
        assertEquals(10,value.durationSeconds)
    }
    @Test fun oldOrIncompleteFirmwareDoesNotInventEditableSettings() {
        assertNull(AlarmSettings.parse(JSONObject()))
        assertNull(AlarmSettings.parse(settings().apply { remove("auto_alarm_sleep") }))
        assertNull(HardwareStatus.parse(JSONObject("""{"hardware_profile":"dht11-leds"}"""))!!.alarmSettings)
    }
    @Test fun rejectsInvalidTimeoutsAndFlags() {
        listOf(-1,0,4,61,999).forEach { assertNull(AlarmSettings.parse(settings().put("alarm_duration_seconds",it))) }
        assertNull(AlarmSettings.parse(settings().put("auto_alarm_away",2)))
        assertNull(AlarmSettings.parse(settings().put("auto_alarm_sleep",JSONObject.NULL)))
    }
    @Test fun silentIncidentsRemainVisibleWithoutClaimingSirenStarted() {
        val events=listOf("proximity_detected","alarm_silent").mapIndexed { i,kind ->
            DeviceEvent("board:${i+1}",(i+1).toLong(),"board",1,1,kind,0,1000)
        }
        val alert=mergeIncidentAlerts(emptyList(),events,events).single()
        assertTrue(alert.title.contains("Vật gần"))
        assertTrue(alert.title.contains("đang tắt"))
        assertFalse(alert.title.contains("đã bật"))
    }
}

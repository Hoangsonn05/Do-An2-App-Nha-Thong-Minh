package com.example.smarthomeai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HardwareStatusTest {
    @Test fun homeDoesNotAlarmFromProximityInNewFirmware() {
        val h=HardwareStatus("dht11-leds",distanceCm=8f,securityProfile="instant-20s",alarmOutput=false,proximityAlarmEnabled=false)
        val state=SmartHomeState(hardware=h,alarm=false)
        assertEquals(true,h.nearObject);assertFalse(state.proximityAlert);assertFalse(state.alarmActive)
    }
    @Test fun reportedAlarmOutputWinsOverDistanceInference() {
        val h=HardwareStatus.parse(json().put("security_profile","instant-20s").put("distance_cm",8).put("alarm_active",0).put("proximity_alarm_enabled",1))!!
        assertFalse(SmartHomeState(hardware=h).alarmActive)
    }
    @Test fun liveRuleMetadataPreservesUnknownCountdown() {
        val h=HardwareStatus.parse(json().put("security_profile","instant-20s").put("door_auto_close_remaining",JSONObject.NULL).put("ultrasonic_alarm_cm",15))!!
        assertTrue(h.instantModes);assertEquals(15f,h.proximityCm,0f);assertNull(h.autoCloseRemaining)
    }
    @Test fun proximityAlarmUsesFirmwareDistanceBoundaries() {
        val hardware=HardwareStatus("dht11-leds")
        assertEquals(true,hardware.copy(distanceCm=2f).nearObject)
        assertEquals(true,hardware.copy(distanceCm=10f).nearObject)
        assertEquals(false,hardware.copy(distanceCm=10.1f).nearObject)
        assertEquals(false,hardware.copy(distanceCm=1.9f).nearObject)
    }
    @Test fun infraredObstructionDoesNotPretendToBeUltrasonicAlarm() {
        val hardware=HardwareStatus("dht11-leds",obstacle=true,distanceCm=30f)
        assertTrue(hardware.doorBlocked);assertEquals(false,hardware.nearObject)
    }
    @Test fun missingEchoIsUnknownRatherThanClearDistance() {
        assertNull(HardwareStatus("dht11-leds",distanceCm=null).nearObject)
    }
    @Test fun ultrasonicAlarmStillRunsWhenManualFlagIsOff() {
        val state=SmartHomeState(alarm=false,hardware=HardwareStatus("dht11-leds",distanceCm=8f))
        assertTrue(state.alarmActive);assertTrue(state.proximityAlert);assertFalse(state.alarm)
    }
    @Test fun stoppingManualTestDoesNotClaimProximityAlarmStopped() {
        assertTrue(alarmCommandFeedback(false,true).contains("cảnh báo tiếp tục"))
        assertEquals("ESP32 đã dừng thử báo động.",alarmCommandFeedback(false,false))
    }
    @Test fun buzzerHardwareUsesCombinedAlarmLabel() {
        assertEquals("Còi & LED vàng",SmartHomeState(hardware=HardwareStatus("dht11-leds",hasBuzzer=true)).deviceLabel(DeviceType.ALARM))
    }
    private fun json()=JSONObject("""{"hardware_profile":"dht11-leds","has_buzzer":0,"auth_required":0,"obstacle":1,"raining":0,"distance_cm":12.5}""")
    @Test fun parsesDocumentHardwareAndIpOnlyAccess() {
        val hardware=HardwareStatus.parse(json())!!
        assertFalse(hardware.hasBuzzer);assertFalse(hardware.authRequired)
        assertEquals(true,hardware.obstacle);assertEquals(false,hardware.raining);assertEquals(12.5f,hardware.distanceCm!!,0.001f)
    }
    @Test fun labelsDoNotPretendLedsAreRelayOrBuzzer() {
        val state=SmartHomeState(hardware=HardwareStatus.parse(json()))
        assertEquals("LED xanh",state.deviceLabel(DeviceType.LIGHT));assertEquals("LED vàng báo động",state.deviceLabel(DeviceType.ALARM));assertEquals("Servo cửa",state.deviceLabel(DeviceType.DOOR))
    }
    @Test fun existingHardwareKeepsItsLabels() { assertEquals(DeviceType.ALARM.label,SmartHomeState().deviceLabel(DeviceType.ALARM)) }
    @Test fun noEchoDoesNotInventZeroDistance() {
        assertNull(HardwareStatus.parse(json().put("distance_cm",JSONObject.NULL))!!.distanceCm)
        assertNull(HardwareStatus.parse(json().put("distance_cm",0))!!.distanceCm)
        assertNull(HardwareStatus.parse(json().put("distance_cm",500))!!.distanceCm)
    }
    @Test fun absentHardwareDoesNotDisableAuthentication() {
        assertNull(HardwareStatus.parse(JSONObject()))
        assertTrue(HardwareStatus.parse(JSONObject("""{"hardware_profile":"dht11-leds"}"""))!!.authRequired)
    }
    @Test fun missingDigitalReadingsStayUnknown() {
        val hardware=HardwareStatus.parse(JSONObject("""{"hardware_profile":"dht11-leds"}"""))!!
        assertNull(hardware.obstacle);assertNull(hardware.raining)
    }
    @Test fun legacyFirmwareDoesNotPretendToHaveSecondServo() {
        val hardware=HardwareStatus.parse(json())!!
        assertFalse(hardware.hasClothesline);assertNull(hardware.clothesRetracted);assertNull(hardware.rainRetractEnabled)
    }
    @Test fun readsClotheslineAndRainRuleIndependently() {
        val hardware=HardwareStatus.parse(json().put("has_clothesline",1).put("clothes_retracted",0).put("rain_retract_enabled",1))!!
        assertTrue(hardware.hasClothesline);assertEquals(false,hardware.clothesRetracted);assertEquals(true,hardware.rainRetractEnabled)
    }
}

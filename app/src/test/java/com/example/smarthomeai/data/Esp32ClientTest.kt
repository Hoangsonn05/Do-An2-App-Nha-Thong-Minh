package com.example.smarthomeai.data

import java.io.ByteArrayInputStream
import java.net.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class Esp32ClientTest {
    @Test fun alarmSettingsUsePostAndAuthenticateWithHeader() {
        body="OK"
        assertTrue(Esp32Client.sendAlarmSettings("192.168.1.50",AlarmSettings(true,false,10),"test-key-123456"))
        assertEquals("POST",lastMethod);assertEquals("test-key-123456",lastKey)
        assertTrue(lastUrl.endsWith("alarm/settings?away=1&sleep=0&seconds=10"))
        body="Not OK";assertFalse(Esp32Client.sendAlarmSettings("192.168.1.50",AlarmSettings()))
    }
    @Test fun invalidAlarmTimeoutIsRejectedBeforeSendingAnything() {
        body="OK"
        listOf(0,4,61,999).forEach { assertFalse(Esp32Client.sendAlarmSettings("192.168.1.50",AlarmSettings(durationSeconds=it))) }
        assertEquals("",lastUrl)
    }
    @Test fun suppliedFirmwareAlarmFieldRemainsManualWhileDistanceTriggersOutput() {
        body="""{"firmware":3,"temp":28,"hum":65,"motion":0,"light":0,"door":1,"alarm":0,"mode":"HOME","phase":"DISARMED","boot":1,"stream_id":"user-board","hardware_profile":"dht11-leds","has_buzzer":1,"distance_cm":7.5,"obstacle":0,"has_clothesline":1,"clothes_retracted":1,"rain_retract_enabled":1}"""
        val sensor=Esp32Client.readSensor("192.168.1.50")!!
        assertEquals(false,sensor.alarm);assertEquals(true,sensor.hardware!!.nearObject);assertTrue(sensor.hardware!!.doorBlocked)
    }
    companion object {
        private var body = ""
        private var code = 200
        private var disconnected = false
        private var lastUrl = ""
        private var lastKey: String? = null
        private var lastMethod = ""
        @JvmStatic @BeforeClass fun installTransport() {
            URL.setURLStreamHandlerFactory { protocol ->
                if(protocol != "http") null else object: URLStreamHandler() {
                    override fun openConnection(url: URL): URLConnection {
                        lastUrl = url.toString()
                        return object: HttpURLConnection(url) {
                            override fun connect() {}
                            override fun disconnect() { disconnected = true }
                            override fun usingProxy() = false
                            override fun getResponseCode(): Int { lastKey=getRequestProperty("X-Device-Key"); lastMethod=requestMethod; return code }
                            override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
                        }
                    }
                }
            }
        }
    }
    @Before fun reset() { code = 200; body = ""; disconnected = false; lastUrl = "" }
    @Test fun rejectInvalidAddresses() {
        listOf("","localhost","1.2.3","1.2.3.999","1.2.3.-1","1.2.3.4:80","1.2.3.4/path").forEach { assertFalse(it, Esp32Client.validIp(it)) }
        assertTrue(Esp32Client.validIp("192.168.1.50"))
    }
    @Test fun commandRequiresExplicitAcknowledgment() { body="Not OK"; assertFalse(Esp32Client.sendCommand("192.168.1.50","door",true)); assertTrue(disconnected) }
    @Test fun commandUsesCorrectDeviceAndValue() { body="OK"; assertTrue(Esp32Client.sendCommand("192.168.1.50","door",false)); assertTrue(lastUrl.endsWith("relay?dev=door&state=0")) }
    @Test fun rejectsHttpFailure() { body="OK"; code=503; assertFalse(Esp32Client.sendCommand("192.168.1.50","light",true)); assertTrue(disconnected) }
    @Test fun parsesLegacyFirmware() {
        body="""{"temp":28,"hum":65,"motion":1,"light":0,"door":1,"alarm":0}"""
        val d = Esp32Client.readSensor("192.168.1.50")!!
        assertEquals(28,d.temp); assertTrue(d.motion); assertEquals(true,d.door); assertNull(d.nightLight)
    }
    @Test fun parsesRulesAndOverdue() {
        body="""{"temp":28,"hum":65,"motion":0,"night_light":1,"door_reminder":1,"door_overdue":1}"""
        val d=Esp32Client.readSensor("192.168.1.50")!!
        assertEquals(true,d.nightLight); assertEquals(true,d.doorReminder); assertTrue(d.doorOverdue)
    }
    @Test fun doesNotInventSensorValues() {
        body="""{"temp":null,"hum":null,"motion":0}"""
        val d=Esp32Client.readSensor("192.168.1.50")!!
        assertNull(d.temp); assertNull(d.hum)
    }
    @Test fun rejectsInvalidMeasurements() {
        body="""{"temp":999,"hum":150,"motion":0}"""
        val d=Esp32Client.readSensor("192.168.1.50")!!
        assertNull(d.temp); assertNull(d.hum)
    }
    @Test fun handlesBrokenJson() { body="not-json"; assertNull(Esp32Client.readSensor("192.168.1.50")); assertTrue(disconnected) }
    @Test fun rejectsInvalidKeysWithoutSendingThem() {
        assertFalse(Esp32Client.validKey("short")); assertFalse(Esp32Client.validKey("abcdefghijkl\nBad")); assertTrue(Esp32Client.validKey("test-key-123456"))
    }
    @Test fun modeChangesUsePostAndHeaderAuthentication() {
        body="OK"
        assertTrue(Esp32Client.sendMode("192.168.1.50",HomeMode.AWAY,"test-key-123456"))
        assertEquals("POST",lastMethod); assertEquals("test-key-123456",lastKey)
        assertTrue(lastUrl.endsWith("mode?value=AWAY")); assertFalse(lastUrl.contains("test-key"))
    }
    @Test fun authenticationFailuresGiveActionableMessage() {
        code=401; assertNull(Esp32Client.readSensor("192.168.1.50","test-key-123456"))
        assertTrue(Esp32Client.lastError!!.contains("Khóa ESP32"))
    }
    @Test fun armedRelayConflictIsReported() {
        code=409; assertFalse(Esp32Client.sendCommand("192.168.1.50","door",true,"test-key-123456"))
        assertTrue(Esp32Client.lastError!!.contains("tắt bảo vệ"))
    }
    @Test fun invalidV3StateDoesNotDowngradeToLegacyControls() {
        body="""{"firmware":3,"temp":25,"hum":60,"motion":0,"mode":"AWAY","phase":"INVALID"}"""
        assertNull(Esp32Client.readSensor("192.168.1.50"))
    }
    @Test fun refusesInvalidTimingBeforeTransport() {
        body="OK"; assertFalse(Esp32Client.sendTiming("192.168.1.50",0,20,30,"test-key-123456"))
    }
    @Test fun ipOnlyModeCommandDoesNotRequireAKey() {
        body="OK";assertTrue(Esp32Client.sendMode("192.168.1.50",HomeMode.HOME,""));assertNull(lastKey);assertEquals("POST",lastMethod)
    }
    @Test fun blockedDoorReportsObstacleInsteadOfAuthenticationError() {
        code=423;assertFalse(Esp32Client.sendCommand("192.168.1.50","door",false));assertTrue(Esp32Client.lastError!!.contains("vật cản"))
    }
    @Test fun parsesDht11AndAdditionalSensorsWithoutKey() {
        body="""{"firmware":3,"temp":29,"hum":65,"motion":1,"mode":"HOME","phase":"DISARMED","boot":1,"stream_id":"board","hardware_profile":"dht11-leds","auth_required":0,"has_buzzer":0,"obstacle":1,"raining":1,"distance_cm":9.5}"""
        val sensor=Esp32Client.readSensor("192.168.1.50")!!
        assertNull(lastKey);assertEquals(29,sensor.temp);assertTrue(sensor.motion);assertFalse(sensor.hardware!!.hasBuzzer);assertEquals(true,sensor.hardware!!.raining)
    }
    @Test fun rulesRequireAck() { body="OK"; assertTrue(Esp32Client.sendRules("192.168.1.50",true,false)); assertTrue(lastUrl.endsWith("rules?night=1&door=0")) }
    @Test fun clotheslineUsesSeparatePostEndpointAndAuthentication() {
        body="OK";assertTrue(Esp32Client.sendClothesline("192.168.1.50",true,"test-key-123456"))
        assertEquals("POST",lastMethod);assertEquals("test-key-123456",lastKey);assertTrue(lastUrl.endsWith("clothesline?state=1"))
        assertTrue(Esp32Client.sendClothesline("192.168.1.50",false));assertTrue(lastUrl.endsWith("clothesline?state=0"));assertNull(lastKey)
    }
    @Test fun rainRuleRequiresAcknowledgment() {
        body="OK";assertTrue(Esp32Client.sendRainRetraction("192.168.1.50",false));assertEquals("POST",lastMethod)
        assertTrue(lastUrl.endsWith("clothesline/auto?enabled=0"));body="Not OK";assertFalse(Esp32Client.sendRainRetraction("192.168.1.50",true))
    }
    @Test fun rainInterlockExplainsWhyExtendingIsBlocked() {
        code=412;assertFalse(Esp32Client.sendClothesline("192.168.1.50",false));assertTrue(Esp32Client.lastError!!.contains("Đang mưa"))
    }
}

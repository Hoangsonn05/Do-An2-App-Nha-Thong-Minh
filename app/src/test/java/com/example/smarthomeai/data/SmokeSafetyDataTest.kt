package com.example.smarthomeai.data

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmokeSafetyDataTest {
    private var body = ""
    private var code = 200
    private var disconnected = false
    private var lastUrl = ""
    private var lastKey: String? = null
    private var lastMethod = ""
    private lateinit var originalFactory: (URL) -> HttpURLConnection

    @Before
    fun setUpTransport() {
        originalFactory = Esp32Client.connectionFactory
        body = ""
        code = 200
        disconnected = false
        lastUrl = ""
        lastKey = null
        lastMethod = ""
        Esp32Client.connectionFactory = { url ->
            lastUrl = url.toString()
            object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {
                    disconnected = true
                }
                override fun usingProxy() = false
                override fun getResponseCode(): Int {
                    lastKey = getRequestProperty("X-Device-Key")
                    lastMethod = requestMethod
                    return code
                }
                override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
            }
        }
    }

    @After
    fun tearDownTransport() {
        Esp32Client.connectionFactory = originalFactory
    }

    @Test
    fun deviceTypeEntriesRemainExactlyThreeAndAlertTypeIncludesSmoke() {
        assertEquals(listOf(DeviceType.LIGHT, DeviceType.DOOR, DeviceType.ALARM), DeviceType.entries)
        assertTrue(AlertType.entries.contains(AlertType.SMOKE))
        val defaultState = SmartHomeState()
        assertTrue(defaultState.smokeAlertsEnabled)
        assertEquals(300, defaultState.smokeLimit)
        assertTrue(defaultState.smokeAutoVentilation)
        assertFalse(defaultState.ventilationBusy)
        assertNull(defaultState.ventilationMessage)
    }

    @Test
    fun parsesLegacyFirmwareWithoutMq2WindowOrFan() {
        assertNull(HardwareStatus.parse(JSONObject()))
        val legacyJson = JSONObject(
            """{"hardware_profile":"dht11-leds","auth_required":1,"has_buzzer":1,"obstacle":0,"raining":0,"distance_cm":25.0}"""
        )
        val status = HardwareStatus.parse(legacyJson)!!
        assertFalse(status.hasMq2)
        assertNull(status.smokePpm)
        assertNull(status.smokeAlert)
        assertFalse(status.hasWindow)
        assertNull(status.windowOpen)
        assertFalse(status.hasFan)
        assertNull(status.fanOn)
        assertNull(status.smokeAutoEnabled)
        assertEquals(DEFAULT_SMOKE_LIMIT_PPM, status.smokeThresholdPpm)
    }

    @Test
    fun parsesNewMq2VentilationFirmwareAndRejectsOutOfRangePpm() {
        val json = JSONObject(
            """{"hardware_profile":"dht11-leds","has_mq2":1,"smoke_ppm":320,"smoke_alert":1,"has_window":1,"window_open":1,"has_fan":1,"fan_on":1,"smoke_auto_enabled":1,"smoke_threshold_ppm":300}"""
        )
        val status = HardwareStatus.parse(json)!!
        assertTrue(status.hasMq2)
        assertEquals(320, status.smokePpm)
        assertEquals(true, status.smokeAlert)
        assertTrue(status.hasWindow)
        assertEquals(true, status.windowOpen)
        assertTrue(status.hasFan)
        assertEquals(true, status.fanOn)
        assertEquals(true, status.smokeAutoEnabled)
        assertEquals(300, status.smokeThresholdPpm)

        val state = SmartHomeState(esp32Online = true, hardware = status)
        assertEquals(320, state.effectiveSmokePpm)
        assertTrue(state.effectiveSmokeAuto)
        assertTrue(state.smokeHazardActive)
        assertTrue(state.ventilationSafetyLocked)

        val negativePpm = HardwareStatus.parse(JSONObject("""{"hardware_profile":"dht11-leds","has_mq2":1,"smoke_ppm":-5}"""))!!
        assertNull(negativePpm.smokePpm)

        val overflowPpm = HardwareStatus.parse(JSONObject("""{"hardware_profile":"dht11-leds","has_mq2":1,"smoke_ppm":25000}"""))!!
        assertNull(overflowPpm.smokePpm)

        val nullPpm = HardwareStatus.parse(JSONObject("""{"smoke_ppm":null,"has_mq2":1}"""))!!
        assertEquals("mq2-ventilation", nullPpm.profile)
        assertTrue(nullPpm.authRequired)
        assertTrue(nullPpm.hasMq2)
        assertNull(nullPpm.smokePpm)
    }

    @Test
    fun esp32ClientSendsWindowFanAndSmokeConfigWithPostAndAuthHeader() {
        body = "OK"
        assertTrue(Esp32Client.sendWindow("192.168.1.50", open = true, key = "test-key-123456"))
        assertEquals("POST", lastMethod)
        assertEquals("test-key-123456", lastKey)
        assertTrue(lastUrl.endsWith("window?state=1"))
        assertTrue(disconnected)

        assertTrue(Esp32Client.sendFan("192.168.1.50", on = true, key = "test-key-123456"))
        assertEquals("POST", lastMethod)
        assertEquals("test-key-123456", lastKey)
        assertTrue(lastUrl.endsWith("fan?state=1"))

        assertTrue(Esp32Client.sendSmokeConfig("192.168.1.50", autoEnabled = true, thresholdPpm = 350, key = "test-key-123456"))
        assertEquals("POST", lastMethod)
        assertEquals("test-key-123456", lastKey)
        assertTrue(lastUrl.endsWith("smoke/config?auto=1&threshold=350"))
    }

    @Test
    fun esp32ClientRejectsOutOfRangeSmokeConfigBeforeTransportAndExplainsHttp412() {
        body = "OK"
        listOf(0, 49, 5001, 10001).forEach { badLimit ->
            assertFalse(Esp32Client.sendSmokeConfig("192.168.1.50", autoEnabled = true, thresholdPpm = badLimit, key = "test-key-123456"))
        }
        assertEquals("", lastUrl)

        code = 412
        assertFalse(Esp32Client.sendWindow("192.168.1.50", open = false, key = "test-key-123456"))
        assertTrue(Esp32Client.lastError!!.contains("khói vượt ngưỡng"))

        assertFalse(Esp32Client.sendFan("192.168.1.50", on = false, key = "test-key-123456"))
        assertTrue(Esp32Client.lastError!!.contains("khói vượt ngưỡng"))

        assertFalse(Esp32Client.sendClothesline("192.168.1.50", retracted = false, key = "test-key-123456"))
        assertTrue(Esp32Client.lastError!!.contains("Đang mưa"))
    }

    @Test
    fun smokeSafetyGateEnforcesThreshold300PpmAndHysteresisRearm() {
        val gate = SmokeSafetyGate()
        assertNull(gate.evaluate(enabled = true, smokePpm = 299))
        assertFalse(gate.isActive)

        assertEquals("Cảnh báo khói vượt ngưỡng · 300 ppm", gate.evaluate(enabled = true, smokePpm = 300))
        assertTrue(gate.isActive)

        assertNull(gate.evaluate(enabled = true, smokePpm = 350))
        assertTrue(gate.isActive)

        assertNull(gate.evaluate(enabled = true, smokePpm = null))
        assertNull(gate.evaluate(enabled = true, smokePpm = 20_000))
        assertTrue(gate.isActive)

        assertNull(gate.evaluate(enabled = true, smokePpm = 260))
        assertTrue(gate.isActive)

        assertNull(gate.evaluate(enabled = true, smokePpm = 249))
        assertFalse(gate.isActive)

        assertEquals("Cảnh báo khói vượt ngưỡng · 300 ppm", gate.evaluate(enabled = true, smokePpm = 300))
        assertTrue(gate.isActive)

        gate.reset()
        assertFalse(gate.isActive)
        assertNull(gate.evaluate(enabled = false, smokePpm = 400))
    }

    @Test
    fun sensorCsvAndSummaryPreserveBackwardCompatibilityAndSupportSmokeColumn() {
        val legacySamples = listOf(SensorSample(60_000, 30, 70), SensorSample(0, 20, 50))
        val legacyCsv = sensorCsv(legacySamples)
        assertTrue(legacyCsv.startsWith("\uFEFFThời gian,Nhiệt độ (°C),Độ ẩm (%)\r\n"))
        val legacyRows = legacyCsv.split("\r\n")
        assertTrue(legacyRows[1].endsWith(",20,50"))
        assertTrue(legacyRows[2].endsWith(",30,70"))
        assertNull(summarizeSmoke(legacySamples))

        val smokeSamples = listOf(
            SensorSample(60_000, 30, 70, smokePpm = 320),
            SensorSample(0, 20, 50, smokePpm = null)
        )
        val smokeCsv = sensorCsv(smokeSamples)
        assertTrue(smokeCsv.startsWith("\uFEFFThời gian,Nhiệt độ (°C),Độ ẩm (%),Khói MQ-2 (ppm)\r\n"))
        val smokeRows = smokeCsv.split("\r\n")
        assertTrue(smokeRows[1].endsWith(",20,50,"))
        assertTrue(smokeRows[2].endsWith(",30,70,320"))
        assertEquals(SensorSummary(320, 320, 320.0), summarizeSmoke(smokeSamples))
    }

    @Test
    fun deviceEventMapsAllEightSmokeAndVentilationKinds() {
        fun eventTitle(kind: String) = DeviceEvent("s:1", 1, "s", 1, 0, kind, 0, 1000).title
        assertEquals("Phát hiện khói vượt ngưỡng", eventTitle("smoke_detected"))
        assertEquals("Có khói · tự động mở cửa sổ và bật quạt", eventTitle("smoke_auto_vent"))
        assertEquals("Nồng độ khói đã về mức an toàn", eventTitle("smoke_cleared"))
        assertEquals("Đã mở cửa sổ", eventTitle("window_opened"))
        assertEquals("Đã đóng cửa sổ", eventTitle("window_closed"))
        assertEquals("Đã bật quạt thông gió", eventTitle("fan_on"))
        assertEquals("Đã tắt quạt thông gió", eventTitle("fan_off"))
        assertEquals("Đã đổi thiết lập cảnh báo khói", eventTitle("smoke_rule_changed"))
    }
}

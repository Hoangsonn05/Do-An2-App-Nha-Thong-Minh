package com.example.smarthomeai.data

import com.example.smarthomeai.cloud.CloudMapper
import com.example.smarthomeai.cloud.GatewaySnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LongSensorReadingsTest {
    @Test fun firmwareReadingsSurviveGatewayRoundTrip() {
        val hardware = HardwareStatus.parse(JSONObject(
            """{"hardware_profile":"long-bosung","light_lux":22.5,"reed_closed":0,
                "rain_raw":4095,"rfid_ready":1,"lcd_ready":0}"""
        ))!!
        val readings = LongSensorReadings(22.5, false, 4095, true, false)
        assertEquals(readings, hardware.longSensors)

        val payload = CloudMapper.buildGatewayStatePayload(
            GatewaySnapshot(online = true, temp = 24, hum = 70, longSensors = readings),
            "owner", CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER
        )
        val values = payload.getValue("values") as Map<*, *>
        assertEquals(readings, LongSensorReadings.fromGateway(values["longSensors"]))
        assertEquals(24, values["temp"])
    }

    @Test fun olderFirmwareOmitsTheOptionalFields() {
        assertNull(LongSensorReadings.fromFirmware(JSONObject("""{"temp":24,"hum":70}""")))
        assertNull(LongSensorReadings.fromGateway(mapOf("lightLux" to -1, "rainRaw" to 4.5)))
        val payload = CloudMapper.buildGatewayStatePayload(GatewaySnapshot(online = true), "owner", CloudMapper.SERVER_TIMESTAMP_PLACEHOLDER)
        assertFalse((payload.getValue("values") as Map<*, *>).containsKey("longSensors"))
    }
}

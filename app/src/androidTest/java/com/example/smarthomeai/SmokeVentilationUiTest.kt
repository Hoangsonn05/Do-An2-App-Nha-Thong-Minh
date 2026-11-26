package com.example.smarthomeai

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smarthomeai.cloud.CloudDeviceSnapshot
import com.example.smarthomeai.cloud.CloudEventRecord
import com.example.smarthomeai.cloud.DataSource
import com.example.smarthomeai.cloud.GatewayStateSnapshot
import com.example.smarthomeai.cloud.ResolvedDataSource
import com.example.smarthomeai.data.HardwareStatus
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.ui.components.CloudReadOnlySummaryCard
import com.example.smarthomeai.ui.components.SmokeConfigDialog
import com.example.smarthomeai.ui.components.SmokeVentilationCard
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeVentilationUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun smokeCard_withoutHardware_showsReadyPlaceholderAndDefault300Ppm() {
        compose.setContent {
            SmartHomeTheme {
                SmokeVentilationCard(
                    state = SmartHomeState(),
                    onWindow = {},
                    onFan = {},
                    onSmokeConfig = { _, _, _ -> }
                )
            }
        }

        compose.onNodeWithTag("smoke-ventilation-card").assertIsDisplayed()
        compose.onNodeWithText("Chờ tích hợp phần cứng ESP32 (MQ-2 · Cửa sổ · Quạt)").assertIsDisplayed()
        compose.onNodeWithText("300 ppm", substring = true).assertExists()
        compose.onNodeWithText("Cài đặt ngưỡng khói (300 ppm)").assertIsDisplayed()
        compose.onNodeWithText("Mở cửa sổ").assertDoesNotExist()
        compose.onNodeWithText("Bật quạt").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun smokeCard_safeReading_showsPpmAndDispatchesWindowAndFanCallbacks() {
        var windowRequested: Boolean? = null
        var fanRequested: Boolean? = null
        val state = SmartHomeState(
            esp32Online = true,
            lastSeen = System.currentTimeMillis(),
            smokeLimit = 300,
            hardware = HardwareStatus(
                profile = "mq2-ventilation",
                hasMq2 = true,
                smokePpm = 120,
                smokeAlert = false,
                hasWindow = true,
                windowOpen = false,
                hasFan = true,
                fanOn = false,
                smokeAutoEnabled = true
            )
        )

        compose.setContent {
            SmartHomeTheme {
                SmokeVentilationCard(
                    state = state,
                    onWindow = { windowRequested = it },
                    onFan = { fanRequested = it },
                    onSmokeConfig = { _, _, _ -> }
                )
            }
        }

        compose.onNodeWithText("120 ppm").assertIsDisplayed()
        compose.onNodeWithText("Cảnh báo khói!").assertDoesNotExist()
        compose.onNodeWithText("Mở cửa sổ").assertIsEnabled().performClick()
        compose.onNodeWithText("Bật quạt").assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(true, windowRequested)
            assertEquals(true, fanRequested)
        }
    }

    @Test
    fun smokeCard_hazardReading_showsAlertAndLocksCloseWindowAndTurnOffFan() {
        val state = SmartHomeState(
            esp32Online = true,
            lastSeen = System.currentTimeMillis(),
            smokeLimit = 300,
            smokeAutoVentilation = true,
            hardware = HardwareStatus(
                profile = "mq2-ventilation",
                hasMq2 = true,
                smokePpm = 320,
                smokeAlert = true,
                hasWindow = true,
                windowOpen = true,
                hasFan = true,
                fanOn = true,
                smokeAutoEnabled = true
            )
        )

        compose.setContent {
            SmartHomeTheme {
                SmokeVentilationCard(
                    state = state,
                    onWindow = {},
                    onFan = {},
                    onSmokeConfig = { _, _, _ -> }
                )
            }
        }

        compose.onNodeWithText("320 ppm").assertIsDisplayed()
        compose.onNodeWithText("Cảnh báo khói!").assertIsDisplayed()
        compose.onNodeWithText("Cảnh báo khói vượt ngưỡng (300 ppm)!", substring = true).assertExists()
        compose.onNodeWithText("Đóng cửa sổ").assertIsNotEnabled()
        compose.onNodeWithText("Tắt quạt").assertIsNotEnabled()
    }

    @Test
    fun smokeCard_whenOffline_disablesAllControlsAndHidesLiveHazardBanner() {
        val state = SmartHomeState(
            esp32Online = false,
            lastSeen = System.currentTimeMillis() - 60_000L,
            smokeLimit = 300,
            smokeAutoVentilation = true,
            hardware = HardwareStatus(
                profile = "mq2-ventilation",
                hasMq2 = true,
                smokePpm = 360,
                smokeAlert = true,
                hasWindow = true,
                windowOpen = false,
                hasFan = true,
                fanOn = false,
                smokeAutoEnabled = true
            )
        )

        compose.setContent {
            SmartHomeTheme {
                SmokeVentilationCard(
                    state = state,
                    onWindow = {},
                    onFan = {},
                    onSmokeConfig = { _, _, _ -> }
                )
            }
        }

        compose.onNodeWithText("Cảnh báo khói!").assertDoesNotExist()
        compose.onNodeWithText("Mất kết nối · số đo lần cuối").assertIsDisplayed()
        compose.onNodeWithText("Mở cửa sổ").assertIsNotEnabled()
        compose.onNodeWithText("Đóng cửa sổ").assertIsNotEnabled()
        compose.onNodeWithText("Bật quạt").assertIsNotEnabled()
        compose.onNodeWithText("Tắt quạt").assertIsNotEnabled()
        compose.onNodeWithText("Cài đặt ngưỡng khói (300 ppm)").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Tự mở cửa sổ & quạt khi có khói").assertIsNotEnabled()
    }

    @Test
    fun smokeConfigDialog_quickSelect500Ppm_callsOnSmokeConfigOnSave() {
        var saved: Triple<Boolean, Int, Boolean>? = null

        compose.setContent {
            SmartHomeTheme {
                SmokeConfigDialog(
                    state = SmartHomeState(
                        smokeAlertsEnabled = true,
                        smokeLimit = 300,
                        smokeAutoVentilation = true
                    ),
                    onDismiss = {},
                    onSave = { alerts, limit, auto ->
                        saved = Triple(alerts, limit, auto)
                    }
                )
            }
        }

        compose.onNodeWithText("500 ppm").performClick()
        compose.onNodeWithText("Lưu thiết lập").performClick()

        compose.runOnIdle {
            assertEquals(Triple(true, 500, true), saved)
        }
    }

    @Test
    fun cloudReadOnlySummaryCard_displaysSmokePpmWindowFanAndAutoVentEvent() {
        val now = System.currentTimeMillis()
        val snapshot = CloudDeviceSnapshot(
            deviceId = "esp32_gw_01",
            gatewayState = GatewayStateSnapshot(
                online = true,
                receivedAt = now,
                temp = 29,
                hum = 65,
                mode = HomeMode.HOME,
                smokePpm = 340,
                smokeAlert = true,
                window = true,
                fan = true
            ),
            recentEvents = listOf(
                CloudEventRecord(
                    id = "evt_smoke_1",
                    type = "smoke_auto_vent",
                    severity = "critical",
                    stream = "board-1",
                    seq = 12L,
                    receivedAt = now
                )
            )
        )
        val resolved = ResolvedDataSource(
            source = DataSource.CLOUD,
            updatedAt = now,
            isStale = false,
            gatewayOnline = true
        )

        compose.setContent {
            SmartHomeTheme {
                CloudReadOnlySummaryCard(
                    snapshot = snapshot,
                    resolved = resolved
                )
            }
        }

        compose.onNodeWithText("340 ppm").assertIsDisplayed()
        compose.onNodeWithText("Đang mở").assertIsDisplayed()
        compose.onNodeWithText("Đang bật").assertIsDisplayed()
        compose.onNodeWithText("Có khói · tự động mở cửa sổ và bật quạt").assertIsDisplayed()
    }
}

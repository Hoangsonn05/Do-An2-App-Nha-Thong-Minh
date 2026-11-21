package com.example.smarthomeai.cloud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smarthomeai.data.DeviceType
import com.example.smarthomeai.data.HardwareStatus
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SecurityPhase
import com.example.smarthomeai.data.SecurityStatus
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.data.deviceLabel
import com.example.smarthomeai.data.timeOnly
import com.example.smarthomeai.ui.components.CloudHomeBannerCard
import com.example.smarthomeai.ui.screens.HomeScreen
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudHybridViewUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun fakeDeviceBridge(
        syncFlow: MutableStateFlow<GatewaySyncUiState> = MutableStateFlow(GatewaySyncUiState()),
        snapshotFlow: MutableStateFlow<CloudDeviceSnapshot?> = MutableStateFlow(null),
        onRetry: () -> Unit = {}
    ): CloudDeviceUiBridge = object : CloudDeviceUiBridge {
        override val gatewaySync: StateFlow<GatewaySyncUiState> = syncFlow
        override val cloudSnapshot: StateFlow<CloudDeviceSnapshot?> = snapshotFlow
        override fun retrySync() = onRetry()
    }

    @Test
    fun homeScreen_whenEsp32Online_lanWinsOverNewerCloudSnapshot() {
        val now = System.currentTimeMillis()
        val lanState = SmartHomeState(
            esp32Online = true,
            lastSeen = now - 30_000L,
            temperature = 27,
            humidity = 62,
            motionDetected = false,
            hardware = HardwareStatus(profile = "dht11-leds", hasBuzzer = true, raining = false),
            security = SecurityStatus(HomeMode.HOME, SecurityPhase.DISARMED, 0, "board-1", 1, 0, demoModes = true)
        )
        val newerCloudSnapshot = CloudDeviceSnapshot(
            deviceId = "esp32_gw_01",
            gatewayState = GatewayStateSnapshot(
                online = true,
                receivedAt = now - 5_000L,
                temp = 39,
                hum = 91,
                motion = true,
                light = true,
                door = true,
                alarm = true,
                monitoring = true,
                mode = HomeMode.AWAY
            )
        )
        val deviceBridge = fakeDeviceBridge(snapshotFlow = MutableStateFlow(newerCloudSnapshot))

        compose.setContent {
            SmartHomeTheme {
                HomeScreen(
                    state = lanState,
                    onSetDevice = { _, _ -> },
                    onMode = {},
                    onRefresh = {},
                    onNavigate = {},
                    cloudBridge = null,
                    deviceBridge = deviceBridge
                )
            }
        }

        compose.onNodeWithText("ESP32 đã kết nối").assertIsDisplayed()
        compose.onNodeWithText("Nguồn: LAN trực tiếp").assertIsDisplayed()
        compose.onNodeWithText("Dữ liệu cũ").assertDoesNotExist()

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("27°C"))
        compose.onNodeWithText("27°C").assertIsDisplayed()
        compose.onNodeWithText("62%").assertIsDisplayed()
        compose.onNodeWithText("Không mưa").assertIsDisplayed()

        compose.onNodeWithText("39°C").assertDoesNotExist()
        compose.onNodeWithText("91%").assertDoesNotExist()
        compose.onNodeWithText("Đang hiển thị dữ liệu Cloud · chỉ đọc").assertDoesNotExist()
        compose.onNodeWithText("Trạng thái từ xa qua Cloud (chỉ đọc)").assertDoesNotExist()

        val lightLabel = lanState.deviceLabel(DeviceType.LIGHT)
        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(lightLabel))
        compose.onNodeWithContentDescription(lightLabel).assertIsEnabled()
    }

    @Test
    fun homeScreen_whenLanOfflineAndCloudNewer_showsReadOnlyCloudFallbackAndKeepsControlsLocked() {
        val now = System.currentTimeMillis()
        val cloudReceivedAt = now - 30_000L
        val localOfflineState = SmartHomeState(
            esp32Online = false,
            lastSeen = now - 600_000L,
            temperature = 21,
            humidity = 50,
            hardware = HardwareStatus(
                profile = "dht11-leds",
                hasBuzzer = true,
                hasClothesline = true,
                clothesRetracted = false,
                rainRetractEnabled = true,
                raining = false
            ),
            security = SecurityStatus(HomeMode.HOME, SecurityPhase.DISARMED, 0, "board-1", 1, 0, demoModes = true)
        )
        val cloudSnapshot = CloudDeviceSnapshot(
            deviceId = "esp32_gw_01",
            gatewayState = GatewayStateSnapshot(
                online = true,
                receivedAt = cloudReceivedAt,
                temp = 33,
                hum = 78,
                motion = true,
                light = true,
                door = true,
                alarm = false,
                monitoring = true,
                mode = HomeMode.AWAY
            ),
            recentEvents = listOf(
                CloudEventRecord("s1_3", "rain_started", "warn", "s1", 3L, cloudReceivedAt),
                CloudEventRecord("s1_2", "clothes_rain_retract", "warn", "s1", 2L, cloudReceivedAt - 5_000L),
                CloudEventRecord("s1_1", "motion_entry", "warn", "s1", 1L, cloudReceivedAt - 10_000L),
                CloudEventRecord("s1_0", "servo_open", "info", "s1", 0L, cloudReceivedAt - 20_000L)
            )
        )
        val deviceBridge = fakeDeviceBridge(snapshotFlow = MutableStateFlow(cloudSnapshot))

        compose.setContent {
            SmartHomeTheme {
                HomeScreen(
                    state = localOfflineState,
                    onSetDevice = { _, _ -> },
                    onMode = {},
                    onRefresh = {},
                    onNavigate = {},
                    cloudBridge = null,
                    deviceBridge = deviceBridge
                )
            }
        }

        compose.onNodeWithText("ESP32 mất kết nối").assertIsDisplayed()
        compose.onNodeWithText("Nguồn: Cloud (chỉ đọc)").assertIsDisplayed()
        compose.onNodeWithText("Dữ liệu cũ").assertDoesNotExist()

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đang hiển thị dữ liệu Cloud · chỉ đọc"))
        compose.onNodeWithText("Đang hiển thị dữ liệu Cloud · chỉ đọc").assertIsDisplayed()
        compose.onNodeWithText("33°C").assertIsDisplayed()
        compose.onNodeWithText("78%").assertIsDisplayed()
        compose.onNodeWithText("Phát hiện").assertIsDisplayed()
        compose.onNodeWithText("Cập nhật Cloud lúc ${timeOnly(cloudReceivedAt)} · Gateway online").assertIsDisplayed()
        compose.onNodeWithText("21°C").assertDoesNotExist()

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Trạng thái từ xa qua Cloud (chỉ đọc)"))
        compose.onNodeWithText("Trạng thái từ xa qua Cloud (chỉ đọc)").assertIsDisplayed()
        compose.onNodeWithText("Gateway: Trực tuyến (online)").assertIsDisplayed()
        compose.onNodeWithText("Đang bật").assertIsDisplayed()
        compose.onNodeWithText("Đang mở").assertIsDisplayed()
        compose.onNodeWithText("Bình thường").assertIsDisplayed()
        compose.onNodeWithText("Ra ngoài · Đang giám sát").assertIsDisplayed()
        compose.onNodeWithText("Mưa & giàn phơi: Xem trong sự kiện Cloud").assertIsDisplayed()

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Cảm biến phát hiện mưa"))
        compose.onNodeWithText("Cảm biến phát hiện mưa").assertIsDisplayed()
        compose.onNodeWithText("Có mưa · giàn phơi tự thu vào").assertIsDisplayed()
        compose.onNodeWithText("PIR phát hiện chuyển động · bắt đầu chờ xác nhận").assertIsDisplayed()
        compose.onNodeWithText("Đã gửi servo đến vị trí mở").assertDoesNotExist()

        // Verify all hardware LAN controls remain locked while offline
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Ra ngoài", substring = false))
        compose.onNodeWithText("Ra ngoài", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Ở nhà", substring = false).assertIsNotEnabled()

        val doorLabel = localOfflineState.deviceLabel(DeviceType.DOOR)
        val lightLabel = localOfflineState.deviceLabel(DeviceType.LIGHT)
        val alarmLabel = localOfflineState.deviceLabel(DeviceType.ALARM)

        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(doorLabel))
        compose.onNodeWithContentDescription(doorLabel).assertIsNotEnabled()
        compose.onNodeWithText("Đóng cửa", substring = false).assertIsNotEnabled()

        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(lightLabel))
        compose.onAllNodes(isToggleable()).assertAll(isNotEnabled())

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Thu vào", substring = false))
        compose.onNodeWithText("Thu vào", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Phơi ra", substring = false).assertIsNotEnabled()

        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(alarmLabel))
        compose.onNodeWithContentDescription(alarmLabel).assertIsNotEnabled()
    }

    @Test
    fun homeScreen_whenLanOfflineAndLocalCacheUsed_showsLocalCacheAndStaleBadge() {
        val now = System.currentTimeMillis()
        val staleLocalTs = now - 600_000L
        val staleLocalState = SmartHomeState(
            esp32Online = false,
            lastSeen = staleLocalTs,
            temperature = 25,
            humidity = 60,
            motionDetected = false
        )
        val olderCloudSnapshot = CloudDeviceSnapshot(
            deviceId = "esp32_gw_01",
            gatewayState = GatewayStateSnapshot(
                online = false,
                receivedAt = staleLocalTs - 60_000L,
                temp = 40,
                hum = 90
            )
        )
        val deviceBridge = fakeDeviceBridge(snapshotFlow = MutableStateFlow(olderCloudSnapshot))

        compose.setContent {
            SmartHomeTheme {
                HomeScreen(
                    state = staleLocalState,
                    onSetDevice = { _, _ -> },
                    onMode = {},
                    onRefresh = {},
                    onNavigate = {},
                    cloudBridge = null,
                    deviceBridge = deviceBridge
                )
            }
        }

        compose.onNodeWithText("ESP32 mất kết nối").assertIsDisplayed()
        compose.onNodeWithText("Nguồn: Bộ nhớ máy").assertIsDisplayed()
        compose.onNodeWithText("Dữ liệu cũ").assertIsDisplayed()
        compose.onNodeWithText("Cập nhật dữ liệu lúc ${timeOnly(staleLocalTs)}").assertIsDisplayed()

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("25°C"))
        compose.onNodeWithText("25°C").assertIsDisplayed()
        compose.onNodeWithText("60%").assertIsDisplayed()
        compose.onNodeWithText("Cập nhật lần cuối lúc ${timeOnly(staleLocalTs)}").assertIsDisplayed()

        compose.onNodeWithText("40°C").assertDoesNotExist()
        compose.onNodeWithText("Trạng thái từ xa qua Cloud (chỉ đọc)").assertDoesNotExist()
    }

    @Test
    fun homeScreen_whenIdleOfflineOverFiveMinutes_automaticallyShowsStaleBadge() {
        val now = System.currentTimeMillis()
        val offlineState = SmartHomeState(
            esp32Online = false,
            lastSeen = now - 600_000L,
            temperature = 21,
            humidity = 50
        )
        val cloudSnapshot = CloudDeviceSnapshot(
            deviceId = "esp32_gw_01",
            gatewayState = GatewayStateSnapshot(
                online = true,
                receivedAt = now - 30_000L,
                temp = 31,
                hum = 70
            )
        )
        val deviceBridge = fakeDeviceBridge(snapshotFlow = MutableStateFlow(cloudSnapshot))

        compose.setContent {
            SmartHomeTheme {
                HomeScreen(
                    state = offlineState,
                    onSetDevice = { _, _ -> },
                    onMode = {},
                    onRefresh = {},
                    onNavigate = {},
                    cloudBridge = null,
                    deviceBridge = deviceBridge
                )
            }
        }

        compose.onNodeWithText("Nguồn: Cloud (chỉ đọc)").assertIsDisplayed()
        compose.onNodeWithText("Dữ liệu cũ").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(300_000L)
        compose.waitForIdle()

        compose.onNodeWithText("Dữ liệu cũ").assertIsDisplayed()
    }

    @Test
    fun cloudHomeBanner_displaysGatewaySyncPhases_andMultiDeviceWarning_andRetryCallback() {
        val now = System.currentTimeMillis()
        val readyCloudState = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uOwner",
            homes = listOf(CloudHome("home_001", "Nhà chính", CloudRole.OWNER)),
            selectedHomeId = "home_001"
        )
        var syncState by mutableStateOf(GatewaySyncUiState(phase = GatewaySyncPhase.REGISTERING))
        var retryCalled = false

        compose.setContent {
            SmartHomeTheme {
                CloudHomeBannerCard(
                    cloudState = readyCloudState,
                    onSelectHome = {},
                    onCreateHome = {},
                    onRetry = {},
                    singleHomeMode = true,
                    gatewaySyncState = syncState,
                    onRetryGatewaySync = { retryCalled = true }
                )
            }
        }

        compose.onNodeWithText("Đang đăng ký thiết bị Gateway…").assertIsDisplayed()

        compose.runOnIdle {
            syncState = GatewaySyncUiState(phase = GatewaySyncPhase.SYNCING, activeDeviceId = "dev_1")
        }
        compose.onNodeWithText("Đang đồng bộ dữ liệu lên Cloud…").assertIsDisplayed()

        compose.runOnIdle {
            syncState = GatewaySyncUiState(
                phase = GatewaySyncPhase.OK,
                activeDeviceId = "dev_1",
                lastSyncAt = now,
                warningMessage = "Phát hiện 2 thiết bị Gateway đang hoạt động; đang dùng \"ESP32 Smart Home\"."
            )
        }
        compose.onNodeWithText("Đồng bộ Cloud gần nhất lúc ${timeOnly(now)}").assertIsDisplayed()
        compose.onNodeWithText("Phát hiện 2 thiết bị Gateway đang hoạt động; đang dùng \"ESP32 Smart Home\".").assertIsDisplayed()

        compose.runOnIdle {
            syncState = GatewaySyncUiState(phase = GatewaySyncPhase.PAUSED_LAN_OFFLINE, activeDeviceId = "dev_1")
        }
        compose.onNodeWithText("Đồng bộ tạm dừng · ESP32 mất kết nối LAN").assertIsDisplayed()

        compose.runOnIdle {
            syncState = GatewaySyncUiState(phase = GatewaySyncPhase.BLOCKED, errorMessage = "Thiết bị Cloud đã bị thu hồi")
        }
        compose.onNodeWithText("Thiết bị Cloud đã bị thu hồi — không thể đồng bộ").assertIsDisplayed()

        compose.runOnIdle {
            syncState = GatewaySyncUiState(
                phase = GatewaySyncPhase.ERROR,
                lastError = CloudError.Offline,
                errorMessage = "Mất mạng tạm thời"
            )
        }
        compose.onNodeWithText("Lỗi đồng bộ Cloud: Mất mạng tạm thời").assertIsDisplayed()
        compose.onNodeWithText("Thử lại").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(retryCalled)
        }
    }
}

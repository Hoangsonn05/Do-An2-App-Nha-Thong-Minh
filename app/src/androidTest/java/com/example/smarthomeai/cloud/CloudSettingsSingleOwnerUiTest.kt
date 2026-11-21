package com.example.smarthomeai.cloud

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.data.timeOnly
import com.example.smarthomeai.ui.components.CloudHomeManagementCard
import com.example.smarthomeai.ui.screens.SettingsScreen
import com.example.smarthomeai.ui.screens.SystemScreen
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSettingsSingleOwnerUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun fakeCloudBridge(
        stateFlow: MutableStateFlow<CloudUiState>
    ): CloudUiBridge = object : CloudUiBridge {
        override val state: StateFlow<CloudUiState> = stateFlow
        override fun selectHome(homeId: String) = Unit
        override fun createHome(name: String) = Unit
        override fun leaveSelectedHome() = Unit
        override fun removeMember(memberUid: String) = Unit
        override fun retry() = Unit
        override fun dismissFeedback() = Unit
    }

    private fun fakeDeviceBridge(
        syncFlow: MutableStateFlow<GatewaySyncUiState>
    ): CloudDeviceUiBridge = object : CloudDeviceUiBridge {
        override val gatewaySync: StateFlow<GatewaySyncUiState> = syncFlow
    }

    @Test
    fun settingsScreen_showsSingleOwnerCloudHome_keepsLocalFaceMembersWithNote_andHidesCloudMembers() {
        val cloudState = MutableStateFlow(
            CloudUiState(
                status = CloudStatus.READY,
                currentUid = "uOwner",
                homes = listOf(CloudHome("home_001", "Nhà chính", CloudRole.OWNER)),
                selectedHomeId = "home_001",
                membersStatus = MembersStatus.READY,
                members = listOf(
                    CloudMember("uOwner", CloudRole.OWNER, 1_000L, "uOwner"),
                    CloudMember("uExtra", CloudRole.MEMBER, 2_000L, "uOwner")
                )
            )
        )
        var navigatedRoute: String? = null
        var removedFace: String? = null

        compose.setContent {
            SmartHomeTheme {
                SettingsScreen(
                    state = SmartHomeState(
                        registeredUsers = listOf("An"),
                        userEmail = "owner@example.com"
                    ),
                    onIpChange = {},
                    onAutoAlarm = {},
                    onNotify = {},
                    onRemoveFace = { removedFace = it },
                    onLogout = {},
                    onRefresh = {},
                    onBack = {},
                    onNavigate = { navigatedRoute = it },
                    cloudBridge = fakeCloudBridge(cloudState)
                )
            }
        }

        // Single-owner Cloud section hides multi-home switcher & Cloud member list
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Nhà Cloud"))
        compose.onNodeWithText("Nhà Cloud").assertIsDisplayed()
        compose.onNodeWithText("Chủ sở hữu · Đã kết nối Cloud").assertIsDisplayed()
        compose.onNodeWithText("Nhà chính").assertIsDisplayed()
        compose.onNodeWithText("Chủ nhà (OWNER)").assertIsDisplayed()

        compose.onNodeWithText("Nhà Cloud & thành viên").assertDoesNotExist()
        compose.onNodeWithText("Danh sách nhà (1)").assertDoesNotExist()
        compose.onNodeWithText("Thành viên · Nhà chính").assertDoesNotExist()
        compose.onNodeWithText("Rời khỏi nhà").assertDoesNotExist()
        compose.onNodeWithText("uExtra").assertDoesNotExist()

        // Local FaceStore "Thành viên" section remains fully functional with clear local-only note
        compose.onNodeWithTag("page-list").performScrollToNode(
            hasText("Khuôn mặt mở cửa cục bộ trên điện thoại (không đồng bộ lên Cloud).")
        )
        compose.onNodeWithText("Thành viên").assertIsDisplayed()
        compose.onNodeWithText("Khuôn mặt mở cửa cục bộ trên điện thoại (không đồng bộ lên Cloud).").assertIsDisplayed()
        compose.onNodeWithText("Thêm").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("register", navigatedRoute)
        }

        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription("Xóa khuôn mặt An"))
        compose.onNodeWithText("An").assertIsDisplayed()
        compose.onNodeWithContentDescription("Xóa khuôn mặt An").performClick()
        compose.onNodeWithText("Xóa khuôn mặt?").assertIsDisplayed()
        compose.onNodeWithText("Xóa", substring = false).performClick()
        compose.runOnIdle {
            assertEquals("An", removedFace)
        }
    }

    @Test
    fun settingsScreen_aboutDialog_reflectsLanFirstAndCloudRtdbFallbackArchitecture() {
        compose.setContent {
            SmartHomeTheme {
                SettingsScreen(
                    state = SmartHomeState(userEmail = "owner@example.com"),
                    onIpChange = {},
                    onAutoAlarm = {},
                    onNotify = {},
                    onRemoveFace = {},
                    onLogout = {},
                    onRefresh = {},
                    onBack = {},
                    onNavigate = {},
                    cloudBridge = null
                )
            }
        }

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Về ứng dụng"))
        compose.onNodeWithText("Về ứng dụng").assertIsDisplayed().performClick()

        compose.onNodeWithText("Nhận diện khuôn mặt xử lý trực tiếp trên điện thoại (on-device)", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Wi-Fi LAN trực tiếp tới ESP32", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Firebase RTDB", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("1 nhà – 1 chủ sở hữu", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("chỉ đọc; chưa hỗ trợ điều khiển từ xa hay thông báo đẩy Cloud", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithText("chưa có thông báo cloud từ xa", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Điều khiển và đồng bộ dùng chung LAN", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Đóng").performClick()
        compose.onNodeWithText("Nhận diện khuôn mặt xử lý trực tiếp trên điện thoại (on-device)", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun cloudHomeManagementCard_singleHomeMode_usesSingleHomeCopyWhenSignedOut() {
        compose.setContent {
            SmartHomeTheme {
                CloudHomeManagementCard(
                    cloudState = CloudUiState(status = CloudStatus.SIGNED_OUT, reauthRequired = false),
                    onSelectHome = {},
                    onCreateHome = {},
                    onLeaveHome = {},
                    onRemoveMember = {},
                    onRetry = {},
                    singleHomeMode = true
                )
            }
        }

        compose.onNodeWithText("Nhà Cloud").assertIsDisplayed()
        compose.onNodeWithText("Đăng nhập bằng tài khoản Firebase để đồng bộ nhà Cloud của bạn.").assertIsDisplayed()
        compose.onNodeWithText("đồng bộ danh sách nhà và thành viên", substring = true).assertDoesNotExist()
    }

    @Test
    fun systemScreen_displaysCloudHomeAndGatewaySyncDiagnostics_acrossPhases() {
        val now = System.currentTimeMillis()
        val cloudFlow = MutableStateFlow(
            CloudUiState(
                status = CloudStatus.READY,
                currentUid = "uOwner",
                homes = listOf(CloudHome("home_001", "Nhà chính", CloudRole.OWNER)),
                selectedHomeId = "home_001"
            )
        )
        val syncFlow = MutableStateFlow(
            GatewaySyncUiState(
                phase = GatewaySyncPhase.OK,
                activeDeviceId = "esp32_gw_01",
                lastSyncAt = now
            )
        )

        compose.setContent {
            SmartHomeTheme {
                SystemScreen(
                    state = SmartHomeState(esp32Online = true, lastSeen = now),
                    firebaseConfigured = true,
                    onRefresh = {},
                    onBack = {},
                    onNavigate = {},
                    cloudBridge = fakeCloudBridge(cloudFlow),
                    deviceBridge = fakeDeviceBridge(syncFlow)
                )
            }
        }

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Nhà Cloud"))
        compose.onNodeWithText("Nhà Cloud").assertIsDisplayed()
        compose.onNodeWithText("Nhà chính").assertIsDisplayed()

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đồng bộ Gateway"))
        compose.onNodeWithText("Đồng bộ Gateway").assertIsDisplayed()
        compose.onNodeWithText("OK · ${timeOnly(now)}").assertIsDisplayed()

        val archText = "Điều khiển: Ứng dụng → HTTP LAN → ESP32. Dự phòng xem từ xa: Firebase RTDB (1 nhà). Khuôn mặt xử lý trên điện thoại."
        compose.onNodeWithTag("page-list").performScrollToNode(hasText(archText))
        compose.onNodeWithText(archText).assertIsDisplayed()

        compose.runOnIdle {
            cloudFlow.value = CloudUiState(status = CloudStatus.EMPTY, currentUid = "uOwner")
            syncFlow.value = GatewaySyncUiState(phase = GatewaySyncPhase.SYNCING)
        }
        compose.onNodeWithText("Chưa tạo nhà").assertIsDisplayed()
        compose.onNodeWithText("Đang đồng bộ").assertIsDisplayed()

        compose.runOnIdle {
            cloudFlow.value = CloudUiState(status = CloudStatus.SIGNED_OUT)
            syncFlow.value = GatewaySyncUiState(phase = GatewaySyncPhase.PAUSED_LAN_OFFLINE)
        }
        compose.onNodeWithText("Chưa đăng nhập").assertIsDisplayed()
        compose.onNodeWithText("Tạm dừng khi mất LAN").assertIsDisplayed()

        compose.runOnIdle {
            syncFlow.value = GatewaySyncUiState(phase = GatewaySyncPhase.BLOCKED)
        }
        compose.onNodeWithText("Bị thu hồi").assertIsDisplayed()

        compose.runOnIdle {
            syncFlow.value = GatewaySyncUiState(phase = GatewaySyncPhase.ERROR, errorMessage = "Mất mạng")
        }
        compose.onNodeWithText("Lỗi · Mất mạng").assertIsDisplayed()
    }

    @Test
    fun systemScreen_whenBridgesNullInDemoMode_showsSafeFallbackWithoutCrash() {
        compose.setContent {
            SmartHomeTheme {
                SystemScreen(
                    state = SmartHomeState(),
                    firebaseConfigured = false,
                    onRefresh = {},
                    onBack = {},
                    onNavigate = {},
                    cloudBridge = null,
                    deviceBridge = null
                )
            }
        }

        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đồng bộ Gateway"))
        compose.onNodeWithText("Chế độ tài khoản demo").assertIsDisplayed()
        compose.onNodeWithText("Nhà Cloud").assertIsDisplayed()
        compose.onNodeWithText("Chế độ demo").assertIsDisplayed()
        compose.onNodeWithText("Đồng bộ Gateway").assertIsDisplayed()
        compose.onNodeWithText("Chưa kích hoạt").assertIsDisplayed()
    }
}

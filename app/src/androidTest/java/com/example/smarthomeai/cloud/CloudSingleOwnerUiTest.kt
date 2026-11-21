package com.example.smarthomeai.cloud

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smarthomeai.ui.components.CloudHomeBannerCard
import com.example.smarthomeai.ui.components.CloudHomeManagementCard
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSingleOwnerUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun singleHomeMode_bannerHidesMultiHomeSwitcherAndMemberButtons_showsSingleOwnerBadge() {
        var openManagementClicked = false
        val state = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uOwner",
            homes = listOf(CloudHome("home_001", "Nha Cua Toi", CloudRole.OWNER)),
            selectedHomeId = "home_001"
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeBannerCard(
                    cloudState = state,
                    onSelectHome = {},
                    onCreateHome = {},
                    onRetry = {},
                    onOpenManagement = { openManagementClicked = true },
                    singleHomeMode = true
                )
            }
        }

        // Single owner badge is shown
        compose.onNodeWithText("Chủ sở hữu · Đã kết nối Cloud").assertIsDisplayed()
        compose.onNodeWithText("Nha Cua Toi").assertIsDisplayed()

        // Multi-home & members buttons are hidden
        compose.onNodeWithText("Nhà (1)").assertDoesNotExist()
        compose.onNodeWithText("Thành viên").assertDoesNotExist()

        // Single Home management button is visible and clickable
        compose.onNodeWithText("Quản lý nhà").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(openManagementClicked)
        }
    }

    @Test
    fun singleHomeMode_managementCardHidesCreateButtonWhenOwned_andHidesMembersList() {
        val state = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uOwner",
            homes = listOf(CloudHome("home_001", "Nha Chinh", CloudRole.OWNER)),
            selectedHomeId = "home_001",
            membersStatus = MembersStatus.READY,
            members = listOf(CloudMember("uOwner", CloudRole.OWNER, 1000L, "uOwner"))
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeManagementCard(
                    cloudState = state,
                    onSelectHome = {},
                    onCreateHome = {},
                    onLeaveHome = {},
                    onRemoveMember = {},
                    onRetry = {},
                    singleHomeMode = true
                )
            }
        }

        // Section title is "Nhà Cloud" (not "Nhà Cloud & thành viên")
        compose.onNodeWithText("Nhà Cloud").assertIsDisplayed()
        // Create home action is hidden when already owning a home
        compose.onNodeWithText("Tạo nhà").assertDoesNotExist()

        // Shows single primary home info
        compose.onNodeWithText("Nha Chinh").assertIsDisplayed()
        compose.onNodeWithText("Chủ nhà (OWNER)").assertIsDisplayed()

        // Cloud members list, leave home button, and B4 notice are hidden
        compose.onNodeWithText("Danh sách nhà (1)").assertDoesNotExist()
        compose.onNodeWithText("Thành viên · Nha Chinh").assertDoesNotExist()
        compose.onNodeWithText("Rời khỏi nhà").assertDoesNotExist()
        compose.onNodeWithText("Mời thành viên mới (Invitation / Join / QR) thuộc giai đoạn thiết kế hợp đồng riêng.").assertDoesNotExist()
    }

    @Test
    fun singleHomeMode_legacyMultiOwner_showsWarningBannerAndSessionChooser() {
        var selectedId: String? = null
        val state = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uOwner",
            homes = listOf(
                CloudHome("home_001", "Nha Cu 1", CloudRole.OWNER),
                CloudHome("home_002", "Nha Cu 2", CloudRole.OWNER)
            ),
            selectedHomeId = "home_001"
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeManagementCard(
                    cloudState = state,
                    onSelectHome = { selectedId = it },
                    onCreateHome = {},
                    onLeaveHome = {},
                    onRemoveMember = {},
                    onRetry = {},
                    singleHomeMode = true
                )
            }
        }

        // Warning banner is displayed
        compose.onNodeWithText(
            "Phát hiện 2 nhà Cloud trên tài khoản cũ. Đang dùng nhà chính \"Nha Cu 1\". Hệ thống không tự xóa nhà cũ."
        ).assertIsDisplayed()

        // In-session chooser shows owned homes
        compose.onNodeWithText("Chọn nhà chính trong phiên (2)").assertIsDisplayed()
        compose.onNodeWithText("Nha Cu 2").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("home_002", selectedId)
        }
    }

    @Test
    fun singleHomeMode_memberOnly_showsSpecialDescriptionAndAllowsCreate() {
        var createClicked = false
        val state = CloudUiState(
            status = CloudStatus.EMPTY,
            currentUid = "uMember",
            homes = listOf(CloudHome("home_001", "Nha Chia Se", CloudRole.MEMBER)),
            selectedHomeId = null
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeManagementCard(
                    cloudState = state,
                    onSelectHome = {},
                    onCreateHome = { createClicked = true },
                    onLeaveHome = {},
                    onRemoveMember = {},
                    onRetry = {},
                    singleHomeMode = true
                )
            }
        }

        // Special explanation text is shown
        compose.onNodeWithText(
            "Tài khoản đang chỉ tham gia nhà được chia sẻ; chế độ chia sẻ nhiều thành viên hiện tạm hoãn. Bạn có thể tạo nhà riêng cho ESP32 của mình."
        ).assertIsDisplayed()

        // "Tạo nhà mới" button is present and clickable
        compose.onNodeWithText("Tạo nhà mới").assertIsDisplayed().performClick()
        compose.onNodeWithText("Tạo nhà Cloud mới").assertIsDisplayed()
    }

    @Test
    fun multiHomeMode_bannerAndManagementKeepFullB3Ui() {
        val state = CloudUiState(
            status = CloudStatus.READY,
            currentUid = "uOwner",
            homes = listOf(CloudHome("home_001", "Nha B3", CloudRole.OWNER)),
            selectedHomeId = "home_001",
            membersStatus = MembersStatus.READY,
            members = listOf(CloudMember("uOwner", CloudRole.OWNER, 1000L, "uOwner"))
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeBannerCard(
                    cloudState = state,
                    onSelectHome = {},
                    onCreateHome = {},
                    onRetry = {},
                    singleHomeMode = false
                )
            }
        }

        // Multi-home B3 buttons remain visible
        compose.onNodeWithText("Nhà (1)").assertIsDisplayed()
        compose.onNodeWithText("Thành viên").assertIsDisplayed()
        compose.onNodeWithText("Quản lý nhà").assertDoesNotExist()
    }
}

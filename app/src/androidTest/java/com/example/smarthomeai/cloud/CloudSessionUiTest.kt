package com.example.smarthomeai.cloud

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.ui.components.CloudHomeBannerCard
import com.example.smarthomeai.ui.screens.HomeScreen
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSessionUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bannerCard_whenSignedOutAndReauthRequired_showsReauthNoticeAndButton() {
        var reauthClicked = false
        val state = CloudUiState(
            status = CloudStatus.SIGNED_OUT,
            currentUid = null,
            reauthRequired = true
        )

        compose.setContent {
            SmartHomeTheme {
                CloudHomeBannerCard(
                    cloudState = state,
                    onSelectHome = {},
                    onCreateHome = {},
                    onRetry = {},
                    onOpenManagement = {},
                    singleHomeMode = true,
                    onReauth = { reauthClicked = true }
                )
            }
        }

        compose.onNodeWithText("Phiên đăng nhập Cloud đã kết thúc. Điều khiển LAN vẫn hoạt động.").assertIsDisplayed()
        compose.onNodeWithText("Đăng nhập lại").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(reauthClicked)
        }
    }

    @Test
    fun homeScreen_whenCloudSessionRevoked_navigatesToLoginOnReauthTap_andKeepsLanControls() {
        var navigatedRoute: String? = null
        val signedOutState = MutableStateFlow(
            CloudUiState(
                status = CloudStatus.SIGNED_OUT,
                currentUid = null,
                reauthRequired = true
            )
        )

        val fakeBridge = object : CloudUiBridge {
            override val state: StateFlow<CloudUiState> = signedOutState
            override fun selectHome(homeId: String) = Unit
            override fun createHome(name: String) = Unit
            override fun leaveSelectedHome() = Unit
            override fun removeMember(memberUid: String) = Unit
            override fun retry() = Unit
            override fun dismissFeedback() = Unit
        }

        val lanState = SmartHomeState(
            esp32Online = true,
            lastSeen = System.currentTimeMillis()
        )

        compose.setContent {
            SmartHomeTheme {
                HomeScreen(
                    state = lanState,
                    onSetDevice = { _, _ -> },
                    onMode = {},
                    onRefresh = {},
                    onNavigate = { route -> navigatedRoute = route },
                    cloudBridge = fakeBridge
                )
            }
        }

        // Reauth button is present in the Cloud Home banner on HomeScreen
        compose.onNodeWithText("Đăng nhập lại").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("login", navigatedRoute)
        }

        // ESP32 LAN connection pill remains displayed
        compose.onNodeWithText("ESP32 đã kết nối").assertIsDisplayed()
    }
}

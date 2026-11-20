package com.example.smarthomeai

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import androidx.compose.material3.*
import com.example.smarthomeai.face.FaceEngine
import com.example.smarthomeai.ui.screens.*
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppSmokeTest {
    @Test fun alarmSettingsChangeSleepWithoutChangingAway() {
        var changed:AlarmSettings?=null
        val state=SmartHomeState(esp32Online=true,hardware=HardwareStatus("dht11-leds",alarmSettings=AlarmSettings()))
        compose.setContent {SmartHomeTheme {AlarmSettingsCard(state){changed=it}}}
        compose.onNodeWithContentDescription("Báo động tự động khi Đi ngủ").performClick()
        compose.runOnIdle {assertEquals(AlarmSettings(true,false,60),changed)}
        screenshot("alarm-settings")
    }
    @Test fun alarmTimeoutCanChangeWhileProtectionIsActive() {
        var changed:AlarmSettings?=null
        val state=SmartHomeState(esp32Online=true,security=SecurityStatus(HomeMode.SLEEP,SecurityPhase.ARMED,0,"board",1,0,instantAlarm=true),
            hardware=HardwareStatus("dht11-leds",alarmSettings=AlarmSettings(sleepEnabled=false)))
        compose.setContent {SmartHomeTheme {AlarmSettingsCard(state){changed=it}}}
        compose.onNodeWithText("10s",substring=false).performClick()
        compose.runOnIdle {assertEquals(AlarmSettings(true,false,10),changed)}
    }
    @Test fun alarmSettingsCannotChangeOfflineOrDuringSave() {
        var state by mutableStateOf(SmartHomeState(hardware=HardwareStatus("dht11-leds",alarmSettings=AlarmSettings())))
        compose.setContent {SmartHomeTheme {AlarmSettingsCard(state){fail("Disabled control sent a command")}}}
        compose.onNodeWithContentDescription("Báo động tự động khi Đi ngủ").assertIsNotEnabled()
        compose.onNodeWithText("10s",substring=false).assertIsNotEnabled()
        compose.runOnIdle {state=state.copy(esp32Online=true,alarmSettingsBusy=true)}
        compose.onNodeWithContentDescription("Báo động tự động khi Ra ngoài").assertIsNotEnabled()
    }
    @Test fun oldFirmwareDoesNotShowWorkingAlarmSettingsSwitches() {
        compose.setContent {SmartHomeTheme {AlarmSettingsCard(SmartHomeState(esp32Online=true)){}}}
        compose.onNodeWithText("Cập nhật firmware ESP32 3.5 để điều chỉnh báo động.").assertExists()
        compose.onNodeWithContentDescription("Báo động tự động khi Đi ngủ").assertDoesNotExist()
    }
    @Test fun awayWaitingForDoorLocksManualOpening() {
        val security = SecurityStatus(HomeMode.AWAY, SecurityPhase.EXIT_DELAY, 0, "board", 1, 0,
            exitSeconds = 0, instantAlarm = true, exitBlocked = true, demoModes = true)
        val state = SmartHomeState(esp32Online = true, mode = HomeMode.AWAY, security = security, doorOpen = false)
        compose.setContent { SmartHomeTheme { DoorControlCard(state, { _, _ -> }, {}) } }
        compose.onNodeWithText("Mở cửa", substring = false).assertIsNotEnabled()
    }
    @Test fun liveSensorValuesUpdateWithoutReopeningPage() {
        var state by mutableStateOf(SmartHomeState(esp32Online=true,lastSeen=1,temperature=28,humidity=65,hardware=HardwareStatus("dht11-leds",distanceCm=30f,raining=false,obstacle=false)))
        compose.setContent {SmartHomeTheme {LiveSensorsCard(state)}}
        compose.onNodeWithText("28°C").assertExists()
        compose.runOnIdle {state=state.copy(temperature=36,humidity=85,motionDetected=true,hardware=state.hardware!!.copy(raining=true))}
        compose.onNodeWithText("36°C").assertExists();compose.onNodeWithText("85%").assertExists()
        compose.onNodeWithText("Có mưa",substring=false).assertExists()
        screenshot("live-sensors")
    }
    @Test fun homeModeLeavesNearbyObjectSilentInNewFirmware() {
        val h=HardwareStatus("dht11-leds",distanceCm=8f,securityProfile="instant-20s",alarmOutput=false,proximityAlarmEnabled=false)
        compose.setContent {SmartHomeTheme {AlarmControlCard(SmartHomeState(esp32Online=true,hardware=h),{_,_->})}}
        compose.onNodeWithText("Không có cảnh báo").assertExists()
        compose.onNodeWithText("Thử còi",substring=false).assertIsEnabled()
    }
    @Test fun sleepMonitoringKeepsManualControlsLocked() {
        val security=SecurityStatus(HomeMode.SLEEP,SecurityPhase.ARMED,0,"board",1,0,instantAlarm=true)
        compose.setContent {SmartHomeTheme {DoorControlCard(SmartHomeState(esp32Online=true,security=security),{_,_->},{})}}
        compose.onNodeWithText("Mở cửa",substring=false).assertIsNotEnabled()
        compose.onNodeWithText("Quét mặt để mở cửa").assertIsEnabled()
    }
    @Test fun exitObstructionShowsWaitingInsteadOfContinuingCountdown() {
        val security=SecurityStatus(HomeMode.AWAY,SecurityPhase.EXIT_DELAY,0,"board",1,0,exitSeconds=0,instantAlarm=true,exitBlocked=true,demoModes=true)
        compose.setContent {SmartHomeTheme {SecurityCard(SmartHomeState(esp32Online=true,security=security,mode=HomeMode.AWAY,hardware=HardwareStatus("dht11-leds",obstacle=true)),{})}}
        compose.onNodeWithText("Chưa bật bảo vệ · Có vật cản ở cửa").assertExists()
        screenshot("away-waiting")
    }
    @Test fun sensorsPageOffersLiveChartAndCsv() {
        val now=System.currentTimeMillis()
        val state=SmartHomeState(esp32Online=true,lastSeen=now,temperature=31,humidity=70,
            liveSamples=listOf(SensorSample(now-1000,28,65),SensorSample(now,29,66)))
        compose.setContent {SmartHomeTheme {EnvironmentScreen(state,{},{},mainPage=true)}}
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Trực tiếp",substring=false))
        compose.onNodeWithText("Trực tiếp",substring=false).assertExists()
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Xuất dữ liệu CSV"))
        compose.onNodeWithText("Xuất dữ liệu CSV").assertIsEnabled()
        screenshot("sensors-chart")
    }
    @Test fun awayModeSelectionIsImmediateAndHasNoSleepOption() {
        var selected:HomeMode?=null
        val security=SecurityStatus(HomeMode.HOME,SecurityPhase.DISARMED,0,"board",1,0,instantAlarm=true,demoModes=true)
        compose.setContent {SmartHomeTheme {SecurityCard(SmartHomeState(esp32Online=true,security=security),{selected=it})}}
        compose.onNodeWithText("Đi ngủ",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Ra ngoài",substring=false).performClick()
        compose.runOnIdle {assertEquals(HomeMode.AWAY,selected)}
    }
    @Test fun doorCannotCloseWhileEitherSensorDetectsObstacle() {
        val hardware=HardwareStatus("dht11-leds",obstacle=true,distanceCm=8f)
        compose.setContent { SmartHomeTheme { DoorControlCard(SmartHomeState(esp32Online=true,lastSeen=1,doorOpen=true,hardware=hardware),{_,_->},{}) } }
        compose.onNodeWithText("Đóng cửa",substring=false).assertIsNotEnabled()
        compose.onNodeWithText("Đang giữ cửa mở. Dọn vật cản để cửa tự đóng.").assertExists()
        screenshot("door-waiting")
    }
    @Test fun clearDoorCanBeClosedExplicitly() {
        var command:Pair<DeviceType,Boolean>?=null
        val hardware=HardwareStatus("dht11-leds",obstacle=false,distanceCm=30f)
        compose.setContent { SmartHomeTheme { DoorControlCard(SmartHomeState(esp32Online=true,lastSeen=1,doorOpen=true,hardware=hardware),{type,on->command=type to on},{}) } }
        compose.onNodeWithText("Đóng cửa",substring=false).performClick()
        compose.runOnIdle {assertEquals(DeviceType.DOOR to false,command)}
    }
    @Test fun missingDistanceDoesNotDisplayInventedCountdown() {
        compose.setContent { SmartHomeTheme { DoorControlCard(SmartHomeState(esp32Online=true,lastSeen=1,doorOpen=true,hardware=HardwareStatus("dht11-leds",distanceCm=null)),{_,_->},{}) } }
        compose.onNodeWithText("Tự đóng sau 5 giây không có vật cản.").assertExists()
        compose.onNodeWithText("Còn ",substring=true).assertDoesNotExist()
    }
    @Test fun ultrasonicAlarmIsVisibleEvenWhenManualFlagIsOff() {
        compose.setContent { SmartHomeTheme { AlarmControlCard(SmartHomeState(esp32Online=true,alarm=false,hardware=HardwareStatus("dht11-leds",distanceCm=8f)),{_,_->}) } }
        compose.onNodeWithText("Cảnh báo: vật trong vùng 2–10 cm").assertExists()
        compose.onNodeWithText("Thử còi",substring=false).assertIsNotEnabled()
        screenshot("proximity-alarm")
    }
    @Test fun stoppingManualAlarmKeepsProximityWarningVisible() {
        var state by mutableStateOf(SmartHomeState(esp32Online=true,alarm=true,hardware=HardwareStatus("dht11-leds",distanceCm=8f)))
        compose.setContent { SmartHomeTheme { AlarmControlCard(state,{type,on->assertEquals(DeviceType.ALARM,type);state=state.copy(alarm=on)}) } }
        compose.onNodeWithText("Dừng thử",substring=false).performClick()
        compose.onNodeWithText("Cảnh báo: vật trong vùng 2–10 cm").assertExists()
        compose.onNodeWithText("Thử còi",substring=false).assertIsNotEnabled()
    }
    @Test fun offlineAlarmDoesNotPresentCachedReadingAsLiveWarning() {
        compose.setContent { SmartHomeTheme { AlarmControlCard(SmartHomeState(hardware=HardwareStatus("dht11-leds",distanceCm=8f)),{_,_->}) } }
        compose.onNodeWithText("Chờ kết nối để cập nhật").assertExists()
        compose.onNodeWithText("Cảnh báo: vật trong vùng 2–10 cm").assertDoesNotExist()
        compose.onNodeWithText("Thử còi",substring=false).assertIsNotEnabled()
    }
    @Test fun activityTabShowsDoorClosingFromEsp32Journal() {
        val event=DeviceEvent("board:1",1,"board",1,0,"servo_close",System.currentTimeMillis(),5000)
        compose.setContent { SmartHomeTheme { AlertsScreen(SmartHomeState(deviceEvents=listOf(event)),{},{},{}) } }
        compose.onNodeWithText("Hoạt động",substring=false).performClick()
        compose.onNodeWithText("Cửa chuyển về vị trí đóng").assertExists()
        screenshot("activity-journal")
    }
    @Test fun cameraDoorActionExplainsAutomaticClosing() {
        compose.setContent { SmartHomeTheme { FaceDoorCard(SmartHomeState(esp32Online=true,hardware=HardwareStatus("dht11-leds")),"An"){} } }
        compose.onNodeWithText("Cửa tự đóng sau 5 giây không có vật cản.").assertExists()
        compose.onNodeWithText("Mở cửa",substring=false).assertIsEnabled()
    }
    @Test fun stoppedSirenDoesNotHideUnresolvedSecurityIncident() {
        val security=SecurityStatus(HomeMode.AWAY,SecurityPhase.ALARM,0,"board",1,1)
        compose.setContent {SmartHomeTheme {AlarmControlCard(SmartHomeState(esp32Online=true,security=security,alarm=false,hardware=HardwareStatus("dht11-leds",distanceCm=30f)),{_,_->})}}
        compose.onNodeWithText("Còi đã tự dừng · sự cố chờ xác nhận").assertExists()
        compose.onNodeWithText("Thử còi",substring=false).assertIsNotEnabled()
    }
    @Test fun recognizedFaceOpensDoorInOneTap() {
        var opened:String?=null
        compose.setContent { SmartHomeTheme { FaceDoorCard(SmartHomeState(esp32Online=true),"An"){opened=it} } }
        compose.onNodeWithText("Mở cửa",substring=false).performClick()
        compose.runOnIdle {assertEquals("An",opened)}
        screenshot("face-open-door")
    }
    @Test fun missingFaceNeverShowsOpenDoorAction() {
        compose.setContent { SmartHomeTheme { FaceDoorCard(SmartHomeState(esp32Online=true),null){} } }
        compose.onNodeWithText("Mở cửa",substring=false).assertDoesNotExist()
    }
    @Test fun recognizedFaceCannotOpenWhileOfflineOrAlreadyOpen() {
        var state by mutableStateOf(SmartHomeState())
        compose.setContent { SmartHomeTheme { FaceDoorCard(state,"An"){} } }
        compose.onNodeWithText("Mở cửa",substring=false).assertIsNotEnabled()
        compose.runOnIdle {state=state.copy(esp32Online=true,doorOpen=true)}
        compose.onNodeWithText("Mở cửa",substring=false).assertIsNotEnabled()
    }
    @Test fun recognizedFaceExplainsDisarmingBeforeOpen() {
        val security=SecurityStatus(HomeMode.AWAY,SecurityPhase.ARMED,0,"board",1,0)
        compose.setContent { SmartHomeTheme { FaceDoorCard(SmartHomeState(esp32Online=true,security=security),"An"){} } }
        compose.onNodeWithText("Bấm mở cửa sẽ chuyển sang Ở nhà và tắt bảo vệ.").assertExists()
        compose.onNodeWithText("Mở cửa",substring=false).assertIsEnabled()
    }
    @Test fun clotheslineCanRetractAndEnableRainAutomationWhileAway() {
        var retracted:Boolean?=null;var automatic:Boolean?=null
        val hardware=HardwareStatus("dht11-leds",hasClothesline=true,clothesRetracted=false,rainRetractEnabled=false,raining=false)
        compose.setContent { SmartHomeTheme { ClotheslineCard(SmartHomeState(esp32Online=true,hardware=hardware,
            security=SecurityStatus(HomeMode.AWAY,SecurityPhase.ARMED,0,"board",1,0)),{retracted=it},{automatic=it}) } }
        compose.onNodeWithText("Thu vào",substring=false).performClick()
        compose.onNodeWithContentDescription("Tự thu khi mưa").performClick()
        compose.runOnIdle {assertEquals(true,retracted);assertEquals(true,automatic)}
        screenshot("clothesline-controls")
    }
    @Test fun rainBlocksExtendingButDryWeatherAllowsIt() {
        var hardware by mutableStateOf(HardwareStatus("dht11-leds",hasClothesline=true,clothesRetracted=true,rainRetractEnabled=true,raining=true))
        compose.setContent { SmartHomeTheme { ClotheslineCard(SmartHomeState(esp32Online=true,hardware=hardware),{},{}) } }
        compose.onNodeWithText("Phơi ra",substring=false).assertIsNotEnabled()
        screenshot("clothesline-rain")
        compose.runOnIdle {hardware=hardware.copy(raining=false)}
        compose.onNodeWithText("Phơi ra",substring=false).assertIsEnabled()
    }
    @Test fun clotheslineControlsDisableWhileOffline() {
        val hardware=HardwareStatus("dht11-leds",hasClothesline=true,clothesRetracted=false,rainRetractEnabled=true)
        compose.setContent { SmartHomeTheme { ClotheslineCard(SmartHomeState(hardware=hardware),{},{}) } }
        compose.onNodeWithText("Thu vào",substring=false).assertIsNotEnabled()
        compose.onNodeWithText("Phơi ra",substring=false).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Tự thu khi mưa").assertIsNotEnabled()
    }
    @Test fun homeExposesClotheslineControlsAlongsideDevices() {
        var retracted:Boolean?=null
        val hardware=HardwareStatus("dht11-leds",hasBuzzer=false,authRequired=false,hasClothesline=true,clothesRetracted=false,rainRetractEnabled=true,raining=false)
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(esp32Online=true,hardware=hardware,lastSeen=System.currentTimeMillis()),{_,_->},{},{},{},{retracted=it},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Thu vào",substring=false))
        compose.onNodeWithText("Thu vào",substring=false).performClick()
        compose.runOnIdle {assertEquals(true,retracted)}
        screenshot("home-clothesline")
    }
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"qa-modes-34").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun homeMakesDisconnectedStateExplicit() {
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(),{_,_->},{},{},{}) } }
        screenshot("home-overview")
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("ESP32 mất kết nối"))
        compose.onNodeWithText("ESP32 mất kết nối").assertExists()
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Xem tất cả cảm biến"))
        screenshot("home-offline")
    }
    @Test fun modeSelectionCallsAction() {
        var selected: HomeMode? = null
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(esp32Online=true,lastSeen=System.currentTimeMillis(),temperature=28,humidity=65,security=SecurityStatus(HomeMode.HOME,SecurityPhase.DISARMED,0,"test-board",1,0,instantAlarm=true,demoModes=true)),{_,_->},{selected=it},{},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Ra ngoài"))
        compose.onNodeWithText("Ra ngoài").performClick()
        compose.runOnIdle { assertEquals(HomeMode.AWAY,selected) }
        screenshot("home-online")
    }
    @Test fun deviceControlsDisabledWhenOffline() {
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(),{_,_->},{},{},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đèn phòng khách"))
        compose.onAllNodes(isToggleable()).assertAll(isNotEnabled())
        screenshot("devices-offline")
    }
    @Test fun alertCanBeReviewed() {
        compose.setContent {
            var state by remember { mutableStateOf(SmartHomeState(alerts=listOf(AlertItem(AlertType.STRANGER,"Sự kiện kiểm thử",nowString())))) }
            SmartHomeTheme { AlertsScreen(state,{id->state=state.copy(alerts=state.alerts.map { if(it.id==id) it.copy(reviewed=true) else it })},{},{}) }
        }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đã kiểm tra"))
        compose.onNodeWithText("Đã kiểm tra", substring = false).performClick()
        compose.onNodeWithText("Đánh dấu tất cả").assertIsNotEnabled()
        screenshot("alerts")
    }
    @Test fun emptyChartExplainsDataRequirement() {
        compose.setContent { SmartHomeTheme { EnvironmentScreen(SmartHomeState(),{},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đang chờ dữ liệu trực tiếp. Cập nhật khoảng mỗi giây."))
        compose.onNodeWithText("Xuất dữ liệu CSV").assertIsNotEnabled()
        screenshot("analytics-empty")
    }
    @Test fun weekRangeShowsOlderSamples() {
        val now = System.currentTimeMillis()
        val samples = listOf(SensorSample(now - 3 * 86_400_000L, 24, 60), SensorSample(now - 60_000, 30, 70))
        compose.setContent { SmartHomeTheme { EnvironmentScreen(SmartHomeState(samples=samples),{},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("7 ngày"))
        compose.onNodeWithText("7 ngày").performClick()
        compose.onNodeWithText("24°C").assertExists()
        compose.onNodeWithText("27.0°C").assertExists()
        compose.onNodeWithText("Xuất dữ liệu CSV").assertIsEnabled()
        screenshot("analytics-week")
    }
    @Test fun environmentConfigurationCallsSave() {
        var saved = false
        compose.setContent { SmartHomeTheme { EnvironmentScreen(SmartHomeState(environmentAlerts=false),{},{},{enabled,temp,hum,cold -> saved = enabled && temp == 35 && hum == 80 && cold == 18}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Cảnh báo môi trường"))
        compose.onNodeWithText("Cảnh báo môi trường").performClick()
        compose.onNode(isToggleable()).performClick()
        screenshot("environment-config")
        compose.onNodeWithText("Lưu thiết lập").performClick()
        compose.runOnIdle { assertTrue(saved) }
    }
    @Test fun systemReadinessReflectsDisconnectedSensors() {
        compose.setContent { SmartHomeTheme { SystemScreen(SmartHomeState(),false,{},{},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Chưa nhận đủ số đo từ ESP32"))
        compose.onNodeWithText("Chưa nhận đủ số đo từ ESP32").assertExists()
        screenshot("system-readiness")
    }
    @Test fun environmentalAlertFilterHidesOtherTypes() {
        val alerts = listOf(AlertItem(AlertType.ENVIRONMENT,"Nhiệt độ cao: 36°C",nowString()), AlertItem(AlertType.STRANGER,"Người chưa đăng ký",nowString()))
        compose.setContent { SmartHomeTheme { AlertsScreen(SmartHomeState(alerts=alerts),{},{},{}) } }
        compose.onNodeWithText("Mọi loại").performClick()
        compose.onNodeWithText("Môi trường").performClick()
        compose.onNodeWithText("Nhiệt độ cao: 36°C").assertExists()
        compose.onNodeWithText("Người chưa đăng ký").assertDoesNotExist()
        screenshot("alerts-environment")
    }
    @Test fun notificationOpensTheMatchingJournalEvent() {
        val recent = AlertItem(AlertType.ENVIRONMENT, "Trời bắt đầu mưa", nowString())
        val target = AlertItem(AlertType.ENVIRONMENT, "Phòng quá lạnh · 18°C", nowString())
        compose.setContent {
            SmartHomeTheme {
                AlertsScreen(SmartHomeState(alerts = listOf(recent, target)), {}, {}, {}, target.id)
            }
        }
        compose.onNodeWithText("Sự kiện từ thông báo").assertIsDisplayed()
        compose.onNodeWithText(target.title).assertIsDisplayed()
    }
    @Test fun allThreeDevicesAreVisibleAndCanBeControlledFromHome() {
        val calls = mutableListOf<Pair<DeviceType, Boolean>>()
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(esp32Online=true,lastSeen=System.currentTimeMillis(),temperature=28,humidity=65,security=SecurityStatus(HomeMode.HOME,SecurityPhase.DISARMED,0,"test-board",1,0)),{type,on->calls += type to on},{},{},{}) } }
        DeviceType.entries.forEach { type ->
            compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(type.label))
            compose.onNodeWithContentDescription(type.label).assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(DeviceType.entries.map { it to true }.toSet(), calls.toSet()) }
        screenshot("home-controls")
    }
    @Test fun primaryNavigationMatchesWireframeAndSensorsAreReachableFromHome() {
        var destination = ""
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(),{_,_->},{},{},{destination=it}) } }
        compose.onNodeWithText("Trang chủ", substring=false).assertIsDisplayed()
        compose.onNodeWithText("Khuôn mặt", substring=false).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("camera", destination) }
        compose.onNodeWithText("Cài đặt", substring=false).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("settings", destination) }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Xem tất cả cảm biến"))
        compose.onNodeWithText("Xem tất cả cảm biến").performClick()
        compose.runOnIdle { assertEquals("env", destination) }
        compose.onNodeWithText("Nhật ký", substring=false).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("alerts", destination) }
    }
    @Test fun automationKeepsRulesAndHasBackNavigation() {
        var back = false
        var destination=""
        compose.setContent { SmartHomeTheme { AutomationScreen(SmartHomeState(esp32Online=true,rulesSupported=true),{_,_->},{},{destination=it},{back=true}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Ngưỡng cảnh báo cảm biến"))
        compose.onNodeWithText("Ngưỡng cảnh báo cảm biến").performClick()
        compose.runOnIdle { assertEquals("sensors",destination) }
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.runOnIdle { assertTrue(back) }
        screenshot("automation")
    }
    @Test fun settingsStillAllowsIpEditingAndMemberRemoval() {
        var savedIp = ""
        var removed = ""
        compose.setContent { SmartHomeTheme { SettingsScreen(SmartHomeState(registeredUsers=listOf("An")),{savedIp=it},{},{},{removed=it},{},{},{},{}) } }
        compose.onNodeWithText("Địa chỉ ESP32").performClick()
        compose.onNodeWithText("Địa chỉ IPv4").performTextReplacement("192.168.1.77")
        compose.onNodeWithText("Lưu & kết nối").performClick()
        compose.runOnIdle { assertEquals("192.168.1.77", savedIp) }
        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription("Xóa khuôn mặt An"))
        compose.onNodeWithContentDescription("Xóa khuôn mặt An").performClick()
        compose.onNodeWithText("Xóa", substring=false).performClick()
        compose.runOnIdle { assertEquals("An", removed) }
        screenshot("settings")
    }
    @Test fun loginIsCompactAndKeepsDemoDisclosure() {
        compose.setContent { SmartHomeTheme { LoginScreen(true,{_,_,_,_->},{},{}) } }
        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Mật khẩu", substring=false).assertIsDisplayed()
        compose.onNodeWithText("Bản trình diễn tài khoản:",substring=true).assertExists()
        screenshot("login")
    }
    @Test fun cameraStartsIdleAndOffersFirstRegistration() {
        compose.setContent { SmartHomeTheme { CameraScreen(SmartHomeState(),{_,_,_->},{},{}) } }
        compose.onNodeWithText("Bắt đầu quét").assertExists()
        compose.onNodeWithText("Dừng quét").assertDoesNotExist()
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Thêm thành viên đầu tiên"))
        screenshot("camera")
    }
    @Test fun armedDevicesRequireDisarmingFirst() {
        val security=SecurityStatus(HomeMode.AWAY,SecurityPhase.ARMED,0,"test-board",1,0)
        compose.setContent { SmartHomeTheme { HomeScreen(SmartHomeState(esp32Online=true,mode=HomeMode.AWAY,security=security,lastSeen=System.currentTimeMillis()),{_,_->},{},{},{}) } }
        DeviceType.entries.forEach {
            compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription(it.label))
            compose.onNodeWithContentDescription(it.label).assertIsNotEnabled()
        }
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Ở nhà", substring=false))
        compose.onNodeWithText("Ở nhà", substring=false).assertIsEnabled()
        screenshot("home-armed")
    }
    @Test fun activeAlarmCanBeAcknowledgedFromHomeMode() {
        val security = SecurityStatus(HomeMode.AWAY, SecurityPhase.ALARM, 0, "test-board", 1, 1,
            instantAlarm = true, demoModes = true)
        var requested: HomeMode? = null
        val state = SmartHomeState(esp32Online = true, mode = HomeMode.AWAY, security = security, alarm = true)
        compose.setContent { SmartHomeTheme { SecurityCard(state) { requested = it } } }
        compose.onNodeWithText("Đang báo động").assertExists()
        compose.onNodeWithText("Ở nhà", substring = false).performClick()
        compose.runOnIdle { assertEquals(HomeMode.HOME, requested) }
    }
    @Test fun alarmNoticeIsVisibleOnOtherPages() {
        val state=SmartHomeState(esp32Online=true,security=SecurityStatus(HomeMode.AWAY,SecurityPhase.ALARM,0,"test-board",1,1))
        var ack=false
        compose.setContent { SmartHomeTheme { CompositionLocalProvider(LocalSafetyBanner provides SafetyBanner(state){ack=true}) { AppPage("Thống kê","","env",{}) {} } } }
        compose.onNodeWithText("Có sự cố cần xử lý").assertExists()
        compose.onNodeWithText("Tôi đây · tắt bảo vệ").performClick()
        compose.runOnIdle { assertTrue(ack) }
    }
    @Test fun incidentTimelineShowsDeviceCausality() {
        val events=listOf(DeviceEvent("board:1",1,"board",1,1,"motion_entry",0,1000),DeviceEvent("board:2",2,"board",1,1,"alarm_started",0,21000))
        val state=SmartHomeState(deviceEvents=events,alerts=mergeIncidentAlerts(emptyList(),events,events))
        compose.setContent { SmartHomeTheme { AlertsScreen(state,{},{},{}) } }
        compose.onNodeWithText("Xem diễn biến").performClick()
        compose.onNodeWithText("PIR phát hiện chuyển động · bắt đầu chờ xác nhận").assertExists()
        screenshot("incident-timeline")
    }
    @Test fun ownerKeyFallbackRejectsWrongKey() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        org.junit.Assume.assumeFalse(manager.isDeviceSecure)
        var action=""
        compose.setContent { SmartHomeTheme {
            val authorize=rememberOwnerConfirmation("test-key-123456"){action=it}
            Button(onClick={authorize("DOOR")}){Text("Mở cửa")}
        } }
        compose.onNodeWithText("Mở cửa").performClick()
        compose.onNodeWithText("Khóa ESP32").performTextReplacement("wrong-key")
        compose.onNodeWithText("Xác nhận",substring=false).assertIsNotEnabled()
        compose.onNodeWithText("Khóa ESP32").performTextReplacement("test-key-123456")
        compose.onNodeWithText("Xác nhận",substring=false).performClick()
        compose.runOnIdle { assertEquals("DOOR",action) }
    }
    @Test fun exitPeriodIsShownAsTwentySecondsInsteadOfOldTimingEditor() {
        val state=SmartHomeState(esp32Online=true,rulesSupported=true,security=SecurityStatus(HomeMode.HOME,SecurityPhase.DISARMED,0,"test-board",1,0))
        var values=listOf<Int>()
        compose.setContent { SmartHomeTheme { AutomationScreen(state,{_,_->},{},{},{},{a,b,c->values=listOf(a,b,c)}) } }
        compose.onNodeWithText("Chờ 20 giây rồi tắt đèn, đóng cửa và bật giám sát.").assertExists()
        compose.onNodeWithText("Thời gian tự động").assertDoesNotExist()
        compose.runOnIdle { assertTrue(values.isEmpty()) }
    }
    @Test fun documentHardwareShowsCorrectLedsAndExtraReadings() {
        val hardware=HardwareStatus("dht11-leds",authRequired=false,hasBuzzer=false,obstacle=false,raining=true,distanceCm=12.5f)
        val state=SmartHomeState(esp32Online=true,lastSeen=System.currentTimeMillis(),hardware=hardware,temperature=29,humidity=65,security=SecurityStatus(HomeMode.HOME,SecurityPhase.DISARMED,0,"test-board",1,0))
        compose.setContent { SmartHomeTheme { HomeScreen(state,{_,_->},{},{},{}) } }
        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription("LED xanh"))
        compose.onNodeWithContentDescription("LED xanh").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("page-list").performScrollToNode(hasContentDescription("LED vàng báo động"))
        compose.onNodeWithContentDescription("LED vàng báo động").assertExists()
        compose.onNodeWithText("Còi cảnh báo").assertDoesNotExist()
        compose.onNodeWithTag("page-list").performScrollToNode(hasText("Đang mưa"))
        compose.onNodeWithText("Đang mưa").assertExists()
        screenshot("document-hardware")
    }
    @Test fun ipOnlyHardwareCanConfirmWithoutDeviceKey() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        org.junit.Assume.assumeFalse(manager.isDeviceSecure)
        var action=""
        compose.setContent { SmartHomeTheme {
            val authorize=rememberOwnerConfirmation("",true){action=it}
            Button(onClick={authorize("DOOR")}){Text("Mở servo")}
        } }
        compose.onNodeWithText("Mở servo").performClick()
        compose.onNodeWithText("Xác nhận thao tác").assertExists()
        compose.onNodeWithText("Khóa ESP32",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Xác nhận",substring=false).performClick()
        compose.runOnIdle { assertEquals("DOOR",action) }
    }
    @Test fun keyedHardwareDoesNotAllowBlankKeyConfirmation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        org.junit.Assume.assumeFalse(manager.isDeviceSecure)
        compose.setContent { SmartHomeTheme {
            val authorize=rememberOwnerConfirmation("",false){}
            Button(onClick={authorize("DOOR")}){Text("Mở servo")}
        } }
        compose.onNodeWithText("Mở servo").performClick()
        compose.onNodeWithText("Xác nhận",substring=false).assertIsNotEnabled()
    }
    @Test fun tensorflowModelLoadsAndExecutes() {
        val engine=FaceEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertTrue("Bundled TFLite model did not load",engine.modelReady)
            val bitmap=Bitmap.createBitmap(112,112,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
            val result=engine.encodeFace(bitmap)
            assertNotNull(result)
            assertEquals(192,result!!.size)
            assertTrue(result.all { it.isFinite() })
            assertEquals(1.0,result.sumOf { it.toDouble()*it.toDouble() },.001)
        } finally { engine.close() }
    }
}

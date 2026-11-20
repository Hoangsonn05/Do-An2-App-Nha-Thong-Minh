package com.example.smarthomeai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.theme.*

private val mainPages = setOf("home", "camera", "alerts", "settings")

@Composable
fun AppPage(title: String, subtitle: String, current: String, onNavigate: (String) -> Unit,
            onBack: (() -> Unit)? = null, unreadAlerts: Int = 0, listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Scaffold(containerColor = Bg, topBar = {
        Column {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if(onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Column(Modifier.weight(1f).padding(start = if(onBack == null) 8.dp else 0.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if(subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
        }
        val safety = LocalSafetyBanner.current
        if(current != "home" && safety?.state?.security?.needsAttention == true) {
            Box(Modifier.padding(horizontal=16.dp,vertical=4.dp)) { SafetyNotice(safety.state,safety.acknowledge) }
        }
        }
    }, bottomBar = { if(current in mainPages) SmartBottomBar(current, onNavigate, unreadAlerts) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).testTag("page-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), state = listState, content = content)
    }
}

@Composable
fun GradientTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Bg).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if(onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = TextDark, modifier = Modifier.weight(1f))
        if(action != null) TextButton(onClick = onAction) { Text(action, fontSize = 12.sp) }
    }
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = CardWhite, border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, bg: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .50f))
    }
}

@Composable
fun SmartSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, label: String? = null) {
    Switch(checked, onChange, enabled = enabled, modifier = if(label == null) Modifier else Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(checkedTrackColor = Blue600, checkedThumbColor = Color.White))
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SmartBottomBar(current: String, onNavigate: (String) -> Unit, unreadAlerts: Int = 0) {
    val items = listOf(Triple("home", "Trang chủ", Icons.Default.Home), Triple("camera", "Khuôn mặt", Icons.Default.CenterFocusStrong),
        Triple("alerts", "Nhật ký", Icons.Default.NotificationsNone), Triple("settings", "Cài đặt", Icons.Default.Settings))
    NavigationBar(containerColor = CardWhite, tonalElevation = 0.dp) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(selected = current == route, onClick = { onNavigate(route) }, icon = {
                if(route == "alerts" && unreadAlerts > 0) BadgedBox(badge = { Badge { Text(if(unreadAlerts > 99) "99+" else "$unreadAlerts") } }) { Icon(icon, null) }
                else Icon(icon, null)
            }, label = { Text(label, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Blue50, selectedIconColor = Blue600,
                    selectedTextColor = Blue600, unselectedIconColor = TextGray, unselectedTextColor = TextGray))
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inbox, null, tint = TextGray, modifier = Modifier.size(28.dp))
        Text(text, color = TextGray, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun StatusPill(text: String, color: Color = Blue600, background: Color = Blue50) {
    Row(Modifier.clip(CircleShape).background(background).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(text, fontSize = 11.sp, color = color, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun ConnectionCard(state: SmartHomeState, onRefresh: () -> Unit, onSetup: (() -> Unit)? = null) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if(state.esp32Online) Icons.Default.Wifi else Icons.Default.WifiOff, null,
                tint = if(state.esp32Online) Blue600 else OrangeWarn, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(if(state.esp32Online) "ESP32 đang kết nối" else "ESP32 chưa kết nối", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(if(state.lastSeen == 0L) "Chưa có dữ liệu cảm biến" else if(state.esp32Online) "Cập nhật ${timeOnly(state.lastSeen)}" else "Số liệu cũ · ${nowString(state.lastSeen)}",
                    style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            if(!state.esp32Online && onSetup != null) TextButton(onClick = onSetup) { Text("Kết nối") }
            else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Kết nối lại", tint = Blue600) }
        }
    }
}

@Composable
fun DeviceControlRow(state: SmartHomeState, type: DeviceType, onSet: (DeviceType, Boolean) -> Unit) {
    val on = state.deviceOn(type)
    val command = state.commands[type]
    val busy = command?.phase == CommandPhase.SENDING
    val icon = when(type) { DeviceType.LIGHT -> Icons.Default.Lightbulb; DeviceType.DOOR -> Icons.Default.MeetingRoom; DeviceType.ALARM -> if(state.hardware?.hasBuzzer == false) Icons.Default.WarningAmber else Icons.Default.NotificationsActive }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if(on && state.lastSeen != 0L) Blue600 else TextGray, if(on && state.lastSeen != 0L) Blue50 else OffGray, 36.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(state.deviceLabel(type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val status = when { state.lastSeen == 0L -> "Chưa có trạng thái"; !state.esp32Online -> "Lần cuối: ${if(on) "bật / mở" else "tắt / đóng"}";
                    type == DeviceType.DOOR -> if(on) "Servo: vị trí mở" else "Servo: vị trí đóng"; else -> if(on) "Đang bật" else "Đang tắt" }
                Text(if(busy) "Đang chờ xác nhận…" else status, style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            SmartSwitch(on, { onSet(type, it) }, state.esp32Online && state.mode != HomeMode.AWAY && state.security?.protecting != true && !busy && !state.modeBusy && !state.ruleBusy, state.deviceLabel(type))
        }
        if(type == DeviceType.LIGHT && state.esp32Online && on && state.security?.lightReason in setOf("sleep_motion","night_motion")) {
            Text("Đèn tự bật vì chuyển động; tự tắt sau ${state.security?.holdSeconds}s yên tĩnh.",style=MaterialTheme.typography.bodySmall,color=Blue600)
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(command?.phase == CommandPhase.ERROR) Text(command.message, style = MaterialTheme.typography.bodySmall, color = RedAlert, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
fun SettingsLink(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = CardWhite, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextGray, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if(subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextGray, modifier = Modifier.size(20.dp))
        }
    }
}

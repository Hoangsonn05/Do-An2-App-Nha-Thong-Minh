package com.example.smarthomeai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.smarthomeai.cloud.CloudUiBridge
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun SettingsScreen(state: SmartHomeState, onIpChange: (String) -> Unit, onAutoAlarm: (Boolean) -> Unit,
                   onNotify: (Boolean) -> Unit, onRemoveFace: (String) -> Unit, onLogout: () -> Unit,
                   onRefresh: () -> Unit, onBack: () -> Unit, onNavigate: (String) -> Unit,
                   onConnection: (String, String) -> Unit = { ip, _ -> onIpChange(ip) },
                   onEnvironmentConfig: (Boolean, Int, Int, Int) -> Unit = { _, _, _, _ -> },
                   onDoorReminder: (Boolean) -> Unit = {},
                   cloudBridge: CloudUiBridge? = rememberActiveCloudBridge()) {
    var showIp by rememberSaveable { mutableStateOf(false) }
    var ip by rememberSaveable(state.esp32Ip) { mutableStateOf(state.esp32Ip) }
    var deviceKey by rememberSaveable(state.deviceKey) { mutableStateOf(state.deviceKey) }
    var deleteName by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var showEnvironment by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    AppPage("Cài đặt", "", "settings", onNavigate) {
        item {
            SectionTitle("Kết nối")
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if(state.esp32Online) "ESP32 đang kết nối" else "ESP32 chưa kết nối", style = MaterialTheme.typography.bodyMedium,
                        color = if(state.esp32Online) Blue600 else OrangeWarn, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Kết nối lại", tint = Blue600) }
                }
                SettingsLink("Địa chỉ ESP32", Icons.Default.Wifi, state.esp32Ip) { showIp = true }
                if(!state.esp32Online) Text(state.connectionMessage ?: "Điện thoại và ESP32 cần cùng mạng Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
        }
        item {
            SectionTitle("Bảo vệ & thông báo")
            WhiteCard(Modifier.fillMaxWidth()) {
                Preference("Thông báo trên điện thoại", "Báo sự kiện mới khi app nhận dữ liệu qua Wi-Fi.", state.notifyEnabled, onNotify)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Outline)
                SettingsLink("Cảnh báo môi trường", Icons.Default.Thermostat,
                    if (state.environmentAlerts) "${state.coldLimit}–${state.temperatureLimit}°C" else "Đang tắt") {
                    showEnvironment = true
                }
                if (state.esp32Online && state.rulesSupported) {
                    Preference("Cảnh báo cửa mở quá lâu", "Báo khi cửa mở quá 2 phút.",
                        state.doorReminderEnabled, onDoorReminder)
                }
            }
        }
        if (cloudBridge != null) {
            item { CloudHomeManagementFromBridge(cloudBridge) }
        }
        item {
            SectionTitle("Thành viên", action = "Thêm") { onNavigate("register") }
            WhiteCard(Modifier.fillMaxWidth()) {
                if(state.registeredUsers.isEmpty()) Text("Chưa có thành viên đã đăng ký.", style = MaterialTheme.typography.bodyMedium, color = TextGray)
                state.registeredUsers.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Face, null, tint = TextGray, modifier = Modifier.size(22.dp))
                        Text(name, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { deleteName = name }) { Icon(Icons.Default.DeleteOutline, "Xóa khuôn mặt $name", tint = TextGray) }
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                SettingsLink("Thông tin hệ thống", Icons.Default.Info) { onNavigate("system") }
                HorizontalDivider(color = Outline)
                SettingsLink("Về ứng dụng", Icons.Default.PrivacyTip, "Smart Home · 3.6") { showInfo = true }
            }
        }
        item {
            Text(state.userEmail ?: "Chưa đăng nhập", style = MaterialTheme.typography.bodySmall, color = TextGray)
            TextButton(onClick = { confirmLogout = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Đăng xuất")
            }
        }
    }
    if (showEnvironment) EnvironmentConfigDialog(state, { showEnvironment = false }) { enabled, hot, humidity, cold ->
        onEnvironmentConfig(enabled, hot, humidity, cold)
        showEnvironment = false
    }
    if(showIp) AlertDialog(onDismissRequest = { showIp = false }, title = { Text("Kết nối ESP32") },
        text = { Column {
            OutlinedTextField(ip, { ip = it }, label = { Text("Địa chỉ IPv4") }, singleLine = true,
                isError = ip.isNotBlank() && !Esp32Client.validIp(ip.trim()), supportingText = { Text("Ví dụ: 192.168.1.50 · cùng Wi-Fi") })
            OutlinedTextField(deviceKey,{deviceKey=it},label={Text("Khóa ESP32 (tùy chọn)")},singleLine=true,
                visualTransformation=PasswordVisualTransformation(),
                isError=deviceKey.isNotEmpty() && !Esp32Client.validKey(deviceKey),
                supportingText={Text("Bản firmware theo bảng chân: để trống, chỉ nhập IP. Chỉ điền nếu bạn tự bật DEVICE_KEY trong firmware.")})
        } },
        confirmButton = { TextButton(onClick = { onConnection(ip.trim(),deviceKey); showIp = false }, enabled = Esp32Client.validIp(ip.trim()) && (deviceKey.isEmpty() || Esp32Client.validKey(deviceKey)) && !state.modeBusy && !state.ruleBusy && !state.clothesBusy && state.commands.values.none { it.phase == CommandPhase.SENDING }) { Text("Lưu & kết nối") } },
        dismissButton = { TextButton(onClick = { showIp = false }) { Text("Hủy") } })
    deleteName?.let { name -> AlertDialog(onDismissRequest = { deleteName = null }, title = { Text("Xóa khuôn mặt?") },
        text = { Text("$name sẽ không còn được nhận diện là thành viên đã đăng ký.") },
        confirmButton = { TextButton(onClick = { onRemoveFace(name); deleteName = null }) { Text("Xóa") } },
        dismissButton = { TextButton(onClick = { deleteName = null }) { Text("Hủy") } }) }
    if(confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("Đăng xuất?") },
        text = { Text("App sẽ dừng đọc cảm biến. Quy tắc trên ESP32 vẫn hoạt động.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Đăng xuất") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Hủy") } })
    if(showInfo) AlertDialog(onDismissRequest = { showInfo = false }, title = { Text("Smart Home · 3.6") },
        text = { Text("Khuôn mặt được xử lý trên điện thoại. Dữ liệu thiết bị và nhật ký lưu chung trên máy, chưa phân quyền theo tài khoản.\n\nESP32 v3 tự bảo vệ và lưu 64 sự kiện gần nhất khi app đóng. App lưu 100 cảnh báo và 7 ngày số đo. Camera cần mở để nhận diện. Điều khiển và đồng bộ dùng chung LAN; chưa có thông báo cloud từ xa.") },
        confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Đóng") } })
}

@Composable
private fun Preference(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
        SmartSwitch(checked, onChange, label = title)
    }
}

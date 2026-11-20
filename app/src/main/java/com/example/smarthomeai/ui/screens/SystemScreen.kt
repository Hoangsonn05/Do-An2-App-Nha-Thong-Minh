package com.example.smarthomeai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun SystemScreen(state: SmartHomeState, firebaseConfigured: Boolean, onRefresh: () -> Unit,
                 onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val camera = remember(revision) { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    val notificationPermission = remember(revision) {
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val modelPresent = remember { runCatching { context.assets.openFd("mobile_face_net.tflite").use { it.length > 0 } }.getOrDefault(false) }
    val sensors = state.esp32Online && state.temperature != null && state.humidity != null
    AppPage("Thông tin hệ thống", "", "system", onNavigate, onBack) {
        item { ConnectionCard(state, onRefresh) }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                DiagnosticRow("Cảm biến DHT22", if(sensors) "Đã nhận nhiệt độ và độ ẩm" else "Chưa nhận đủ số đo từ ESP32", sensors)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Outline)
                DiagnosticRow("Quyền camera", if(camera) "Đã được cấp quyền" else "Mở Camera để cấp quyền truy cập", camera)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Outline)
                DiagnosticRow("Tệp mô hình AI", if(modelPresent) "MobileFaceNet có trong ứng dụng" else "Không tìm thấy tệp mô hình", modelPresent)
                Text("Tình trạng chạy mô hình được hiển thị khi mở Camera & AI.", style = MaterialTheme.typography.bodySmall, color = TextGray)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Outline)
                DiagnosticRow("Thành viên", "${state.registeredUsers.size} khuôn mặt đã đăng ký", state.registeredUsers.isNotEmpty())
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Outline)
                DiagnosticRow("Thông báo", if(notificationPermission && state.notifyEnabled) "Đang được cho phép" else "Đang tắt trong app hoặc hệ thống", notificationPermission && state.notifyEnabled)
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                SectionTitle("Kết nối & tự động hóa")
                DetailLine("Địa chỉ ESP32", state.esp32Ip)
                DetailLine("Quy tắc firmware", if(!state.esp32Online) "Chờ kết nối" else if(state.rulesSupported) "Hỗ trợ" else "Cần firmware v2")
                DetailLine("Đồng bộ giờ ESP32", if(!state.esp32Online) "Chưa xác định" else if(state.clockReady) "Đã đồng bộ" else "Đang chờ NTP")
                if(state.hardware?.usesDoorRules==true) {
                    DetailLine("Cửa tự đóng", "Sau 5 giây không có vật cản")
                    DetailLine("Cảnh báo khoảng cách", "Vật trong vùng 2–10 cm")
                }
                DetailLine("Tài khoản", if(firebaseConfigured) "Firebase Authentication" else "Chế độ tài khoản demo")
                Text("Ứng dụng → HTTP trong LAN → ESP32 → cảm biến và thiết bị. Nhận diện khuôn mặt chạy trên điện thoại.",
                    color = TextGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) {
                Text("Quản lý quyền ứng dụng")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(title: String, detail: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if(ready) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if(ready) GreenOk else OrangeWarn, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextGray)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

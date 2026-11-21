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
import com.example.smarthomeai.cloud.CloudDeviceUiBridge
import com.example.smarthomeai.cloud.CloudStatus
import com.example.smarthomeai.cloud.CloudUiBridge
import com.example.smarthomeai.cloud.CloudUiState
import com.example.smarthomeai.cloud.GatewaySyncPhase
import com.example.smarthomeai.cloud.GatewaySyncUiState
import com.example.smarthomeai.cloud.rememberActiveCloudDeviceBridge
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun SystemScreen(
    state: SmartHomeState,
    firebaseConfigured: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    cloudBridge: CloudUiBridge? = rememberActiveCloudBridge(),
    deviceBridge: CloudDeviceUiBridge? = rememberActiveCloudDeviceBridge()
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cloudState = if (cloudBridge != null) {
        cloudBridge.state.collectAsState().value
    } else {
        null
    }
    val gatewaySyncState = if (deviceBridge != null) {
        deviceBridge.gatewaySync.collectAsState().value
    } else {
        null
    }
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
                DetailLine("Nhà Cloud", systemCloudHomeStatusText(firebaseConfigured, cloudState))
                DetailLine("Đồng bộ Gateway", systemGatewaySyncStatusText(gatewaySyncState))
                Text("Điều khiển: Ứng dụng → HTTP LAN → ESP32. Dự phòng xem từ xa: Firebase RTDB (1 nhà). Khuôn mặt xử lý trên điện thoại.",
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

internal fun systemCloudHomeStatusText(
    firebaseConfigured: Boolean,
    cloudState: CloudUiState?
): String {
    if (cloudState == null) {
        return if (!firebaseConfigured) "Chế độ demo" else "Không có cầu nối Cloud"
    }
    return when (cloudState.status) {
        CloudStatus.UNAVAILABLE -> if (!firebaseConfigured) "Chế độ demo" else "Cloud chưa khả dụng"
        CloudStatus.SIGNED_OUT -> "Chưa đăng nhập"
        CloudStatus.LOADING -> "Đang tải…"
        CloudStatus.EMPTY -> "Chưa tạo nhà"
        CloudStatus.READY -> (cloudState.selectedHome ?: cloudState.ownedHomes.firstOrNull())?.name ?: "Đã kết nối"
        CloudStatus.ACCESS_REVOKED -> "Mất quyền truy cập nhà"
        CloudStatus.ERROR -> cloudState.errorMessage?.takeIf { it.isNotBlank() } ?: "Lỗi kết nối Cloud"
    }
}

internal fun systemGatewaySyncStatusText(
    syncState: GatewaySyncUiState?
): String {
    if (syncState == null) return "Chưa kích hoạt"
    return when (syncState.phase) {
        GatewaySyncPhase.IDLE -> "Chưa kích hoạt"
        GatewaySyncPhase.WAITING_HOME -> "Chờ chọn nhà Cloud"
        GatewaySyncPhase.REGISTERING -> "Đang đăng ký thiết bị"
        GatewaySyncPhase.SYNCING -> "Đang đồng bộ"
        GatewaySyncPhase.OK -> syncState.lastSyncAt?.takeIf { it > 0L }?.let { "OK · ${timeOnly(it)}" } ?: "OK"
        GatewaySyncPhase.PAUSED_LAN_OFFLINE -> "Tạm dừng khi mất LAN"
        GatewaySyncPhase.BLOCKED -> "Bị thu hồi"
        GatewaySyncPhase.ERROR -> syncState.errorMessage?.takeIf { it.isNotBlank() }?.let { "Lỗi · $it" } ?: "Lỗi"
    }
}

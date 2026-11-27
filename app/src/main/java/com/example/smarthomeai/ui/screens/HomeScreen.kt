package com.example.smarthomeai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.cloud.CloudDeviceUiBridge
import com.example.smarthomeai.cloud.CloudUiBridge
import com.example.smarthomeai.cloud.DataSource
import com.example.smarthomeai.cloud.DataSourceResolver
import com.example.smarthomeai.cloud.rememberActiveCloudDeviceBridge
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(state: SmartHomeState, onSetDevice: (DeviceType, Boolean) -> Unit,
               onMode: (HomeMode) -> Unit, onRefresh: () -> Unit, onNavigate: (String) -> Unit,
               onClothesline: (Boolean) -> Unit = {}, onRainRetraction: (Boolean) -> Unit = {},
               cloudBridge: CloudUiBridge? = rememberActiveCloudBridge(),
               deviceBridge: CloudDeviceUiBridge? = rememberActiveCloudDeviceBridge(),
               onWindow: (Boolean) -> Unit = {},
               onFan: (Boolean) -> Unit = {},
               onSmokeConfig: (Boolean, Int, Boolean) -> Unit = { _, _, _ -> }) {
    val cloudSnapshot = if (deviceBridge != null) {
        deviceBridge.cloudSnapshot.collectAsState().value
    } else {
        null
    }
    var nowMs by remember(state.esp32Online, state.lastSeen, cloudSnapshot) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    val resolvedSource = remember(state.esp32Online, state.lastSeen, cloudSnapshot, nowMs) {
        DataSourceResolver.resolve(
            esp32Online = state.esp32Online,
            localLastSeen = state.lastSeen,
            snapshot = cloudSnapshot,
            nowMs = nowMs
        )
    }
    LaunchedEffect(state.esp32Online, state.lastSeen, cloudSnapshot) {
        val initialResolved = DataSourceResolver.resolve(
            esp32Online = state.esp32Online,
            localLastSeen = state.lastSeen,
            snapshot = cloudSnapshot,
            nowMs = nowMs
        )
        val updatedAt = initialResolved.updatedAt ?: return@LaunchedEffect
        while (!state.esp32Online && !DataSourceResolver.resolve(
                esp32Online = state.esp32Online,
                localLastSeen = state.lastSeen,
                snapshot = cloudSnapshot,
                nowMs = nowMs
            ).isStale
        ) {
            val remainingMs = (updatedAt + DataSourceResolver.STALE_THRESHOLD_MS + 1L) - nowMs
            if (remainingMs <= 0L) {
                nowMs = maxOf(System.currentTimeMillis(), updatedAt + DataSourceResolver.STALE_THRESHOLD_MS + 1L)
                break
            }
            val stepMs = minOf(remainingMs, 30_000L)
            delay(stepMs)
            nowMs = maxOf(System.currentTimeMillis(), nowMs + stepMs)
        }
    }
    val cloudFallbackSnapshot = cloudSnapshot?.takeIf { resolvedSource.source == DataSource.CLOUD }

    AppPage("Nhà của tôi", "", "home", onNavigate, unreadAlerts = state.alerts.count { !it.reviewed }) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (state.esp32Online) "ESP32 đã kết nối" else "ESP32 mất kết nối",
                        if (state.esp32Online) Blue600 else OrangeWarn,
                        if (state.esp32Online) Blue50 else YellowSoft)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Làm mới") }
                }
                DataSourceStatusRow(
                    resolved = resolvedSource,
                    fallbackLoading = cloudSnapshot?.loading == true,
                    fallbackErrorMessage = cloudSnapshot?.errorMessage
                )
            }
        }
        if (cloudBridge != null) {
            item {
                CloudHomeBannerFromBridge(
                    bridge = cloudBridge,
                    onReauth = { onNavigate("login") },
                    onOpenManagement = { onNavigate("settings") },
                    deviceBridge = deviceBridge
                )
            }
        }
        item { SecurityCard(state, onMode) }
        item { DoorControlCard(state, onSetDevice) { onNavigate("camera") } }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                if (cloudFallbackSnapshot != null) {
                    Text(
                        text = "Đang hiển thị dữ liệu Cloud · chỉ đọc",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (resolvedSource.isStale) OrangeWarn else GreenOk,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val tempText = if (cloudFallbackSnapshot != null) {
                    cloudFallbackSnapshot.effectiveTemp()?.let { "$it°C" } ?: "—"
                } else {
                    state.temperature?.let { "$it°C" } ?: "—"
                }
                val humText = if (cloudFallbackSnapshot != null) {
                    cloudFallbackSnapshot.effectiveHum()?.let { "$it%" } ?: "—"
                } else {
                    state.humidity?.let { "$it%" } ?: "—"
                }
                val rainText = if (cloudFallbackSnapshot != null) {
                    "—"
                } else {
                    when (state.hardware?.raining) {
                        true -> "Đang mưa"; false -> "Không mưa"; null -> "—"
                    }
                }
                val motionText = if (cloudFallbackSnapshot != null) {
                    when (cloudFallbackSnapshot.gatewayState?.motion) {
                        true -> "Phát hiện"
                        false -> "Không có"
                        null -> "Chưa có dữ liệu"
                    }
                } else {
                    when {
                        state.lastSeen == 0L || (state.security?.warmup ?: 0) > 0 -> "Chưa có dữ liệu"
                        state.motionDetected -> "Phát hiện"
                        else -> "Không có"
                    }
                }
                val smokePpm = if (cloudFallbackSnapshot != null) {
                    cloudFallbackSnapshot.effectiveSmokePpm()
                } else {
                    state.hardware?.smokePpm
                }
                val smokeText = smokePpm?.let { "$it ppm" } ?: "—"
                val longSensors = if (cloudFallbackSnapshot != null) {
                    cloudFallbackSnapshot.gatewayState?.longSensors
                } else state.hardware?.longSensors
                if (longSensors != null) {
                    val lux = longSensors.lightLux?.let { "%,.1f lx".format(it) } ?: "—"
                    val door = when (longSensors.reedClosed) {
                        true -> "Đóng"
                        false -> "Mở"
                        null -> "—"
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Reading("Độ sáng", lux, Modifier.weight(1f))
                        Reading("Tiếp điểm cửa", door, Modifier.weight(1f))
                    }
                    Text("Mưa ADC: ${longSensors.rainRaw ?: "—"} · RFID: ${sensorReady(longSensors.rfidReady)} · LCD: ${sensorReady(longSensors.lcdReady)}")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reading("Nhiệt độ", tempText, Modifier.weight(1f))
                    Reading("Độ ẩm", humText, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reading("Mưa", rainText, Modifier.weight(1f))
                    Reading("Chuyển động", motionText, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reading(
                        title = "Khói MQ-2",
                        value = smokeText,
                        modifier = Modifier.weight(1f),
                        valueColor = if (cloudFallbackSnapshot == null && state.smokeHazardActive) RedAlert else TextDark
                    )
                }
                if (cloudFallbackSnapshot != null) {
                    val gatewayStatusText = when (cloudFallbackSnapshot.gatewayState?.online) {
                        true -> "online"
                        false -> "offline"
                        null -> "chưa rõ"
                    }
                    Text(
                        text = "Cập nhật Cloud lúc ${resolvedSource.updatedAt?.let(::timeOnly) ?: "—"} · Gateway $gatewayStatusText",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (resolvedSource.isStale) OrangeWarn else TextGray
                    )
                } else if (!state.esp32Online && state.lastSeen > 0) {
                    Text("Cập nhật lần cuối lúc ${timeOnly(state.lastSeen)}",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = TextGray)
                }
                TextButton(onClick = { onNavigate("env") }) { Text("Xem tất cả cảm biến") }
            }
        }
        if (cloudFallbackSnapshot != null) {
            item {
                CloudReadOnlySummaryCard(
                    snapshot = cloudFallbackSnapshot,
                    resolved = resolvedSource
                )
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) { DeviceControlRow(state, DeviceType.LIGHT, onSetDevice) }
        }
        item { ClotheslineCard(state, onClothesline, onRainRetraction) }
        item { SmokeVentilationCard(state, onWindow, onFan, onSmokeConfig) }
        item { AlarmControlCard(state, onSetDevice) }

    }
}

private fun SmartHomeState.manualControlsReady() = esp32Online && mode != HomeMode.AWAY && security?.protecting!=true && !modeBusy && !ruleBusy && !alarmSettingsBusy && commands.values.none {it.phase==CommandPhase.SENDING}

@Composable
fun DoorControlCard(state: SmartHomeState, onSetDevice: (DeviceType,Boolean) -> Unit, onCamera: () -> Unit) {
    val hardware=state.hardware
    val blocked=hardware?.doorBlocked==true
    val busy=state.commands[DeviceType.DOOR]?.phase==CommandPhase.SENDING
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconBadge(Icons.Default.MeetingRoom,Blue600,Blue50)
            Column(Modifier.weight(1f).padding(start=12.dp)) {
                Text("Cửa chính",style=MaterialTheme.typography.titleMedium)
                Text(when {state.lastSeen==0L -> "Chưa có trạng thái";!state.esp32Online -> "Lần cuối: ${if(state.doorOpen) "mở" else "đóng"}";
                    state.doorOpen -> "Cửa đang mở";else -> "Cửa đã đóng"},style=MaterialTheme.typography.bodyMedium,color=TextGray)
            }
            if(state.esp32Online && blocked) StatusPill("Có vật cản",OrangeWarn,YellowSoft)
        }
        if (state.esp32Online && state.doorOpen && hardware?.usesDoorRules == true && state.mode == HomeMode.HOME) {
            Text("Tự đóng sau ${hardware.autoCloseSeconds} giây không có vật cản.",
                style = MaterialTheme.typography.bodySmall, color = TextGray)
            hardware.autoCloseRemaining?.let { remaining ->
                Text("Thiết bị báo: còn $remaining giây để tự đóng.", style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
        }
        if(state.esp32Online && state.doorOpen && blocked) Text(if(hardware?.distanceCm==null) "Đang giữ cửa mở. Kiểm tra cảm biến khoảng cách." else "Đang giữ cửa mở. Dọn vật cản để cửa tự đóng.",style=MaterialTheme.typography.bodySmall,color=OrangeWarn)
        if(state.security?.protecting==true) Text("Chọn Về nhà hoặc quét khuôn mặt để mở cửa.",style=MaterialTheme.typography.bodySmall,color=TextGray)
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick={onSetDevice(DeviceType.DOOR,true)},enabled=state.manualControlsReady() && !state.doorOpen,
                modifier=Modifier.weight(1f).semantics {contentDescription=state.deviceLabel(DeviceType.DOOR)}) {Text("Mở cửa")}
            OutlinedButton(onClick={onSetDevice(DeviceType.DOOR,false)},enabled=state.manualControlsReady() && state.doorOpen && !blocked,modifier=Modifier.weight(1f)) {Text("Đóng cửa")}
        }
        TextButton(onClick=onCamera,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Face,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Quét mặt để mở cửa")
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.commands[DeviceType.DOOR]?.let {command ->
            Text(command.message,style=MaterialTheme.typography.bodySmall,color=if(command.phase==CommandPhase.ERROR) RedAlert else TextGray)
        }
    }
}

@Composable
fun AlarmControlCard(state: SmartHomeState, onSetDevice: (DeviceType,Boolean) -> Unit, onSettings: (() -> Unit)? = null) {
    val hasBuzzer=state.hardware?.hasBuzzer!=false
    val near=state.proximityAlert
    val active=state.alarmActive
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconBadge(if(hasBuzzer) Icons.Default.NotificationsActive else Icons.Default.WarningAmber,
                if(state.esp32Online && active) OrangeWarn else TextGray,if(state.esp32Online && active) YellowSoft else OffGray)
            Column(Modifier.weight(1f).padding(start=12.dp)) {
                Text(state.deviceLabel(DeviceType.ALARM),style=MaterialTheme.typography.titleMedium)
                Text(when {!state.esp32Online -> "Chờ kết nối để cập nhật";near && active -> "Cảnh báo: vật trong vùng 2–10 cm";
                    state.alarm && state.security?.needsAttention==true -> "Đang báo động bảo vệ";state.alarm -> "Còi đang kêu";
                    state.security?.needsAttention==true -> if(state.security.phase==SecurityPhase.ENTRY_DELAY) "Đang chờ xác nhận chủ nhà" else if(state.hardware?.alarmSettings?.enabledFor(state.mode)==false) "Đã tắt báo động tự động · sự cố chờ xác nhận" else if(state.hardware?.alarmSettings==null) "Còi đã tự dừng · sự cố chờ xác nhận" else "Còi đã dừng · sự cố chờ xác nhận";
                    state.hardware?.usesDoorRules==true && state.hardware.distanceCm==null -> "Chưa có dữ liệu khoảng cách";else -> "Không có cảnh báo"},
                    style=MaterialTheme.typography.bodySmall,color=if(state.esp32Online && active) OrangeWarn else TextGray)
            }
        }

        if(state.esp32Online && near && active) Text("Kiểm tra vùng cảm biến, sau đó chọn Ở nhà để xác nhận sự cố.",style=MaterialTheme.typography.bodySmall,color=OrangeWarn)
        OutlinedButton(onClick={onSetDevice(DeviceType.ALARM,!state.alarm)},enabled=state.manualControlsReady() && (state.alarm || !near),
            modifier=Modifier.fillMaxWidth().padding(top=8.dp).semantics {contentDescription=state.deviceLabel(DeviceType.ALARM)}) {
            Text(if(state.alarm) "Dừng thử" else if(hasBuzzer) "Thử còi" else "Thử LED vàng")
        }
        Text("Thử bằng tay tự dừng sau ${if (state.security?.demoModes == true) 3 else state.hardware?.alarmSettings?.durationSeconds ?: 60} giây.",style=MaterialTheme.typography.bodySmall,color=TextGray)
        onSettings?.let { item -> TextButton(onClick=item) {Text("Điều chỉnh báo động")} }
        state.commands[DeviceType.ALARM]?.let { command ->
            if(command.phase==CommandPhase.SENDING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(command.message,style=MaterialTheme.typography.bodySmall,color=if(command.phase==CommandPhase.ERROR) RedAlert else TextGray)
        }
    }
}

@Composable
fun ClotheslineCard(state: SmartHomeState, onClothesline: (Boolean) -> Unit, onRainRetraction: (Boolean) -> Unit) {
    val hardware=state.hardware
    WhiteCard(Modifier.fillMaxWidth()) {
        Text("Giàn phơi",style=MaterialTheme.typography.titleMedium)
        if(hardware?.hasClothesline!=true) {
            Text("Nạp firmware mới và nối servo giàn phơi vào GPIO19 để sử dụng.",style=MaterialTheme.typography.bodySmall,color=TextGray)
        } else {
            Text(if(!state.esp32Online) "Mất kết nối · trạng thái lần cuối" else when(hardware.clothesRetracted) {true -> "Đã thu vào";false -> "Đang phơi ngoài";null -> "Chưa rõ vị trí servo"},style=MaterialTheme.typography.bodyMedium)
            Text(when(hardware.raining) {true -> "Cảm biến đang báo mưa";false -> "Cảm biến không báo mưa";null -> "Chưa có dữ liệu mưa"},style=MaterialTheme.typography.bodySmall,color=TextGray)
            val enabled=state.esp32Online && !state.clothesBusy && !state.modeBusy
            val rainBlocked=hardware.raining==true && hardware.rainRetractEnabled==true
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick={onClothesline(true)},enabled=enabled && hardware.clothesRetracted!=true,modifier=Modifier.weight(1f)) {Text("Thu vào")}
                OutlinedButton(onClick={onClothesline(false)},enabled=enabled && hardware.clothesRetracted!=false && !rainBlocked,modifier=Modifier.weight(1f)) {Text("Phơi ra")}
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tự thu khi mưa",style=MaterialTheme.typography.bodyMedium)
                    Text("Vẫn chạy khi đóng app. Hết mưa giữ nguyên vị trí thu.",style=MaterialTheme.typography.bodySmall,color=TextGray)
                }
                SmartSwitch(hardware.rainRetractEnabled==true,onRainRetraction,enabled && hardware.rainRetractEnabled!=null,"Tự thu khi mưa")
            }
            if(rainBlocked) Text("Đang mưa: giàn phơi được giữ ở vị trí thu.",style=MaterialTheme.typography.bodySmall,color=OrangeWarn)
            if(state.clothesBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.clothesMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=TextGray) }
        }
    }
}

@Composable
private fun Reading(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = TextDark) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = TextGray)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}


private fun sensorReady(value: Boolean?): String = when (value) {
    true -> "Kết nối"
    false -> "Chưa nhận"
    null -> "—"
}

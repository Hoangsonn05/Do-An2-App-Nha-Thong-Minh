package com.example.smarthomeai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.cloud.CloudUiBridge
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun HomeScreen(state: SmartHomeState, onSetDevice: (DeviceType, Boolean) -> Unit,
               onMode: (HomeMode) -> Unit, onRefresh: () -> Unit, onNavigate: (String) -> Unit,
               onClothesline: (Boolean) -> Unit = {}, onRainRetraction: (Boolean) -> Unit = {},
               cloudBridge: CloudUiBridge? = rememberActiveCloudBridge()) {
    AppPage("Nhà của tôi", "", "home", onNavigate, unreadAlerts = state.alerts.count { !it.reviewed }) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (state.esp32Online) "ESP32 đã kết nối" else "ESP32 mất kết nối",
                    if (state.esp32Online) Blue600 else OrangeWarn,
                    if (state.esp32Online) Blue50 else YellowSoft)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("Làm mới") }
            }
        }
        if (cloudBridge != null) {
            item {
                CloudHomeBannerFromBridge(cloudBridge) { onNavigate("settings") }
            }
        }
        item { SecurityCard(state, onMode) }
        item { DoorControlCard(state, onSetDevice) { onNavigate("camera") } }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reading("Nhiệt độ", state.temperature?.let { "$it°C" } ?: "—", Modifier.weight(1f))
                    Reading("Độ ẩm", state.humidity?.let { "$it%" } ?: "—", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Reading("Mưa", when (state.hardware?.raining) {
                        true -> "Đang mưa"; false -> "Không mưa"; null -> "—"
                    }, Modifier.weight(1f))
                    Reading("Chuyển động", when {
                        state.lastSeen == 0L || (state.security?.warmup ?: 0) > 0 -> "Chưa có dữ liệu"
                        state.motionDetected -> "Phát hiện"
                        else -> "Không có"
                    }, Modifier.weight(1f))
                }
                if (!state.esp32Online && state.lastSeen > 0) {
                    Text("Cập nhật lần cuối lúc ${timeOnly(state.lastSeen)}",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = TextGray)
                }
                TextButton(onClick = { onNavigate("env") }) { Text("Xem tất cả cảm biến") }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) { DeviceControlRow(state, DeviceType.LIGHT, onSetDevice) }
        }
        item { ClotheslineCard(state, onClothesline, onRainRetraction) }
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
private fun Reading(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = TextGray)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

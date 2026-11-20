package com.example.smarthomeai.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.theme.*
import kotlinx.coroutines.delay

data class SafetyBanner(val state: SmartHomeState, val acknowledge: () -> Unit)
val LocalSafetyBanner = staticCompositionLocalOf<SafetyBanner?> { null }

@Composable
fun CountdownLabel(state: SmartHomeState) {
    val security=state.security ?: return
    var remaining by remember(state.lastSeen,security.remaining) { mutableIntStateOf(security.remaining) }
    LaunchedEffect(state.lastSeen,security.remaining,state.esp32Online) {
        val start=SystemClock.elapsedRealtime()
        while(state.esp32Online && remaining>0) {
            delay(250)
            remaining=(security.remaining-((SystemClock.elapsedRealtime()-start)/1000).toInt()).coerceAtLeast(0)
        }
    }
    if(security.phase in setOf(SecurityPhase.EXIT_DELAY,SecurityPhase.ENTRY_DELAY)) Text(
        if(!state.esp32Online) "Chờ kết nối lại để cập nhật" else if(security.exitBlocked) "Đang chờ vùng cửa trống" else if(remaining>0) "Còn ${remaining}s" else "Đang chờ trạng thái ESP32…",
        style=MaterialTheme.typography.titleMedium,color=if(security.needsAttention) RedAlert else Blue600)
}

@Composable
fun SafetyNotice(state: SmartHomeState, onAcknowledge: () -> Unit) {
    val security=state.security ?: return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(YellowSoft).padding(12.dp)) {
        Text(security.phase.label,style=MaterialTheme.typography.titleMedium)
        CountdownLabel(state)
        Text(if(state.esp32Online) "ESP32 đang tự xử lý sự cố." else "Mất kết nối. ESP32 vẫn xử lý; cần cùng LAN để xác nhận.",style=MaterialTheme.typography.bodySmall,color=TextGray)
        TextButton(onClick=onAcknowledge,enabled=state.esp32Online && !state.modeBusy) {Text("Tôi đây · tắt bảo vệ")}
    }
}

@Composable
fun SecurityCard(state: SmartHomeState, onMode: (HomeMode) -> Unit) {
    val security = state.security
    val canChange = state.esp32Online && security != null && !state.modeBusy &&
        !state.ruleBusy && !state.alarmSettingsBusy && state.commands.values.none { it.phase == CommandPhase.SENDING }
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(HomeMode.HOME, HomeMode.AWAY).forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { if (state.mode != mode) onMode(mode) },
                    enabled = canChange && (mode == HomeMode.HOME || security?.demoModes == true),
                    label = { Text(mode.label, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val status = when {
            !state.esp32Online -> "Mất kết nối · Chế độ lần cuối: ${state.mode.label}"
            security?.demoModes != true -> "Nạp firmware bản demo để dùng hai chế độ."
            state.modeBusy -> "Đang áp dụng chế độ…"
            security.exitBlocked -> if (state.hardware?.distanceCm == null && state.hardware?.obstacle != true)
                "Chưa bật bảo vệ · Chưa xác định được vùng cửa"
                else "Chưa bật bảo vệ · Có vật cản ở cửa"
            security.needsAttention -> if (state.alarm) "Đang báo động" else "Còi đã dừng · Có sự kiện trong Nhật ký"
            security.protecting -> "Đang bảo vệ · Còi 10 giây khi có chuyển động"
            else -> "Điều khiển bình thường · Còi 3 giây khi có chuyển động"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium,
            color = if (security?.exitBlocked == true || security?.needsAttention == true) OrangeWarn else TextGray)
        if ((security?.warmup ?: 0) > 0 && state.esp32Online) {
            Text("Cảm biến chuyển động đang ổn định.", style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
        if (state.modeBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        state.modeMessage?.takeUnless { it.endsWith("ESP32 đã áp dụng") }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
    }
}

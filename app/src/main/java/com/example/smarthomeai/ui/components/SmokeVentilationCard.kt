package com.example.smarthomeai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.HomeMode
import com.example.smarthomeai.data.SmartHomeState
import com.example.smarthomeai.data.effectiveSmokeAuto
import com.example.smarthomeai.data.smokeHazardActive
import com.example.smarthomeai.data.ventilationSafetyLocked
import com.example.smarthomeai.ui.theme.Blue50
import com.example.smarthomeai.ui.theme.Blue600
import com.example.smarthomeai.ui.theme.GreenOk
import com.example.smarthomeai.ui.theme.OffGray
import com.example.smarthomeai.ui.theme.OrangeWarn
import com.example.smarthomeai.ui.theme.Outline
import com.example.smarthomeai.ui.theme.RedAlert
import com.example.smarthomeai.ui.theme.RedSoft
import com.example.smarthomeai.ui.theme.TextDark
import com.example.smarthomeai.ui.theme.TextGray
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmokeVentilationCard(
    state: SmartHomeState,
    onWindow: (Boolean) -> Unit,
    onFan: (Boolean) -> Unit,
    onSmokeConfig: (Boolean, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val h = state.hardware
    val hasHardware = h?.hasMq2 == true || h?.hasWindow == true || h?.hasFan == true
    val hazard = state.smokeHazardActive
    val safetyLocked = state.ventilationSafetyLocked
    var showConfig by rememberSaveable { mutableStateOf(false) }

    WhiteCard(modifier.fillMaxWidth().testTag("smoke-ventilation-card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = if (hazard) Icons.Default.WarningAmber else Icons.Default.Air,
                tint = when {
                    hazard -> RedAlert
                    hasHardware && state.esp32Online -> Blue600
                    else -> TextGray
                },
                bg = when {
                    hazard -> RedSoft
                    hasHardware && state.esp32Online -> Blue50
                    else -> OffGray
                }
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = "Phòng khói & Thông gió (MQ-2)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextDark
                )
                if (!hasHardware) {
                    Text(
                        text = "Chờ tích hợp phần cứng ESP32 (MQ-2 · Cửa sổ · Quạt)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                } else {
                    val ppmText = h?.smokePpm?.let { "$it ppm" }
                        ?: if (!state.esp32Online) "Mất kết nối" else "Đang làm nóng cảm biến"
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = ppmText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                hazard -> RedAlert
                                !state.esp32Online -> TextGray
                                h?.smokePpm != null -> GreenOk
                                else -> TextGray
                            }
                        )
                        if (hazard) {
                            StatusPill("Cảnh báo khói!", RedAlert, RedSoft)
                        }
                    }
                    if (!state.esp32Online && h?.smokePpm != null) {
                        Text(
                            text = "Mất kết nối · số đo lần cuối",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrangeWarn
                        )
                    }
                }
            }
        }

        if (!hasHardware) {
            Text(
                text = "Khung phần mềm & Cloud đã sẵn sàng. Mặc định báo động từ ${state.smokeLimit} ppm, tự mở cửa sổ và bật quạt.",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "Sơ đồ chân khuyến nghị: MQ-2 GPIO34, Cửa sổ GPIO21, Quạt GPIO22.",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(
                onClick = { showConfig = true },
                modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cài đặt ngưỡng khói (${state.smokeLimit} ppm)")
            }
        } else {
            if (hazard) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RedSoft,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = RedAlert,
                            modifier = Modifier.padding(top = 2.dp).size(20.dp)
                        )
                        Text(
                            text = "Cảnh báo khói vượt ngưỡng (${state.smokeLimit} ppm)! Hệ thống tự động mở cửa sổ và bật quạt.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = RedAlert,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
            }

            val baseReady = state.esp32Online && !state.ventilationBusy && !state.modeBusy
            val manualPermitted = state.mode != HomeMode.AWAY && state.security?.protecting != true

            if (h?.hasWindow == true) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Outline)
                Text(
                    text = "Cửa sổ: ${
                        when (h.windowOpen) {
                            true -> "Đang mở"
                            false -> "Đã đóng"
                            null -> "Chưa rõ"
                        }
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDark
                )
                val canOpenWindow = baseReady && h.windowOpen != true && (hazard || manualPermitted)
                val canCloseWindow = baseReady && h.windowOpen != false && !safetyLocked && manualPermitted
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onWindow(true) },
                        enabled = canOpenWindow,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Mở cửa sổ")
                    }
                    OutlinedButton(
                        onClick = { onWindow(false) },
                        enabled = canCloseWindow,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Đóng cửa sổ")
                    }
                }
            }

            if (h?.hasFan == true) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Outline)
                Text(
                    text = "Quạt thông gió: ${
                        when (h.fanOn) {
                            true -> "Đang bật"
                            false -> "Đang tắt"
                            null -> "Chưa rõ"
                        }
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDark
                )
                val canTurnOnFan = baseReady && h.fanOn != true && (hazard || manualPermitted)
                val canTurnOffFan = baseReady && h.fanOn != false && !safetyLocked && manualPermitted
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onFan(true) },
                        enabled = canTurnOnFan,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Bật quạt")
                    }
                    OutlinedButton(
                        onClick = { onFan(false) },
                        enabled = canTurnOffFan,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Tắt quạt")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Tự mở cửa sổ & quạt khi có khói",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDark
                    )
                    Text(
                        text = "Ngưỡng cảnh báo: ${state.smokeLimit} ppm",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
                SmartSwitch(
                    checked = state.effectiveSmokeAuto,
                    onChange = { onSmokeConfig(state.smokeAlertsEnabled, state.smokeLimit, it) },
                    enabled = baseReady,
                    label = "Tự mở cửa sổ & quạt khi có khói"
                )
            }

            if (safetyLocked) {
                Text(
                    text = "Đang có khói (≥ ${state.smokeLimit} ppm): khóa đóng cửa sổ và tắt quạt để đảm bảo an toàn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrangeWarn,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.ventilationBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            state.ventilationMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            TextButton(
                onClick = { showConfig = true },
                enabled = baseReady,
                modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cài đặt ngưỡng khói (${state.smokeLimit} ppm)")
            }
        }
    }

    if (showConfig) {
        SmokeConfigDialog(
            state = state,
            onDismiss = { showConfig = false },
            onSave = { alertsEnabled, thresholdPpm, autoVent ->
                onSmokeConfig(alertsEnabled, thresholdPpm, autoVent)
                showConfig = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmokeConfigDialog(
    state: SmartHomeState,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Boolean) -> Unit
) {
    var alertsEnabled by rememberSaveable(state.smokeAlertsEnabled) { mutableStateOf(state.smokeAlertsEnabled) }
    var autoVent by rememberSaveable(state.effectiveSmokeAuto) { mutableStateOf(state.effectiveSmokeAuto) }
    var limit by rememberSaveable(state.smokeLimit) {
        mutableFloatStateOf(state.smokeLimit.coerceIn(100, 1000).toFloat())
    }
    val presets = listOf(200 to "200 ppm", 300 to "300 ppm (Mặc định)", 500 to "500 ppm")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt cảnh báo khói MQ-2") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cảnh báo khói về điện thoại",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    SmartSwitch(
                        checked = alertsEnabled,
                        onChange = { alertsEnabled = it },
                        label = "Cảnh báo khói về điện thoại"
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tự động mở cửa sổ & bật quạt",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    SmartSwitch(
                        checked = autoVent,
                        onChange = { autoVent = it },
                        label = "Tự động mở cửa sổ & bật quạt"
                    )
                }
                Text(
                    text = "Ngưỡng cảnh báo: ${limit.roundToInt()} ppm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Slider(
                    value = limit,
                    onValueChange = { raw -> limit = ((raw / 50f).roundToInt() * 50).coerceIn(100, 1000).toFloat() },
                    valueRange = 100f..1000f,
                    steps = 17
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (ppm, label) ->
                        FilterChip(
                            selected = limit.roundToInt() == ppm,
                            onClick = { limit = ppm.toFloat() },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Text(
                    text = "Mặc định 300 ppm. Khi vượt ngưỡng, hệ thống cảnh báo và tự mở cửa sổ, bật quạt để thoát khói.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(alertsEnabled, limit.roundToInt(), autoVent) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Lưu thiết lập")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Hủy")
            }
        }
    )
}

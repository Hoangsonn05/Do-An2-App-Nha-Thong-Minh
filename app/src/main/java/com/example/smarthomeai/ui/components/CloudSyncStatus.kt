package com.example.smarthomeai.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.cloud.CloudDeviceSnapshot
import com.example.smarthomeai.cloud.CloudEventRecord
import com.example.smarthomeai.cloud.DataSource
import com.example.smarthomeai.cloud.GatewaySyncPhase
import com.example.smarthomeai.cloud.GatewaySyncUiState
import com.example.smarthomeai.cloud.ResolvedDataSource
import com.example.smarthomeai.data.timeOnly
import com.example.smarthomeai.ui.theme.Bg
import com.example.smarthomeai.ui.theme.Blue50
import com.example.smarthomeai.ui.theme.Blue600
import com.example.smarthomeai.ui.theme.GreenOk
import com.example.smarthomeai.ui.theme.GreenSoft
import com.example.smarthomeai.ui.theme.OffGray
import com.example.smarthomeai.ui.theme.OrangeWarn
import com.example.smarthomeai.ui.theme.Outline
import com.example.smarthomeai.ui.theme.RedAlert
import com.example.smarthomeai.ui.theme.RedSoft
import com.example.smarthomeai.ui.theme.TextDark
import com.example.smarthomeai.ui.theme.TextGray
import com.example.smarthomeai.ui.theme.YellowSoft

fun dataSourceLabel(source: DataSource): String = when (source) {
    DataSource.LAN -> "Nguồn: LAN trực tiếp"
    DataSource.CLOUD -> "Nguồn: Cloud (chỉ đọc)"
    DataSource.LOCAL_CACHE -> "Nguồn: Bộ nhớ máy"
    DataSource.NONE -> "Chưa có dữ liệu nguồn"
}

fun gatewaySyncPhaseLabel(state: GatewaySyncUiState): String = when (state.phase) {
    GatewaySyncPhase.IDLE -> ""
    GatewaySyncPhase.WAITING_HOME -> "Chờ chọn nhà Cloud để đồng bộ"
    GatewaySyncPhase.REGISTERING -> "Đang đăng ký thiết bị Gateway…"
    GatewaySyncPhase.SYNCING -> "Đang đồng bộ dữ liệu lên Cloud…"
    GatewaySyncPhase.OK -> state.lastSyncAt?.takeIf { it > 0L }?.let {
        "Đồng bộ Cloud gần nhất lúc ${timeOnly(it)}"
    } ?: "Đồng bộ Cloud hoàn tất"
    GatewaySyncPhase.PAUSED_LAN_OFFLINE -> "Đồng bộ tạm dừng · ESP32 mất kết nối LAN"
    GatewaySyncPhase.BLOCKED -> "Thiết bị Cloud đã bị thu hồi — không thể đồng bộ"
    GatewaySyncPhase.ERROR -> {
        val detail = state.errorMessage?.takeIf { it.isNotBlank() } ?: "Không thể đồng bộ dữ liệu"
        "Lỗi đồng bộ Cloud: $detail"
    }
}

fun cloudEventTitle(type: String): String = when (type) {
    "restarted" -> "ESP32 khởi động lại"
    "exit_started" -> "Ra ngoài · bắt đầu thời gian rời nhà"
    "away_selected" -> "Chuyển sang Ra ngoài"
    "motion_home" -> "Phát hiện chuyển động trước cửa · Ở nhà"
    "motion_waiting" -> "Phát hiện chuyển động trước cửa · Chờ bật bảo vệ"
    "armed" -> "Đã bật bảo vệ trên ESP32"
    "motion_entry" -> "PIR phát hiện chuyển động · bắt đầu chờ xác nhận"
    "alarm_started" -> "Báo động đã bật"
    "exit_waiting" -> "Chưa bật bảo vệ · Kiểm tra vùng cửa"
    "motion_alarm" -> "Cảm biến chuyển động kích hoạt báo động"
    "proximity_alarm" -> "Vật gần kích hoạt báo động"
    "siren_stopped" -> "Báo động tự dừng khi hết thời gian"
    "motion_detected" -> "Phát hiện chuyển động · báo động tự động đang tắt"
    "proximity_detected" -> "Phát hiện vật gần · báo động tự động đang tắt"
    "alarm_silent" -> "Đã ghi nhận sự cố · không bật còi và LED vàng"
    "alarm_muted" -> "Đã dừng báo động tự động · giữ sự cố chờ xác nhận"
    "alarm_rule_changed" -> "Đã thay đổi thiết lập báo động"
    "acknowledged" -> "Chủ nhà đã xác nhận · kết thúc sự cố"
    "home_selected" -> "Chuyển sang Ở nhà"
    "sleep_selected" -> "Chuyển sang Đi ngủ"
    "light_motion_on" -> "Chuyển động · đèn tự bật"
    "light_auto_off" -> "Không còn chuyển động · đèn tự tắt"
    "light_manual_on" -> "Bật đèn bằng tay"
    "light_manual_off" -> "Tắt đèn bằng tay"
    "servo_open" -> "Đã gửi servo đến vị trí mở"
    "servo_close" -> "Cửa chuyển về vị trí đóng"
    "servo_overdue" -> "Servo giữ vị trí mở quá 2 phút"
    "alarm_manual_on" -> "Bật báo động bằng tay"
    "alarm_manual_off" -> "Tắt báo động bằng tay"
    "servo_blocked" -> "Từ chối đóng servo vì có vật cản"
    "rain_started" -> "Cảm biến phát hiện mưa"
    "rain_cleared" -> "Cảm biến không còn báo mưa"
    "clothes_retracted" -> "Đã đưa servo giàn phơi đến vị trí thu vào"
    "clothes_extended" -> "Đã đưa servo giàn phơi đến vị trí phơi ra"
    "clothes_rain_retract" -> "Có mưa · giàn phơi tự thu vào"
    "clothes_rule_changed" -> "Đã đổi thiết lập tự thu khi mưa"
    "smoke_detected" -> "Phát hiện khói vượt ngưỡng"
    "smoke_auto_vent" -> "Có khói · tự động mở cửa sổ và bật quạt"
    "smoke_cleared" -> "Nồng độ khói đã về mức an toàn"
    "window_opened" -> "Đã mở cửa sổ"
    "window_closed" -> "Đã đóng cửa sổ"
    "fan_on" -> "Đã bật quạt thông gió"
    "fan_off" -> "Đã tắt quạt thông gió"
    "smoke_rule_changed" -> "Đã đổi thiết lập cảnh báo khói"
    "config_changed" -> "Đã thay đổi thời gian tự động hóa"
    else -> "Sự kiện thiết bị: $type"
}

internal fun CloudDeviceSnapshot.effectiveTemp(): Int? {
    val gw = gatewayState
    val tel = latestTelemetry
    return when {
        gw != null && tel != null -> if (tel.receivedAt > gw.receivedAt) (tel.temp ?: gw.temp) else (gw.temp ?: tel.temp)
        else -> gw?.temp ?: tel?.temp
    }
}

internal fun CloudDeviceSnapshot.effectiveHum(): Int? {
    val gw = gatewayState
    val tel = latestTelemetry
    return when {
        gw != null && tel != null -> if (tel.receivedAt > gw.receivedAt) (tel.hum ?: gw.hum) else (gw.hum ?: tel.hum)
        else -> gw?.hum ?: tel?.hum
    }
}

internal fun CloudDeviceSnapshot.effectiveSmokePpm(): Int? {
    val gw = gatewayState
    val tel = latestTelemetry
    return when {
        gw != null && tel != null -> if (tel.receivedAt > gw.receivedAt) (tel.smokePpm ?: gw.smokePpm) else (gw.smokePpm ?: tel.smokePpm)
        else -> gw?.smokePpm ?: tel?.smokePpm
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataSourceStatusRow(
    resolved: ResolvedDataSource,
    modifier: Modifier = Modifier,
    fallbackLoading: Boolean = false,
    fallbackErrorMessage: String? = null
) {
    val (pillColor, pillBg) = dataSourceColors(resolved)
    Column(
        modifier = modifier.fillMaxWidth().testTag("data-source-status"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                text = dataSourceLabel(resolved.source),
                color = pillColor,
                background = pillBg
            )
            if (resolved.isStale && resolved.source != DataSource.NONE) {
                StatusPill(
                    text = "Dữ liệu cũ",
                    color = OrangeWarn,
                    background = YellowSoft
                )
            }
        }
        val timestamp = resolved.updatedAt?.takeIf { it > 0L }
        if (timestamp != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Cập nhật dữ liệu lúc ${timeOnly(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (resolved.isStale && resolved.source != DataSource.LAN) OrangeWarn else TextGray
                )
            }
        }
        if (resolved.source != DataSource.LAN && resolved.source != DataSource.CLOUD) {
            when {
                fallbackLoading -> Text(
                    text = "Đang kiểm tra dữ liệu Cloud dự phòng…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                !fallbackErrorMessage.isNullOrBlank() -> Text(
                    text = "Cloud dự phòng: $fallbackErrorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrangeWarn
                )
            }
        }
    }
}

@Composable
fun GatewaySyncStatusSection(
    gatewaySyncState: GatewaySyncUiState,
    onRetrySync: (() -> Unit)? = null,
    retryButtonLabel: String = "Thử lại",
    modifier: Modifier = Modifier
) {
    val hasPhase = gatewaySyncState.phase != GatewaySyncPhase.IDLE
    val hasWarning = !gatewaySyncState.warningMessage.isNullOrBlank()
    if (!hasPhase && !hasWarning) return

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).testTag("gateway-sync-status"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (hasPhase) {
            val (fg, bg, icon) = gatewaySyncVisuals(gatewaySyncState.phase)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = gatewaySyncPhaseLabel(gatewaySyncState),
                        style = MaterialTheme.typography.bodySmall,
                        color = fg,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }
            if (gatewaySyncState.phase == GatewaySyncPhase.ERROR && onRetrySync != null) {
                OutlinedButton(
                    onClick = onRetrySync,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(retryButtonLabel)
                }
            }
        }
        val warning = gatewaySyncState.warningMessage
        if (!warning.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = YellowSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = OrangeWarn,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = OrangeWarn,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CloudReadOnlySummaryCard(
    snapshot: CloudDeviceSnapshot,
    resolved: ResolvedDataSource,
    modifier: Modifier = Modifier
) {
    val gw = snapshot.gatewayState
    val gatewayStatusText = when (gw?.online) {
        true -> "Gateway: Trực tuyến (online)"
        false -> "Gateway: Ngoại tuyến (offline)"
        null -> "Gateway: Chưa rõ trạng thái"
    }
    val recentEvents = snapshot.recentEvents.take(3)

    WhiteCard(modifier.fillMaxWidth().testTag("cloud-readonly-summary-card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(
                icon = Icons.Default.CloudDone,
                tint = if (resolved.isStale) OrangeWarn else GreenOk,
                bg = if (resolved.isStale) YellowSoft else GreenSoft,
                size = 36.dp
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Trạng thái từ xa qua Cloud (chỉ đọc)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextDark
                )
                Text(
                    text = gatewayStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gw?.online == false || resolved.isStale) OrangeWarn else TextGray
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CloudStateMetric(
                label = "Đèn",
                value = when (gw?.light) {
                    true -> "Đang bật"
                    false -> "Đang tắt"
                    null -> "—"
                },
                modifier = Modifier.weight(1f)
            )
            CloudStateMetric(
                label = "Cửa",
                value = when (gw?.door) {
                    true -> "Đang mở"
                    false -> "Đã đóng"
                    null -> "—"
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CloudStateMetric(
                label = "Còi",
                value = when (gw?.alarm) {
                    true -> "Đang kêu"
                    false -> "Bình thường"
                    null -> "—"
                },
                modifier = Modifier.weight(1f)
            )
            CloudStateMetric(
                label = "Chế độ",
                value = buildString {
                    append(gw?.mode?.label ?: "—")
                    if (gw?.monitoring == true) {
                        append(" · Đang giám sát")
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CloudStateMetric(
                label = "Khói MQ-2",
                value = snapshot.effectiveSmokePpm()?.let { "$it ppm" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            CloudStateMetric(
                label = "Cửa sổ",
                value = when (gw?.window) {
                    true -> "Đang mở"
                    false -> "Đã đóng"
                    null -> "—"
                },
                modifier = Modifier.weight(1f)
            )
            CloudStateMetric(
                label = "Quạt",
                value = when (gw?.fan) {
                    true -> "Đang bật"
                    false -> "Đang tắt"
                    null -> "—"
                },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Mưa & giàn phơi: Xem trong sự kiện Cloud",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            modifier = Modifier.padding(top = 10.dp)
        )

        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Outline)

        Text(
            text = "Sự kiện Cloud gần nhất",
            style = MaterialTheme.typography.labelLarge,
            color = TextDark
        )
        if (recentEvents.isEmpty()) {
            Text(
                text = "Chưa có sự kiện Cloud gần đây.",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                recentEvents.forEach { event ->
                    CloudEventItem(event)
                }
            }
        }

        Text(
            text = "Điều khiển phần cứng qua LAN đang khóa khi ngoài mạng nội bộ.",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun CloudStateMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Bg,
        border = BorderStroke(1.dp, Outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextGray)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
        }
    }
}

@Composable
private fun CloudEventItem(event: CloudEventRecord) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Bg,
        border = BorderStroke(1.dp, Outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = cloudEventTitle(event.type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextDark
            )
            val timePart = event.receivedAt.takeIf { it > 0L }?.let(::timeOnly) ?: "—"
            Text(
                text = "${event.type} · $timePart",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

private fun dataSourceColors(resolved: ResolvedDataSource): Pair<Color, Color> = when (resolved.source) {
    DataSource.LAN -> Blue600 to Blue50
    DataSource.CLOUD -> if (resolved.isStale) OrangeWarn to YellowSoft else GreenOk to GreenSoft
    DataSource.LOCAL_CACHE -> if (resolved.isStale) OrangeWarn to YellowSoft else TextGray to OffGray
    DataSource.NONE -> TextGray to OffGray
}

private fun gatewaySyncVisuals(phase: GatewaySyncPhase) = when (phase) {
    GatewaySyncPhase.OK -> Triple(GreenOk, GreenSoft, Icons.Default.CloudDone)
    GatewaySyncPhase.REGISTERING, GatewaySyncPhase.SYNCING -> Triple(Blue600, Blue50, Icons.Default.Sync)
    GatewaySyncPhase.PAUSED_LAN_OFFLINE -> Triple(OrangeWarn, YellowSoft, Icons.Default.CloudOff)
    GatewaySyncPhase.BLOCKED, GatewaySyncPhase.ERROR -> Triple(RedAlert, RedSoft, Icons.Default.ErrorOutline)
    GatewaySyncPhase.WAITING_HOME, GatewaySyncPhase.IDLE -> Triple(TextGray, OffGray, Icons.Default.Schedule)
}

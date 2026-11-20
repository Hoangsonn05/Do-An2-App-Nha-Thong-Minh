package com.example.smarthomeai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun AlarmSettingsCard(state: SmartHomeState, onChange: (AlarmSettings) -> Unit) {
    val settings = state.hardware?.alarmSettings
    val enabled = state.esp32Online && settings != null && !state.alarmSettingsBusy &&
        !state.modeBusy && !state.ruleBusy && !state.clothesBusy && state.commands.values.none { it.phase==CommandPhase.SENDING }
    WhiteCard(Modifier.fillMaxWidth()) {
        Text("Điều chỉnh báo động",style=MaterialTheme.typography.titleMedium)
        if(settings == null) {
            Text(if(state.esp32Online) "Cập nhật firmware ESP32 3.5 để điều chỉnh báo động." else "Kết nối ESP32 để đọc thiết lập báo động.",
                style=MaterialTheme.typography.bodySmall,color=TextGray,modifier=Modifier.padding(top=8.dp))
        } else {
            AlarmPreference("Báo động tự động khi Đi ngủ",settings.sleepEnabled,enabled) { onChange(settings.copy(sleepEnabled=it)) }
            AlarmPreference("Báo động tự động khi Ra ngoài",settings.awayEnabled,enabled) { onChange(settings.copy(awayEnabled=it)) }
            Text("Tắt: cảm biến vẫn ghi nhận sự cố, còi và LED vàng không tự bật. Nếu đang báo động ở chế độ đó, báo động sẽ dừng.",
                style=MaterialTheme.typography.bodySmall,color=TextGray,modifier=Modifier.padding(top=6.dp))
            HorizontalDivider(Modifier.padding(vertical=12.dp),color=Outline)
            Text("Còi tự dừng sau ${settings.durationSeconds} giây",style=MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(5,10,30,60).forEach { seconds ->
                    FilterChip(selected=settings.durationSeconds==seconds,onClick={onChange(settings.copy(durationSeconds=seconds))},
                        enabled=enabled,label={Text("${seconds}s")})
                }
            }
            Text("Áp dụng cả khi thử còi bằng tay. Hết thời gian, sự cố vẫn chờ xác nhận. Bật lại báo động tự động áp dụng từ sự cố tiếp theo.",
                style=MaterialTheme.typography.bodySmall,color=TextGray)
            Text("Thiết lập được lưu trên ESP32, vẫn áp dụng khi đóng app và khởi động lại thiết bị.",
                style=MaterialTheme.typography.bodySmall,color=Blue600,modifier=Modifier.padding(top=8.dp))
            if(!state.esp32Online) Text("Đang mất kết nối · hiển thị thiết lập lần cuối.",style=MaterialTheme.typography.bodySmall,color=OrangeWarn)
        }
        if(state.alarmSettingsBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
        state.alarmSettingsMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=TextGray,modifier=Modifier.padding(top=8.dp)) }
    }
}

@Composable
private fun AlarmPreference(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f).padding(end=12.dp))
        SmartSwitch(checked,onChange,enabled,title)
    }
}

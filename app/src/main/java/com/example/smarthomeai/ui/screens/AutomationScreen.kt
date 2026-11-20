package com.example.smarthomeai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun AutomationScreen(state: SmartHomeState, onRules: (Boolean,Boolean)->Unit,
                     onRefresh: ()->Unit, onNavigate: (String)->Unit, onBack: ()->Unit,
                     onTiming: (Int,Int,Int)->Unit={_,_,_->}, onAlarmSettings: (AlarmSettings)->Unit={}) {
    AppPage("Tự động hóa","","automation",onNavigate,onBack) {
        if(!state.esp32Online) item {ConnectionCard(state,onRefresh)}
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Text("Ba chế độ ngôi nhà",style=MaterialTheme.typography.titleMedium)
                HomeMode.entries.forEach { mode ->
                    Text(mode.label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))
                    Text(mode.description,style=MaterialTheme.typography.bodySmall,color=TextGray)
                }
                Text("Ra ngoài và Đi ngủ: cảm biến ghi nhận sự cố khi giám sát. Còi và LED vàng chỉ tự bật nếu báo động tự động của chế độ đó đang bật.",style=MaterialTheme.typography.bodySmall,color=TextGray,modifier=Modifier.padding(top=12.dp))
                Text("Chế độ tiếp tục chạy trên ESP32 khi đóng app.",style=MaterialTheme.typography.bodySmall,color=Blue600,modifier=Modifier.padding(top=8.dp))
            }
        }
        item { AlarmSettingsCard(state,onAlarmSettings) }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                SettingsLink("Ngưỡng cảnh báo cảm biến",Icons.Default.Sensors,
                    if(state.environmentAlerts) "Từ ${state.temperatureLimit}°C hoặc ${state.humidityLimit}%" else "Bật và đặt ngưỡng tại trang Cảm biến") {onNavigate("sensors")}
                HorizontalDivider(color=Outline)
                SettingsLink("Giàn phơi khi mưa",Icons.Default.Umbrella,
                    if(state.hardware?.rainRetractEnabled==true) "Đang bật tự thu" else "Điều khiển và thiết lập tại trang Nhà") {onNavigate("home")}
            }
        }
        item {Text("Cửa có vật cản hoặc thiếu số đo khoảng cách sẽ chờ trước khi đóng. PIR cần thời gian ổn định sau khi cấp nguồn.",style=MaterialTheme.typography.bodySmall,color=TextGray)}
    }
}

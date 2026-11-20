package com.example.smarthomeai.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun AlertsScreen(state: SmartHomeState, onReview: (String) -> Unit, onReviewAll: () -> Unit, onNavigate: (String) -> Unit, selectedAlert: String? = null) {
    var onlyUnread by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<AlertType?>(null) }
    var expandedIncident by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var showActivity by rememberSaveable { mutableStateOf(false) }
    val categories = listOf(null to "Mọi loại", AlertType.STRANGER to "Người lạ", AlertType.MOTION to "Chuyển động", AlertType.DOOR to "Cửa", AlertType.ENVIRONMENT to "Môi trường")
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedAlert) {
        if (selectedAlert != null) {
            onlyUnread = false
            category = null
            showActivity = false
            listState.scrollToItem(0)
        }
    }
    val alerts = state.alerts.filter { (!onlyUnread || !it.reviewed) && (category == null || it.type == category) }
        .sortedByDescending { it.id == selectedAlert }
    AppPage("Nhật ký", "", "alerts", onNavigate, unreadAlerts = state.alerts.count { !it.reviewed }, listState = listState) {
        state.syncMessage?.let { message -> item { Text(message,style=MaterialTheme.typography.bodySmall,color=OrangeWarn) } }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!showActivity,{showActivity=false},label={Text("Cảnh báo")})
                FilterChip(showActivity,{showActivity=true},label={Text("Hoạt động")})
            }
        }
        if(showActivity) {
            if(state.deviceEvents.isEmpty()) item {EmptyHint("Chưa có hoạt động được đồng bộ từ ESP32.")}
            items(state.deviceEvents.sortedByDescending {it.seq}.take(40),key={it.id}) {event ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    Text(event.title,style=MaterialTheme.typography.bodyMedium)
                    Text(event.timeLabel,style=MaterialTheme.typography.bodySmall,color=TextGray)
                }
            }
        }
        if(!showActivity) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.alerts.count { !it.reviewed }} chưa kiểm tra", style = MaterialTheme.typography.bodyMedium, color = TextGray, modifier = Modifier.weight(1f))
                TextButton(onClick = onReviewAll, enabled = state.alerts.any { !it.reviewed }) { Text("Đánh dấu tất cả") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(onlyUnread, { onlyUnread = !onlyUnread }, label = { Text("Chưa kiểm tra") })
                Box {
                    OutlinedButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(categories.firstOrNull { it.first == category }?.second ?: "Mọi loại")
                    }
                    DropdownMenu(showFilter, { showFilter = false }) {
                        categories.forEach { (type, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { category = type; showFilter = false }) }
                    }
                }
            }
        }
        if(alerts.isEmpty()) item { EmptyHint(if(state.alerts.isEmpty()) "Chưa có cảnh báo." else "Không có cảnh báo phù hợp.") }
        items(alerts, key = { it.id }) { alert ->
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(when(alert.type) { AlertType.STRANGER -> Icons.Default.PersonSearch; AlertType.DOOR -> Icons.Default.MeetingRoom; AlertType.ENVIRONMENT -> Icons.Default.Thermostat; else -> Icons.Default.Sensors },
                        null, tint = if(alert.reviewed) TextGray else OrangeWarn, modifier = Modifier.padding(top = 2.dp).size(24.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        if (alert.id == selectedAlert) Text("Sự kiện từ thông báo", color = Blue600, style = MaterialTheme.typography.labelMedium)
                        Text(alert.title, style = MaterialTheme.typography.bodyMedium)
                        Text(alert.time, style = MaterialTheme.typography.bodySmall, color = TextGray)
                    }
                }
                if(alert.imagePath != null) EvidencePhoto(alert.imagePath)
                alert.incidentKey?.let { key ->
                    TextButton(onClick={expandedIncident=if(expandedIncident==key) null else key}) {
                        Text(if(expandedIncident==key) "Thu gọn diễn biến" else "Xem diễn biến")
                    }
                    if(expandedIncident==key) {
                        state.deviceEvents.filter { it.incidentKey==key }.sortedBy { it.seq }.forEach { event ->
                            Column(Modifier.padding(start=12.dp,top=8.dp,bottom=4.dp)) {
                                Text(event.title,style=MaterialTheme.typography.bodyMedium)
                                Text(event.timeLabel,style=MaterialTheme.typography.bodySmall,color=TextGray)
                            }
                        }
                        Text("Ghi trên ESP32. Chỉ có ảnh khi camera điện thoại được mở.",style=MaterialTheme.typography.bodySmall,color=TextGray)
                    }
                }
                if(alert.reviewed) Text("Đã kiểm tra", style = MaterialTheme.typography.bodySmall, color = TextGray, modifier = Modifier.padding(start = 36.dp, top = 10.dp))
                else TextButton(onClick = { onReview(alert.id) }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Done, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đã kiểm tra")
                }
            }
        }
        }
    }
}
@Composable
private fun EvidencePhoto(path: String) {
    var image by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(path) {
        image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    Spacer(Modifier.height(14.dp))
    image?.let { Image(it, "Ảnh khuôn mặt tại thời điểm cảnh báo", Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Fit) }
        ?: Text("Ảnh không còn khả dụng.", style = MaterialTheme.typography.bodySmall, color = TextGray)
}

package com.example.smarthomeai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smarthomeai.data.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EnvironmentScreen(state: SmartHomeState, onRefresh: () -> Unit, onNavigate: (String) -> Unit,
                      onEnvironmentConfig: (Boolean, Int, Int, Int) -> Unit = { _, _, _, _ -> },
                      onBack: () -> Unit = { onNavigate("home") }, mainPage: Boolean = false) {
    var temperature by rememberSaveable { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf(HistoryRange.LIVE) }
    var exportRange by rememberSaveable { mutableStateOf(HistoryRange.DAY) }
    var showConfig by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportSamples by remember { mutableStateOf(emptyList<SensorSample>()) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var metricMenu by remember { mutableStateOf(false) }
    var logFilter by rememberSaveable { mutableStateOf("all") }
    val samples = if(range==HistoryRange.LIVE) state.liveSamples.inRange(HistoryRange.LIVE) else state.samples.inRange(range)
    val summary = summarize(samples, temperature)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri != null) {
            val selected = exportSamples
            exporting = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                        stream.bufferedWriter(Charsets.UTF_8).use { it.write(sensorCsv(selected)) }
                    }.isSuccess
                }
                exporting = false
                exportMessage = if(success) "Đã xuất ${selected.size} số đo (${exportRange.label}) vào tệp CSV." else "Không lưu được tệp. Hãy chọn vị trí khác và thử lại."
            }
        }
    }
    val logs = state.logs.filter { logFilter == "all" || if(logFilter == "motion") it.kind == "motion" else it.kind != "motion" }
    AppPage("Cảm biến", "", if(mainPage) "sensors" else "env", onNavigate, if(mainPage) null else onBack) {
        item { ConnectionCard(state, onRefresh) }
        item { LiveSensorsCard(state) }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                SettingsLink("Cảnh báo môi trường", Icons.Default.Thermostat,
                    if (state.environmentAlerts) "${state.coldLimit}–${state.temperatureLimit}°C" else "Đang tắt") {
                    showConfig = true
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { metricMenu = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(if(temperature) "Nhiệt độ" else "Độ ẩm", style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(metricMenu, { metricMenu = false }) {
                            DropdownMenuItem(text = { Text("Nhiệt độ") }, onClick = { temperature = true; metricMenu = false })
                            DropdownMenuItem(text = { Text("Độ ẩm") }, onClick = { temperature = false; metricMenu = false })
                        }
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Làm mới dữ liệu", tint = TextGray) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryRange.entries.forEach { option -> FilterChip(range == option, { range = option }, label = { Text(option.label) }) }
                }
                if(samples.size < 2) EmptyHint(if(range==HistoryRange.LIVE) "Đang chờ dữ liệu trực tiếp. Cập nhật khoảng mỗi giây." else "Chưa đủ dữ liệu. App lưu một mẫu mỗi phút khi kết nối.")
                else {
                    SensorChart(samples, temperature)
                    val format = SimpleDateFormat(when(range) {HistoryRange.WEEK->"dd/MM HH:mm";HistoryRange.LIVE->"HH:mm:ss";HistoryRange.DAY->"HH:mm"}, Locale("vi", "VN"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(format.format(Date(samples.first().timestamp)), style = MaterialTheme.typography.bodySmall, color = TextGray)
                        Text(format.format(Date(samples.last().timestamp)), style = MaterialTheme.typography.bodySmall, color = TextGray)
                    }
                }
                summary?.let {
                    val unit = if(temperature) "°C" else "%"
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Statistic("Thấp nhất", "${it.minimum}$unit")
                        Statistic("Trung bình", "${String.format(Locale.US, "%.1f", it.average)}$unit")
                        Statistic("Cao nhất", "${it.maximum}$unit")
                    }
                }
                Text("${samples.size} mẫu · ${if(range==HistoryRange.LIVE) "60 giây gần nhất" else "khoảng thiếu dữ liệu không nối đường"}.", style = MaterialTheme.typography.bodySmall, color = TextGray, modifier = Modifier.padding(top = 14.dp))
                TextButton(onClick = {
                    exportRange = range
                    exportSamples = samples.toList()
                    exportMessage = null
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    export.launch("SmartHome-${when(range) {HistoryRange.LIVE->"tructiep";HistoryRange.DAY->"24h";HistoryRange.WEEK->"7ngay"}}-$stamp.csv")
                }, enabled = samples.isNotEmpty() && !exporting) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if(exporting) "Đang lưu…" else "Xuất dữ liệu CSV")
                }
                exportMessage?.let { Text(it, color = Blue600, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            SectionTitle("Nhật ký hoạt động", action = if(showLogs) "Thu gọn" else "Xem") { showLogs = !showLogs }
            if(showLogs) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Tất cả", "motion" to "Chuyển động", "device" to "Điều khiển").forEach { (key, label) ->
                    FilterChip(logFilter == key, { logFilter = key }, label = { Text(label) })
                }
            }
        }
        if(showLogs) {
            if(logs.isEmpty()) item { EmptyHint("Chưa có hoạt động trong nhóm này.") }
            else items(logs.take(40)) { log ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    Text(log.title, style = MaterialTheme.typography.bodyMedium)
                    Text(log.time, style = MaterialTheme.typography.bodySmall, color = TextGray)
                }
            }
        }
    }
    if(showConfig) EnvironmentConfigDialog(state, { showConfig = false }) { enabled, temp, hum, cold ->
        onEnvironmentConfig(enabled, temp, hum, cold); showConfig = false
    }
}

@Composable
fun LiveSensorsCard(state: SmartHomeState) {
    val h=state.hardware
    WhiteCard(Modifier.fillMaxWidth()) {
        Text(if(state.esp32Online) "Dữ liệu trực tiếp" else "Dữ liệu lần cuối",style=MaterialTheme.typography.titleMedium)
        Text("Wi-Fi nội bộ · cập nhật khoảng mỗi giây",style=MaterialTheme.typography.bodySmall,color=TextGray)
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Nhiệt độ DHT22",style=MaterialTheme.typography.bodySmall,color=TextGray)
                Text(state.temperature?.let {"$it°C"} ?: "—",style=MaterialTheme.typography.headlineMedium,
                    color=if(state.esp32Online && state.environmentAlerts && (state.temperature ?: Int.MIN_VALUE)>=state.temperatureLimit) RedAlert else Blue600)
            }
            Column(Modifier.weight(1f)) {
                Text("Độ ẩm",style=MaterialTheme.typography.bodySmall,color=TextGray)
                Text(state.humidity?.let {"$it%"} ?: "—",style=MaterialTheme.typography.headlineMedium,
                    color=if(state.esp32Online && state.environmentAlerts && (state.humidity ?: Int.MIN_VALUE)>=state.humidityLimit) RedAlert else Blue600)
            }
        }
        HorizontalDivider(color=Outline)
        SensorDetail("Chuyển động",when {state.lastSeen==0L -> "Chưa có dữ liệu";(state.security?.warmup ?: 0)>0 -> "Đang ổn định";state.motionDetected -> "Phát hiện";else -> "Không phát hiện"})
        SensorDetail("Hồng ngoại",h?.obstacle?.let {if(it) "Có vật cản" else "Không có vật cản"} ?: "Chưa có dữ liệu")
        SensorDetail("Mưa",h?.raining?.let {if(it) "Có mưa" else "Không có mưa"} ?: "Chưa có dữ liệu")
        SensorDetail("Khoảng cách",h?.distanceCm?.let {"%.1f cm".format(Locale.US,it)} ?: "Chưa có phản hồi")
        SensorDetail("Báo động",if(state.lastSeen==0L) "Chưa có dữ liệu" else if(state.alarmActive) "Đang bật" else "Đang tắt")
        if(!state.esp32Online) Text("Chờ kết nối lại để nhận số đo mới.",style=MaterialTheme.typography.bodySmall,color=OrangeWarn)
        Text(if(state.environmentAlerts) "Nóng từ ${state.temperatureLimit}°C · Lạnh từ ${state.coldLimit}°C trở xuống." else "Bật Cảnh báo môi trường bên dưới để nhận thông báo vượt ngưỡng.",style=MaterialTheme.typography.bodySmall,color=TextGray,modifier=Modifier.padding(top=8.dp))
    }
}

@Composable
private fun SensorDetail(label: String,value: String) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,style=MaterialTheme.typography.bodyMedium,color=TextGray)
        Text(value,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Statistic(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Blue600)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextGray)
    }
}

@Composable
fun EnvironmentConfigDialog(state: SmartHomeState, onDismiss: () -> Unit, onSave: (Boolean, Int, Int, Int) -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(state.environmentAlerts) }
    var temp by rememberSaveable { mutableFloatStateOf(state.temperatureLimit.toFloat()) }
    var cold by rememberSaveable { mutableFloatStateOf(state.coldLimit.toFloat()) }
    var humidity by rememberSaveable { mutableFloatStateOf(state.humidityLimit.toFloat()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cảnh báo môi trường") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bật cảnh báo", modifier = Modifier.weight(1f))
                SmartSwitch(enabled, { enabled = it })
            }
            Text("Nóng từ ${temp.roundToInt()}°C", modifier = Modifier.padding(top = 12.dp))
            Slider(temp, { temp = it }, valueRange = 20f..50f, steps = 29, enabled = enabled)
            Text("Lạnh từ ${cold.roundToInt()}°C trở xuống", modifier = Modifier.padding(top = 12.dp))
            Slider(cold, { cold = it }, valueRange = 0f..19f, steps = 18, enabled = enabled)
            Text("Độ ẩm từ ${humidity.roundToInt()}%", modifier = Modifier.padding(top = 12.dp))
            Slider(humidity, { humidity = it }, valueRange = 40f..95f, steps = 54, enabled = enabled)
            Text("Áp dụng khi app nhận dữ liệu mới từ ESP32. Cảnh báo được lưu trong nhật ký và không tự bật còi.", style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
    }, confirmButton = { TextButton(onClick = { onSave(enabled, temp.roundToInt(), humidity.roundToInt(), cold.roundToInt()) }) { Text("Lưu thiết lập") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } })
}

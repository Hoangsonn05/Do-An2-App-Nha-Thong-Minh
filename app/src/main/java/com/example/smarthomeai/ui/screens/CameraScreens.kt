package com.example.smarthomeai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.smarthomeai.data.*
import com.example.smarthomeai.face.*
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun CameraPreviewBox(modifier: Modifier = Modifier, front: Boolean = true, engine: FaceEngine? = null,
                     onFace: ((FaceResult?) -> Unit)? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onFace)
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if(!granted) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(granted, owner, engine, front) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var useCases: Array<UseCase> = emptyArray()
        val executor = Executors.newSingleThreadExecutor()
        if(granted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if(!disposed) try {
                    val p = future.get()
                    provider = p
                    val rotation = view.display?.rotation ?: Surface.ROTATION_0
                    val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val analysis = engine?.let { e ->
                        ImageAnalysis.Builder().setTargetRotation(rotation).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                            it.setAnalyzer(executor,e.analyzer { r -> if (!disposed) callback?.invoke(r) })
                        }
                    }
                    useCases = if(analysis == null) arrayOf(preview) else arrayOf(preview,analysis)
                    val selector = if(front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    val group = UseCaseGroup.Builder()
                        .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).build())
                    useCases.forEach { group.addUseCase(it) }
                    p.bindToLifecycle(owner, selector, group.build())
                    error = null
                } catch (_: Exception) { error = "Không mở được camera. Hãy kiểm tra quyền truy cập và thử lại." }
            },ContextCompat.getMainExecutor(context))
        }
        onDispose { disposed = true; callback?.invoke(null); provider?.unbind(*useCases); executor.shutdown() }
    }
    Box(modifier.background(Color(0xFF10272D)),contentAlignment = Alignment.Center) {
        if(granted && error == null) AndroidView(factory = { view },modifier = Modifier.fillMaxSize())
        else Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VideocamOff,null,tint = Color.White)
            Text(error ?: "Cần cho phép truy cập camera.", color = Color.White, modifier = Modifier.padding(vertical = 12.dp))
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Mở cài đặt quyền", color = Accent) }
        }
    }
}
@Composable
fun CameraScreen(state: SmartHomeState, onRecognized: (String?, Int, Bitmap?) -> Unit,
                 onRegister: () -> Unit, onNavigate: (String) -> Unit,
                 onOpenDoor: (String) -> Unit = {}, onVerification: (String?) -> Unit = {}) {
    val context = LocalContext.current
    val engine = remember { FaceEngine(context) }
    val store = remember { FaceStore(context) }
    val owner = LocalLifecycleOwner.current
    val verification by rememberUpdatedState(onVerification)
    val gate = remember { RecognitionGate() }
    var scanning by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(true) }
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    var box by remember { mutableStateOf<NormBox?>(null) }
    var verified by remember { mutableStateOf<String?>(null) }
    var classified by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    val reportedKeys = remember { mutableSetOf<String>() }
    var lastFaceAt by remember { mutableLongStateOf(0L) }

    fun clearMatch() {
        box = null
        verified = null
        classified = false
        gate.observe(null)
        verification(null)
    }

    fun resetScan() {
        clearMatch()
        opened = false
        reportedKeys.clear()
        lastFaceAt = 0L
    }

    DisposableEffect(owner, engine) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) active = true
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                active = false
                resetScan()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            verification(null)
            engine.close()
        }
    }

    LaunchedEffect(scanning) {
        while (scanning) {
            delay(250)
            if (lastFaceAt != 0L && SystemClock.elapsedRealtime() - lastFaceAt > 1_500) resetScan()
        }
    }

    val ready = state.esp32Online && !state.modeBusy && !state.ruleBusy && !state.clothesBusy &&
        !state.alarmSettingsBusy && state.commands.values.none { it.phase == CommandPhase.SENDING }
    LaunchedEffect(verified, ready, scanning, active, state.doorOpen, state.mode) {
        val name = verified
        if (name != null && scanning && active && ready && !opened &&
            (!state.doorOpen || state.mode == HomeMode.AWAY)) {
            opened = true
            onOpenDoor(name)
        }
    }

    val handle: (FaceResult?) -> Unit = { result ->
        if (!active || !scanning || result == null) {
            clearMatch()
        } else {
            box = result.box
            lastFaceAt = SystemClock.elapsedRealtime()
            val match = result.embedding?.let(store::match)
            val key = result.embedding?.let { match?.let { "member:${it.name}" } ?: "unknown" }
            classified = gate.observe(key)
            verified = match?.name?.takeIf { classified }
            verification(verified)
            if (classified && key != null && reportedKeys.add(key)) {
                onRecognized(verified, ((match?.score ?: 0f) * 100).toInt().coerceIn(0, 100), result.snapshot)
            }
        }
    }

    val status = when {
        !engine.modelReady -> "Không tải được mô hình AI"
        !scanning -> "Bật camera để nhận diện và mở cửa"
        box == null -> "Đưa một khuôn mặt vào camera"
        !classified -> "Đang nhận diện…"
        verified != null -> verified.orEmpty()
        else -> "Người lạ"
    }
    AppPage("Khuôn mặt", "", "camera", onNavigate, unreadAlerts = state.alerts.count { !it.reviewed }) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF19242D)), contentAlignment = Alignment.Center) {
                if (scanning) {
                    CameraPreviewBox(Modifier.fillMaxSize(), front, engine, handle)
                    box?.let { face ->
                        FaceOverlay(face, front, status, when {
                            !classified -> Color(0xFFE4EBF1)
                            verified != null -> Color(0xFF52DF96)
                            else -> Color(0xFFFF7277)
                        })
                    }
                    FilledTonalIconButton(onClick = { front = !front; resetScan() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                        Icon(Icons.Default.Cameraswitch, "Đổi camera")
                    }
                } else {
                    Icon(Icons.Default.Face, null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
                Text(status, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xDA19242D)).padding(14.dp), color = Color.White)
            }
        }
        item {
            Button(onClick = { resetScan(); scanning = !scanning }, enabled = engine.modelReady,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (scanning) "Dừng quét" else "Bắt đầu quét")
            }
            if (scanning) {
                val message = when {
                    verified == null -> null
                    !state.esp32Online -> "Chưa kết nối ESP32"
                    state.faceDoorMessage != null && opened -> state.faceDoorMessage
                    state.doorOpen -> "Cửa đang mở"
                    else -> "Đang chuẩn bị mở cửa…"
                }
                message?.let { Text(it, Modifier.padding(top = 8.dp), color = TextGray) }
            }
        }
        item {
            Text("Nhận diện tự mở cửa dành cho mô hình demo.", style = MaterialTheme.typography.bodySmall, color = TextGray)
            if (state.registeredUsers.isEmpty()) TextButton(onClick = onRegister) { Text("Thêm thành viên đầu tiên") }
        }
    }
}

@Composable
private fun FaceOverlay(face: NormBox, mirrored: Boolean, label: String, color: Color) {
    val left = if (mirrored) 1f - face.right else face.left
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val shape = RoundedCornerShape(5.dp)
        Box(Modifier.offset(maxWidth * left, maxHeight * face.top)
            .size(maxWidth * (face.right - face.left), maxHeight * (face.bottom - face.top))
            .border(2.dp, color, shape))
        Text(label, Modifier.offset(maxWidth * left.coerceAtMost(.57f), (maxHeight * face.top - 27.dp).coerceAtLeast(0.dp))
            .widthIn(max = maxWidth * .42f).background(color, shape).padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color(0xFF101820), style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun FaceDoorCard(state: SmartHomeState, verifiedName: String?, onOpen: (String) -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        if(verifiedName!=null) {
            Text("Đã nhận diện: $verifiedName",style=MaterialTheme.typography.titleMedium)
            if(state.security?.protecting==true) Text("Bấm mở cửa sẽ chuyển sang Ở nhà và tắt bảo vệ.",style=MaterialTheme.typography.bodySmall,color=TextGray)
            if(state.hardware?.usesDoorRules==true) Text("Cửa tự đóng sau 5 giây không có vật cản.",style=MaterialTheme.typography.bodySmall,color=TextGray)
            PrimaryButton(if(state.modeBusy) "Đang mở cửa…" else "Mở cửa",
                enabled=state.esp32Online && !state.doorOpen && !state.modeBusy && !state.ruleBusy && !state.clothesBusy && state.commands.values.none { it.phase==CommandPhase.SENDING }) {onOpen(verifiedName)}
            if(!state.esp32Online) Text("Kết nối ESP32 để mở cửa.",color=OrangeWarn,style=MaterialTheme.typography.bodySmall)
            else if(state.doorOpen) Text("Servo cửa đang ở vị trí mở.",style=MaterialTheme.typography.bodySmall,color=TextGray)
        } else Text("Quét lại khuôn mặt để mở cửa.",style=MaterialTheme.typography.bodySmall,color=TextGray)
        state.faceDoorMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=TextGray) }
    }
}
@Composable
fun FaceRegisterScreen(onBack: () -> Unit, onRegistered: (String) -> Unit) {
    val context = LocalContext.current
    val engine = remember { FaceEngine(context) }
    val store = remember { FaceStore(context) }
    DisposableEffect(engine) { onDispose { engine.close() } }
    var name by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val samples = remember { mutableListOf<FloatArray>() }
    LaunchedEffect(scanning) {
        if(scanning) {
            delay(20_000)
            scanning = false; samples.clear(); count = 0
            message = "Chưa lấy đủ mẫu. Hãy thử lại ở nơi đủ sáng, chỉ có một người trong khung."
        }
    }
    LaunchedEffect(count) {
        if(scanning && count >= 8) {
            store.save(name.trim(),engine.average(samples))
            scanning = false
            onRegistered(name.trim())
            onBack()
        }
    }
    Scaffold(containerColor = Bg,topBar = { GradientTopBar("Thêm thành viên", onBack) }) { pad ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Nhìn thẳng, sau đó xoay nhẹ đầu. Hệ thống thu 8 mẫu và lưu trên điện thoại.",style = MaterialTheme.typography.bodyMedium,color = TextGray)
            }
            item { OutlinedTextField(name,{ name = it.take(50) },label = { Text("Tên thành viên") },singleLine = true,enabled = !scanning,modifier = Modifier.fillMaxWidth()) }
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Blue50),contentAlignment = Alignment.Center) {
                    if(scanning) CameraPreviewBox(Modifier.fillMaxSize(),engine = engine) { r ->
                        if(scanning && r?.embedding != null && samples.size < 8) { samples.add(r.embedding); count = samples.size }
                    } else Icon(Icons.Default.Face,null,tint = Blue600,modifier = Modifier.size(100.dp))
                }
            }
            if(scanning) item {
                LinearProgressIndicator(progress = { count/8f },modifier = Modifier.fillMaxWidth())
                Text("Đang thu mẫu: $count / 8",style = MaterialTheme.typography.bodyMedium,color = Blue600)
            }
            message?.let { item { Text(it,color = RedAlert,style = MaterialTheme.typography.bodyMedium) } }
            item {
                PrimaryButton(if(scanning) "Hủy quét" else "Bắt đầu đăng ký") {
                    if(scanning) { scanning = false; samples.clear(); count = 0 }
                    else when {
                        name.isBlank() -> message = "Vui lòng nhập tên thành viên."
                        name.trim() in store.names() -> message = "Tên này đã được đăng ký. Hãy dùng tên khác hoặc xóa bản cũ trong Cài đặt."
                        !engine.modelReady -> message = "Không tải được mô hình nhận diện. Hãy kiểm tra bản cài app."
                        else -> { message = null; samples.clear(); count = 0; scanning = true }
                    }
                }
            }
            item { Text("Khuôn mặt là dữ liệu cá nhân. Chỉ đăng ký khi người đó đồng ý; có thể xóa trong Cài đặt.",
                style = MaterialTheme.typography.bodySmall,color = TextGray) }
        }
    }
}

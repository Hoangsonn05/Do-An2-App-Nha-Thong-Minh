package com.example.smarthomeai.data

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthomeai.cloud.CloudComponents
import com.example.smarthomeai.cloud.CloudDeviceSnapshot
import com.example.smarthomeai.cloud.CloudDeviceUiBridge
import com.example.smarthomeai.cloud.CloudDeviceUiBridgeRegistry
import com.example.smarthomeai.cloud.CloudFallbackObserver
import com.example.smarthomeai.cloud.CloudFoundationFactory
import com.example.smarthomeai.cloud.CloudGatewaySync
import com.example.smarthomeai.cloud.CloudHomeManager
import com.example.smarthomeai.cloud.CloudSessionSyncEvaluator
import com.example.smarthomeai.cloud.CloudUiBridgeRegistry
import com.example.smarthomeai.cloud.CloudUiState
import com.example.smarthomeai.cloud.GatewaySyncUiState
import com.example.smarthomeai.cloud.toGatewayInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class SmartHomeViewModel internal constructor(
    app: Application,
    cloudComponents: CloudComponents
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, CloudFoundationFactory.create(app))

    private val storage = Storage(app)
    private val notifier = Notifier(app)
    private val auth = AuthRepository(app)
    private val network = Mutex()
    private val _state = MutableStateFlow(storage.load().copy(userEmail = auth.currentEmail))
    val state = _state.asStateFlow()
    private val _loggedIn = MutableStateFlow(auth.isSignedIn())
    val loggedIn = _loggedIn.asStateFlow()
    val firebaseConfigured get() = auth.configured
    private val cloudManager = CloudHomeManager(viewModelScope, cloudComponents.session, cloudComponents.repository, singleHomeMode = true)
    val cloudState: StateFlow<CloudUiState> = cloudManager.state
    private val gatewayInputFlow = _state
        .map { it.toGatewayInput() }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
    private val gatewaySync: CloudGatewaySync? = cloudComponents.deviceRepository?.let { deviceRepo ->
        CloudGatewaySync(
            scope = viewModelScope,
            cloudStateFlow = cloudManager.state,
            gatewayInputFlow = gatewayInputFlow,
            deviceRepository = deviceRepo,
            workContext = Dispatchers.IO
        )
    }
    private val esp32OnlineFlow = _state
        .map { it.esp32Online }
        .distinctUntilChanged()
    private val cloudFallbackObserver: CloudFallbackObserver? = cloudComponents.deviceRepository?.let { deviceRepo ->
        CloudFallbackObserver(
            scope = viewModelScope,
            cloudStateFlow = cloudManager.state,
            esp32OnlineFlow = esp32OnlineFlow,
            deviceRepository = deviceRepo,
            workContext = Dispatchers.IO
        )
    }
    private val deviceUiBridge: CloudDeviceUiBridge? = if (gatewaySync != null || cloudFallbackObserver != null) {
        object : CloudDeviceUiBridge {
            override val gatewaySync: StateFlow<GatewaySyncUiState> =
                this@SmartHomeViewModel.gatewaySync?.gatewaySync
                    ?: MutableStateFlow(GatewaySyncUiState()).asStateFlow()

            override val cloudSnapshot: StateFlow<CloudDeviceSnapshot?> =
                cloudFallbackObserver?.cloudSnapshot
                    ?: MutableStateFlow<CloudDeviceSnapshot?>(null).asStateFlow()

            override fun retrySync() {
                this@SmartHomeViewModel.gatewaySync?.retrySync()
                cloudFallbackObserver?.retry()
            }
        }
    } else {
        null
    }
    val cloudSnapshot: StateFlow<CloudDeviceSnapshot?> =
        deviceUiBridge?.cloudSnapshot ?: MutableStateFlow<CloudDeviceSnapshot?>(null).asStateFlow()
    private val _reauthRequired = MutableStateFlow(false)
    val reauthRequired: StateFlow<Boolean> = _reauthRequired.asStateFlow()
    private var observedCloudUid: String? = null
    private var lastSample = 0L
    private var lastMotionAlert = 0L
    private var lastProximityAlert = 0L
    private var overdueHandled = false
    private var environmentGate = EnvironmentEvents()
    private var clockSyncedBoot = ""
    private val faceAccess = com.example.smarthomeai.face.FaceAccessSession()

    init {
        CloudUiBridgeRegistry.register(cloudManager)
        deviceUiBridge?.let { CloudDeviceUiBridgeRegistry.register(it) }
        viewModelScope.launch {
            combine(
                _loggedIn,
                cloudManager.state.map { it.status to it.currentUid }.distinctUntilChanged()
            ) { isLoggedIn, (status, uid) ->
                Triple(isLoggedIn, status, uid)
            }.distinctUntilChanged().collect { (isLoggedIn, status, uid) ->
                val decision = CloudSessionSyncEvaluator.evaluate(
                    firebaseConfigured = auth.configured,
                    localLoggedIn = isLoggedIn,
                    prevObservedUid = observedCloudUid,
                    cloudStatus = status,
                    cloudUid = uid
                )
                observedCloudUid = decision.nextObservedUid
                _reauthRequired.value = decision.shouldRequireReauth
                cloudManager.setReauthRequired(decision.shouldRequireReauth)
                if (decision.shouldRefreshUserEmail) {
                    val freshEmail = auth.currentEmail
                    if (!freshEmail.isNullOrBlank()) {
                        _state.update { it.copy(userEmail = freshEmail) }
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { val saved = storage.samples(); _state.update { it.copy(samples = (saved + it.samples).distinctBy { p -> p.timestamp }.sortedBy { p -> p.timestamp }) } }
        viewModelScope.launch(Dispatchers.IO) {
            _state.collectLatest { delay(500); storage.save(it) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while(isActive) {
                if (_loggedIn.value) network.withLock { if(_loggedIn.value) refreshLocked() }
                delay(1_000)
            }
        }
    }
    fun login(email: String, password: String, onResult: (String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) { onResult("Vui lòng nhập email và mật khẩu"); return }
        if (!auth.configured) { finishLogin(email.trim()); onResult(null); return }
        auth.login(email.trim(), password) { error ->
            if (error == null) finishLogin(auth.currentEmail ?: email.trim())
            onResult(error)
        }
    }
    fun register(email: String, password: String, onResult: (String?) -> Unit) {
        if (email.isBlank() || password.length < 6) { onResult("Nhập email và mật khẩu ít nhất 6 ký tự"); return }
        if (!auth.configured) { finishLogin(email.trim()); onResult(null); return }
        auth.register(email.trim(), password) { error ->
            if (error == null) finishLogin(auth.currentEmail ?: email.trim())
            onResult(error)
        }
    }
    fun loginWithGoogle(activity: Activity, done: (String?) -> Unit) {
        if (!auth.configured) { done("Chưa cấu hình đăng nhập Google"); return }
        auth.signInWithGoogle(activity) { error ->
            if(error == null) finishLogin(auth.currentEmail ?: "Google")
            done(error)
        }
    }
    private fun finishLogin(email: String) {
        _reauthRequired.value = false
        cloudManager.setReauthRequired(false)
        _state.update { it.copy(userEmail = email) }; _loggedIn.value = true; refresh()
    }
    fun logout() { verifyLiveFace(null); observedCloudUid = null; _reauthRequired.value = false; cloudManager.onSignedOut(); auth.signOut(); _loggedIn.value = false; _state.update { it.copy(userEmail = null, esp32Online = false, commands = emptyMap(),faceDoorMessage=null) } }

    fun selectCloudHome(homeId: String) = cloudManager.selectHome(homeId)
    fun createCloudHome(name: String) = cloudManager.createHome(name)
    fun leaveSelectedCloudHome() = cloudManager.leaveSelectedHome()
    fun removeCloudMember(memberUid: String) = cloudManager.removeMember(memberUid)
    fun retryCloud() = cloudManager.retry()
    fun dismissCloudFeedback() = cloudManager.dismissFeedback()

    override fun onCleared() {
        deviceUiBridge?.let { CloudDeviceUiBridgeRegistry.unregister(it) }
        cloudFallbackObserver?.clear()
        gatewaySync?.clear()
        CloudUiBridgeRegistry.unregister(cloudManager)
        cloudManager.clear()
        super.onCleared()
    }

    fun refresh() { viewModelScope.launch(Dispatchers.IO) { network.withLock { if(_loggedIn.value) refreshLocked() } } }
    private fun refreshLocked(): SensorData? {
        val s = _state.value
        val data = Esp32Client.readSensor(s.esp32Ip,s.deviceKey)
        if(data == null) _state.update { it.copy(esp32Online=false,connectionMessage=Esp32Client.lastError ?: "Không nhận được dữ liệu ESP32.") }
        else {
            applySensor(data)
            data.security?.let { security ->
                val boot = "${security.stream}:${security.boot}"
                if(!data.clockReady && clockSyncedBoot != boot && Esp32Client.syncClock(s.esp32Ip,s.deviceKey)) clockSyncedBoot = boot
                syncEventsLocked(security)
            }
        }
        return data
    }
    private fun syncEventsLocked(security: SecurityStatus) {
        val s = _state.value
        val cursor = if(s.eventStream == security.stream) s.eventCursor else 0L
        val batch = Esp32Client.readEvents(s.esp32Ip,cursor,s.deviceKey)
        if(batch == null || batch.stream != security.stream) {
            _state.update { it.copy(syncMessage=Esp32Client.lastError ?: "Chưa đồng bộ được nhật ký thiết bị.") }; return
        }
        val fresh = batch.events.filter { it.seq > cursor && s.deviceEvents.none { old -> old.id == it.id } }
        val events = mergeDeviceEvents(s.deviceEvents, fresh)
        val eventAlerts = fresh.mapNotNull { event ->
            val type = when (event.kind) {
                "motion_home", "motion_waiting" -> AlertType.MOTION
                "rain_started", "rain_cleared" -> AlertType.ENVIRONMENT
                "servo_overdue", "servo_blocked", "exit_waiting" -> AlertType.DOOR
                else -> return@mapNotNull null
            }
            val title = when (event.kind) {
                "rain_started" -> if (fresh.any { it.kind == "clothes_rain_retract" && it.boot == event.boot &&
                    it.seq == event.seq + 1 }) "Trời bắt đầu mưa · Giàn phơi đã thu vào" else "Trời bắt đầu mưa"
                "rain_cleared" -> "Trời đã tạnh mưa"
                else -> event.title
            }
            AlertItem(type, title, event.timeLabel, id = event.id, timestamp = event.timestamp)
        }
        _state.update {
            val incidents = mergeIncidentAlerts(it.alerts, fresh, events)
            it.copy(deviceEvents = events,
                alerts = (eventAlerts.asReversed() + incidents).distinctBy { alert -> alert.id }.take(100),
                eventStream = batch.stream, eventCursor = batch.next - 1,
                syncMessage = if (cursor < batch.oldest - 1) "ESP32 chỉ giữ 64 sự kiện gần nhất; một phần lịch sử cũ không còn trên thiết bị." else null,
                logs = (fresh.asReversed().map { e -> LogItem(if (e.kind.startsWith("motion_")) "motion" else "esp32", e.title, e.timeLabel) } + it.logs).take(200))
        }
        if (fresh.isNotEmpty()) cleanupEvidence()
        if (_state.value.notifyEnabled) {
            val currentIds = fresh.filter { it.boot == security.boot }.map { it.id }.toSet()
            eventAlerts.filter { it.id in currentIds }.forEach(::notifyAlert)
            val incidentKeys = fresh.filter {
                it.boot == security.boot && it.kind in setOf("motion_alarm", "proximity_alarm", "motion_entry", "alarm_silent")
            }.mapNotNull { it.incidentKey }.distinct()
            _state.value.alerts.filter { it.id in incidentKeys }.forEach(::notifyAlert)
        }
    }
    fun setDevice(type: DeviceType, on: Boolean) {
        val s = _state.value
        if(!_loggedIn.value || !s.esp32Online || s.modeBusy || s.ruleBusy || s.alarmSettingsBusy || s.mode == HomeMode.AWAY || s.security?.protecting == true || s.commands[type]?.phase == CommandPhase.SENDING) return
        _state.update { it.copy(commands=it.commands + (type to DeviceCommand(CommandPhase.SENDING,"Đang gửi lệnh…"))) }
        viewModelScope.launch(Dispatchers.IO) { network.withLock { if(_loggedIn.value) commandLocked(type,on) } }
    }
    private fun commandLocked(type: DeviceType, on: Boolean): Boolean {
        _state.update { it.copy(commands=it.commands + (type to DeviceCommand(CommandPhase.SENDING,"Đang chờ ESP32 xác nhận…"))) }
        val s = _state.value
        val accepted = Esp32Client.sendCommand(s.esp32Ip,type.key,on,s.deviceKey)
        val error = Esp32Client.lastError
        val data = refreshLocked()
        val reported = when(type) { DeviceType.LIGHT -> data?.light; DeviceType.DOOR -> data?.door; DeviceType.ALARM -> data?.alarm }
        val ok = accepted && reported == on
        val message = if(ok) {
            if(type==DeviceType.ALARM) alarmCommandFeedback(on,data?.hardware?.nearObject==true && data.hardware.proximityAlarmEnabled!=false,if (data?.security?.demoModes == true) 3 else data?.hardware?.alarmSettings?.durationSeconds ?: 60) else "ESP32 đã xác nhận"
        } else error ?: "Chưa xác nhận được trạng thái. Kiểm tra thiết bị rồi thử lại."
        _state.update { it.copy(commands=it.commands + (type to DeviceCommand(if(ok) CommandPhase.SUCCESS else CommandPhase.ERROR,message)),
            logs=(listOf(LogItem(type.key,"${s.deviceLabel(type)}: ${if(ok && type==DeviceType.ALARM) message else if(ok) (if(on) "Bật / Mở" else "Tắt / Đóng") else "Chưa xác nhận"}",nowString()))+it.logs).take(200)) }
        return ok
    }
    fun setMode(mode: HomeMode) {
        val s = _state.value
        if(mode != HomeMode.HOME && s.security?.demoModes != true) {
            _state.update {it.copy(modeMessage="Nạp firmware bản demo để dùng chế độ Ra ngoài.")};return
        }
        if(!_loggedIn.value || s.modeBusy || s.ruleBusy || s.alarmSettingsBusy || s.commands.values.any { it.phase == CommandPhase.SENDING }) return
        if(!s.esp32Online || s.security == null) {
            _state.update { it.copy(modeMessage="Cần kết nối firmware ESP32 v3 để sử dụng chế độ tự động.") }; return
        }
        _state.update { it.copy(modeBusy=true,modeMessage="Đang áp dụng ${mode.label.lowercase()}…") }
        viewModelScope.launch(Dispatchers.IO) {
            network.withLock {
                try { if(_loggedIn.value) applyModeLocked(mode) }
                finally { _state.update { it.copy(modeBusy=false) } }
            }
        }
    }
    private fun applyModeLocked(mode: HomeMode): Boolean {
        val s = _state.value
        val accepted = Esp32Client.sendMode(s.esp32Ip,mode,s.deviceKey)
        val error = Esp32Client.lastError
        val data = refreshLocked()
        val ok = accepted && data?.security?.mode == mode
        _state.update { it.copy(modeMessage=if(ok) "${mode.label}: ESP32 đã áp dụng" else error ?: "Chưa xác nhận được chế độ. Kiểm tra kết nối rồi thử lại.") }
        return ok
    }
    fun verifyLiveFace(name: String?) {
        faceAccess.observe(name?.takeIf { _loggedIn.value && it in _state.value.registeredUsers },SystemClock.elapsedRealtime())
    }
    fun openDoorForFace(name: String) {
        val s=_state.value
        if(!_loggedIn.value || !s.esp32Online || s.modeBusy || s.ruleBusy || s.clothesBusy || s.alarmSettingsBusy ||
            s.commands.values.any { it.phase==CommandPhase.SENDING } || name !in s.registeredUsers ||
            !faceAccess.canOpen(name,SystemClock.elapsedRealtime())) return
        _state.update { it.copy(modeBusy=true,faceDoorMessage="Đang gửi lệnh mở cửa…") }
        viewModelScope.launch(Dispatchers.IO) { network.withLock {
            try {
                if(!_loggedIn.value || name !in _state.value.registeredUsers || !faceAccess.consume(name,SystemClock.elapsedRealtime())) {
                    _state.update { it.copy(faceDoorMessage="Hãy quét lại khuôn mặt để mở cửa.") };return@withLock
                }
                // Firmware requires HOME before opening when AWAY protection is active.
                val ready=if(_state.value.security?.mode?.let {it!=HomeMode.HOME}==true) applyModeLocked(HomeMode.HOME) else true
                val ok=ready && commandLocked(DeviceType.DOOR,true)
                if (ok) addAlert(AlertType.DOOR, "Cửa mở bằng khuôn mặt · $name", sendNotification = false)
                _state.update { it.copy(faceDoorMessage=if(ok) "Đã mở cửa · $name" else
                    (if(!ready) it.modeMessage else it.commands[DeviceType.DOOR]?.message) ?: "Chưa mở được cửa. Hãy thử lại.") }
            } finally { _state.update { it.copy(modeBusy=false) } }
        } }
    }
    fun setClothesline(retracted: Boolean) = updateClothesline(retracted,null)
    fun setRainRetraction(enabled: Boolean) = updateClothesline(null,enabled)
    fun setAlarmSettings(settings: AlarmSettings) {
        val current = _state.value
        if(!_loggedIn.value || !current.esp32Online || current.hardware?.alarmSettings == null ||
            current.alarmSettingsBusy || current.modeBusy || current.ruleBusy || current.clothesBusy ||
            current.commands.values.any { it.phase == CommandPhase.SENDING } || settings.durationSeconds !in 5..60) return
        _state.update { it.copy(alarmSettingsBusy=true,alarmSettingsMessage="Đang lưu thiết lập báo động…") }
        viewModelScope.launch(Dispatchers.IO) { network.withLock {
            try {
                if(!_loggedIn.value) return@withLock
                val accepted = Esp32Client.sendAlarmSettings(current.esp32Ip,settings,current.deviceKey)
                val error = Esp32Client.lastError
                val reported = refreshLocked()?.hardware?.alarmSettings
                _state.update { it.copy(alarmSettingsMessage=if(accepted && reported == settings)
                    "Đã lưu trên ESP32. Thiết lập vẫn áp dụng khi đóng app."
                    else error ?: "Chưa xác nhận được thiết lập báo động. Hãy kết nối lại và thử lại.") }
            } finally { _state.update { it.copy(alarmSettingsBusy=false) } }
        } }
    }
    private fun updateClothesline(retracted: Boolean?, autoRain: Boolean?) {
        val s=_state.value
        if(!_loggedIn.value || !s.esp32Online || s.hardware?.hasClothesline!=true || s.clothesBusy || s.modeBusy || s.alarmSettingsBusy) return
        _state.update { it.copy(clothesBusy=true,clothesMessage="Đang chờ ESP32 xác nhận…") }
        viewModelScope.launch(Dispatchers.IO) { network.withLock {
            try {
                if(!_loggedIn.value) return@withLock
                val current=_state.value
                val accepted=if(retracted!=null) Esp32Client.sendClothesline(current.esp32Ip,retracted,current.deviceKey)
                    else Esp32Client.sendRainRetraction(current.esp32Ip,autoRain!!,current.deviceKey)
                val error=Esp32Client.lastError
                val hardware=refreshLocked()?.hardware
                val ok=accepted && (if(retracted!=null) hardware?.clothesRetracted==retracted else hardware?.rainRetractEnabled==autoRain)
                val message=if(ok) { if(retracted!=null) "ESP32 đã xác nhận vị trí ${if(retracted) "thu vào" else "phơi ra"}." else "ESP32 đã lưu thiết lập tự thu khi mưa." }
                    else error ?: "Chưa xác nhận được trạng thái giàn phơi. Kiểm tra kết nối rồi thử lại."
                _state.update { it.copy(clothesMessage=message) }
            } finally { _state.update { it.copy(clothesBusy=false) } }
        } }
    }
    fun setRules(night: Boolean, door: Boolean) {
        val s = _state.value
        if(!s.esp32Online || s.ruleBusy || s.modeBusy) return
        _state.update { it.copy(ruleBusy=true) }
        viewModelScope.launch(Dispatchers.IO) { network.withLock {
            try {
                val accepted = Esp32Client.sendRules(s.esp32Ip,night,door,s.deviceKey)
                val error = Esp32Client.lastError
                val data = refreshLocked()
                val ok = accepted && data?.nightLight == night && data.doorReminder == door
                _state.update { it.copy(connectionMessage=if(ok) null else error ?: "Chưa lưu được quy tắc.") }
            } finally { _state.update { it.copy(ruleBusy=false) } }
        } }
    }
    fun setTiming(exit: Int, entry: Int, hold: Int) {
        val s = _state.value
        if(!s.esp32Online || s.security == null || s.security.protecting || s.ruleBusy || s.modeBusy || listOf(exit,entry,hold).any { it !in 5..120 }) return
        _state.update { it.copy(ruleBusy=true) }
        viewModelScope.launch(Dispatchers.IO) { network.withLock {
            try {
                val accepted = Esp32Client.sendTiming(s.esp32Ip,exit,entry,hold,s.deviceKey)
                val error = Esp32Client.lastError
                val data = refreshLocked()?.security
                val ok = accepted && data?.exitSeconds == exit && data.entrySeconds == entry && data.holdSeconds == hold
                _state.update { it.copy(connectionMessage=if(ok) "ESP32 đã lưu thời gian." else error ?: "Chưa xác nhận được thiết lập thời gian.") }
            } finally { _state.update { it.copy(ruleBusy=false) } }
        } }
    }
    private fun applySensor(d: SensorData) {
        val now = System.currentTimeMillis()
        val previous = _state.value
        val motionStarted = d.motion && !previous.motionDetected
        val sample = if(d.temp != null && d.hum != null && now - lastSample >= 60_000) SensorSample(now, d.temp, d.hum) else null
        if(sample != null) { storage.recordSample(sample); lastSample = now }
        _state.update {
            it.copy(temperature = d.temp, humidity = d.hum, motionDetected = d.motion,
                light = d.light ?: it.light, doorOpen = d.door ?: it.doorOpen, alarm = d.alarm ?: it.alarm,
                esp32Online = true, lastSeen = now, connectionMessage = null,
                rulesSupported = d.nightLight != null && d.doorReminder != null,
                nightLightEnabled = d.nightLight ?: false, doorReminderEnabled = d.doorReminder ?: false,
                clockReady = d.clockReady, security = d.security, hardware = d.hardware,
                faceDoorMessage=if(previous.doorOpen && d.door==false) null else it.faceDoorMessage,
                mode = d.security?.mode ?: it.mode, monitoring = d.security?.protecting ?: it.monitoring,
                samples = if(sample == null) it.samples else (it.samples + sample).filter { p -> now - p.timestamp <= HISTORY_WINDOW },
                liveSamples=if(d.temp!=null && d.hum!=null) (it.liveSamples+SensorSample(now,d.temp,d.hum)).filter {p->now-p.timestamp<=60_000}.takeLast(60) else it.liveSamples,
                logs = if(motionStarted && d.security?.demoModes != true) (listOf(LogItem("motion", "Phát hiện chuyển động", nowString())) + it.logs).take(200) else it.logs)
        }
        if (d.security?.demoModes != true && motionStarted &&
            d.security?.protecting != true &&
            (lastMotionAlert == 0L || SystemClock.elapsedRealtime() - lastMotionAlert > 1_000)) {
            lastMotionAlert = SystemClock.elapsedRealtime()
            addAlert(AlertType.MOTION, "Phát hiện chuyển động trước cửa")
        }
        if(d.security == null && d.doorOverdue && !overdueHandled && d.doorReminder == true) {
            overdueHandled = true
            addAlert(AlertType.DOOR, "Cửa đang ở trạng thái mở quá 2 phút")
        }
        if(!d.doorOverdue) overdueHandled = false
        val elapsed=SystemClock.elapsedRealtime()
        if(d.hardware?.nearObject==true && d.hardware.proximityAlarmEnabled!=false && previous.proximityAlert!=true &&
            (lastProximityAlert==0L || elapsed-lastProximityAlert>=60_000)) {
            lastProximityAlert=elapsed
            addAlert(AlertType.DOOR,"Phát hiện vật gần cửa trong vùng 2–10 cm")
        }
        val config = _state.value
        environmentGate.evaluate(config.environmentAlerts, d.temp, d.hum, config.temperatureLimit,
            config.coldLimit, config.humidityLimit).forEach { addAlert(AlertType.ENVIRONMENT, it) }
    }
    private fun addAlert(type: AlertType, title: String, imagePath: String? = null, sendNotification: Boolean = true) {
        val alert = AlertItem(type, title, nowString(), imagePath = imagePath)
        _state.update { it.copy(alerts = (listOf(alert) + it.alerts).take(100)) }
        if (sendNotification && _state.value.notifyEnabled) notifyAlert(alert)
        cleanupEvidence()
    }
    private fun notifyAlert(alert: AlertItem) {
        notifier.notify(alert.title, "Chạm để xem trong Nhật ký.", alert.id)
    }

    private fun cleanupEvidence() {
        val retained=_state.value.alerts.mapNotNull { it.imagePath }.toSet()
        File(getApplication<Application>().filesDir,"evidence").listFiles()?.forEach {
            if(it.absolutePath !in retained) it.delete()
        }
    }
    fun reviewAlert(id: String) { _state.update { it.copy(alerts = it.alerts.map { a -> if(a.id == id) a.copy(reviewed = true) else a }) } }
    fun reviewAll() { _state.update { it.copy(alerts = it.alerts.map { a -> a.copy(reviewed = true) }) } }
    fun setRegisteredUsers(names: List<String>) { _state.update { it.copy(registeredUsers = names) } }
    fun registerFace(name: String) { _state.update { it.copy(registeredUsers = (it.registeredUsers + name).distinct()) } }
    fun removeFace(name: String) {
        verifyLiveFace(null)
        com.example.smarthomeai.face.FaceStore(getApplication()).remove(name)
        _state.update { it.copy(registeredUsers = it.registeredUsers - name) }
    }
    fun onFaceRecognized(name: String?, similarity: Int, image: Bitmap?) {
        if(!_loggedIn.value) return
        _state.update { it.copy(
            recognizedName = name ?: "Người lạ", recognizedAccuracy = similarity,
            faces = (listOf(FaceRecord(name ?: "Người lạ", nowString(), name != null)) + it.faces).take(100))
        }
        if(name == null) {
            viewModelScope.launch(Dispatchers.IO) {
                val path = image?.let { bitmap ->
                    runCatching {
                        val dir = File(getApplication<Application>().filesDir, "evidence").apply { mkdirs() }
                        val file = File(dir, "${java.util.UUID.randomUUID()}.jpg")
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        file.absolutePath
                    }.getOrNull()
                }
                addAlert(AlertType.STRANGER, "Phát hiện người lạ", path)
                if(_state.value.security == null && _state.value.autoAlarm && _state.value.monitoring && _loggedIn.value) setDevice(DeviceType.ALARM, true)
            }
        }
    }
    fun setEsp32Ip(ip: String) { setConnection(ip,_state.value.deviceKey) }
    fun setConnection(ip: String, key: String) {
        verifyLiveFace(null)
        if(!Esp32Client.validIp(ip) || (key.isNotEmpty() && !Esp32Client.validKey(key))) return
        if(_state.value.modeBusy || _state.value.ruleBusy || _state.value.clothesBusy || _state.value.alarmSettingsBusy || _state.value.commands.values.any { it.phase == CommandPhase.SENDING }) return
        viewModelScope.launch(Dispatchers.IO) { network.withLock {
            if (_state.value.esp32Ip != ip) environmentGate = EnvironmentEvents()
            _state.update { it.copy(esp32Ip=ip,deviceKey=key,esp32Online=false,lastSeen=0,temperature=null,humidity=null,
                security=null,hardware=null,rulesSupported=false,commands=emptyMap(),connectionMessage=null,syncMessage=null,faceDoorMessage=null,clothesMessage=null,alarmSettingsMessage=null,liveSamples=emptyList()) }
            if(_loggedIn.value) refreshLocked()
        } }
    }
    fun setAutoAlarm(enabled: Boolean) { _state.update { it.copy(autoAlarm = enabled) } }
    fun setNotify(enabled: Boolean) { _state.update { it.copy(notifyEnabled = enabled) } }
    fun setEnvironmentAlerts(enabled: Boolean, temperature: Int, humidity: Int, cold: Int) {
        _state.update { it.copy(environmentAlerts = enabled, temperatureLimit = temperature.coerceIn(20, 50),
            humidityLimit = humidity.coerceIn(40, 95), coldLimit = cold.coerceIn(0, 19)) }
    }
}

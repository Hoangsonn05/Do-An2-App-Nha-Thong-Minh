package com.example.smarthomeai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.smarthomeai.data.SmartHomeViewModel
import com.example.smarthomeai.data.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.smarthomeai.face.FaceStore
import com.example.smarthomeai.ui.screens.*
import com.example.smarthomeai.ui.theme.SmartHomeTheme
import com.example.smarthomeai.ui.components.*

class MainActivity : ComponentActivity() {
    private val pendingAlert = MutableStateFlow<String?>(null)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        pendingAlert.value = intent.getStringExtra(Notifier.EXTRA_ALERT_ID)
        setContent {
            val alert by pendingAlert.collectAsState()
            SmartHomeTheme { AppRoot(pendingAlert = alert, onAlertOpened = { pendingAlert.value = null }) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAlert.value = intent.getStringExtra(Notifier.EXTRA_ALERT_ID)
    }
}
@Composable
fun AppRoot(vm: SmartHomeViewModel = viewModel(), pendingAlert: String? = null,
            onAlertOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    val loggedIn by vm.loggedIn.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val owner = rememberOwnerConfirmation(state.deviceKey, state.esp32Online && state.hardware?.authRequired == false) { action ->
        when(action) {
            "DOOR" -> vm.setDevice(com.example.smarthomeai.data.DeviceType.DOOR,true)
            "HOME" -> vm.setMode(com.example.smarthomeai.data.HomeMode.HOME)
            "SLEEP" -> vm.setMode(com.example.smarthomeai.data.HomeMode.SLEEP)
            "AWAY" -> vm.setMode(com.example.smarthomeai.data.HomeMode.AWAY)
        }
    }
    LaunchedEffect(Unit) { vm.setRegisteredUsers(FaceStore(context).names()) }
    var selectedAlert by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(loggedIn, pendingAlert) {
        if (loggedIn && pendingAlert != null) {
            selectedAlert = pendingAlert
            nav.navigateTab("alerts")
            onAlertOpened()
        }
    }
    fun go(route: String) {
        selectedAlert = null
        if (route in setOf("home", "camera", "alerts", "settings")) nav.navigateTab(route)
        else nav.navigate(route) { launchSingleTop = true }
    }
    CompositionLocalProvider(LocalSafetyBanner provides if(loggedIn) SafetyBanner(state) { owner("HOME") } else null) {
    NavHost(nav, startDestination = if(loggedIn) "home" else "login") {
        composable("login") {
            LoginScreen(!vm.firebaseConfigured,
                { email, pass, register, done -> if(register) vm.register(email,pass,done) else vm.login(email,pass,done) },
                { done -> vm.loginWithGoogle(activity,done) },
                { nav.navigate("home") { popUpTo("login") { inclusive = true } } })
        }
        composable("home") { HomeScreen(state, {type,on ->
            if(type == com.example.smarthomeai.data.DeviceType.DOOR && on) owner("DOOR") else vm.setDevice(type,on)
        }, { mode -> if(state.security?.protecting == true) owner(mode.name) else vm.setMode(mode) }, vm::refresh, ::go,vm::setClothesline,vm::setRainRetraction) }
        composable("env") { EnvironmentScreen(state,vm::refresh,::go,vm::setEnvironmentAlerts,{ nav.popBackStack() }) }
        composable("system") { SystemScreen(state,vm.firebaseConfigured,vm::refresh,{ nav.popBackStack() },::go) }
        composable("camera") { CameraScreen(state,vm::onFaceRecognized,{ go("register") },::go,vm::openDoorForFace,vm::verifyLiveFace) }
        composable("register") { FaceRegisterScreen({ nav.popBackStack() },vm::registerFace) }
        composable("alerts") { AlertsScreen(state,vm::reviewAlert,vm::reviewAll,::go,selectedAlert) }
        composable("settings") { SettingsScreen(state,vm::setEsp32Ip,vm::setAutoAlarm,vm::setNotify,vm::removeFace,
            { vm.logout(); nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } } },
            vm::refresh,{ nav.popBackStack() },::go,vm::setConnection,vm::setEnvironmentAlerts, { vm.setRules(false, it) }) }
    }
    }
}
private fun NavHostController.navigateTab(route: String) {
    navigate(route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
}

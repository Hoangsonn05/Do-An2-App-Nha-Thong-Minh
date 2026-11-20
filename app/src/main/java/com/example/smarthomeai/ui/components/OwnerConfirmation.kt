package com.example.smarthomeai.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.smarthomeai.data.Esp32Client

/** Confirmation for manual controls outside the live camera recognition flow. */
@Composable
fun rememberOwnerConfirmation(deviceKey: String, allowLanConfirmation: Boolean = false, onConfirmed: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val currentAction by rememberUpdatedState(onConfirmed)
    val currentLanConfirmation by rememberUpdatedState(allowLanConfirmation)
    val currentKey by rememberUpdatedState(deviceKey)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf("") }
    var fallback by rememberSaveable { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = pending
        pending = null
        if(result.resultCode == Activity.RESULT_OK && action != null && currentKey == pendingKey) currentAction(action)
    }
    if(fallback) AlertDialog(onDismissRequest = { fallback=false; pending=null; typed="" },
        title = { Text(if(currentLanConfirmation) "Xác nhận thao tác" else "Xác nhận chủ nhà") }, text = {
            Column {
                if(currentLanConfirmation) Text("Thiết bị đang dùng kết nối LAN bằng IP, không có khóa. Bạn xác nhận mở cửa hoặc thay đổi bảo vệ? Đây là xác nhận thao tác, không xác minh danh tính.")
                else {
                Text("Điện thoại chưa đặt khóa màn hình. Nhập khóa ESP32 để xác nhận thao tác mở cửa hoặc tắt bảo vệ.")
                OutlinedTextField(typed,{typed=it},label={Text("Khóa ESP32")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                }
            }
        }, confirmButton = { TextButton(onClick = {
            val action = pending
            fallback=false; pending=null; typed=""
            if(action != null && currentKey == pendingKey) currentAction(action)
        }, enabled=(currentLanConfirmation || (Esp32Client.validKey(currentKey) && typed == currentKey)) && currentKey == pendingKey) { Text("Xác nhận") } },
        dismissButton = { TextButton(onClick = {fallback=false; pending=null; typed=""}) {Text("Hủy")} })
    return { action ->
        pending=action; pendingKey=deviceKey; typed=""
        @Suppress("DEPRECATION")
        val intent=if(keyguard.isDeviceSecure) keyguard.createConfirmDeviceCredentialIntent("Xác nhận chủ nhà","Mở cửa hoặc thay đổi chế độ bảo vệ") else null
        if(intent != null) launcher.launch(intent) else fallback=true
    }
}

package com.example.smarthomeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarthomeai.ui.components.*
import com.example.smarthomeai.ui.theme.*

@Composable
fun LoginScreen(demoMode: Boolean, onSubmit: (String,String,Boolean,(String?) -> Unit) -> Unit,
                onGoogle: ((String?) -> Unit) -> Unit, onSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val done: (String?) -> Unit = { busy = false; error = it; if(it == null) onSuccess() }
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Home, Blue600, Blue50, 40.dp)
            Text("Smart Home", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
            Text(if(register) "Tạo tài khoản" else "Đăng nhập",style = MaterialTheme.typography.headlineMedium)
            Text(if(register) "Một bước để bắt đầu quản lý ngôi nhà." else "Quản lý thiết bị và theo dõi ngôi nhà.",style = MaterialTheme.typography.bodyMedium,color = TextGray)
            OutlinedTextField(email,{ email = it },label = { Text("Email") },leadingIcon = { Icon(Icons.Default.AlternateEmail,null) },
                singleLine = true,enabled = !busy,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password,{ password = it },label = { Text("Mật khẩu") },leadingIcon = { Icon(Icons.Default.Lock,null) },
                trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if(show) Icons.Default.VisibilityOff else Icons.Default.Visibility,if(show) "Ẩn mật khẩu" else "Hiện mật khẩu") } },
                visualTransformation = if(show) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,enabled = !busy,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),modifier = Modifier.fillMaxWidth())
            error?.let { Text(it,color = RedAlert,style = MaterialTheme.typography.bodyMedium) }
            if(demoMode) Text("Bản trình diễn tài khoản: nhập email và mật khẩu để trải nghiệm. Chưa có xác thực Firebase.",
                color = OrangeWarn,style = MaterialTheme.typography.bodySmall)
            PrimaryButton(if(busy) "Đang xử lý…" else if(register) "Tạo tài khoản" else "Đăng nhập",enabled = !busy) {
                busy = true; error = null; onSubmit(email,password,register,done)
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!demoMode) OutlinedButton(onClick = { busy = true; error = null; onGoogle(done) },enabled = !busy && !demoMode,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.AccountCircle,null); Spacer(Modifier.width(10.dp)); Text("Tiếp tục với Google")
            }
            TextButton(onClick = { register = !register; error = null },enabled = !busy,modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if(register) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
            }
    }
}

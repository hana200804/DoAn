package com.signa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.signa.core.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Cài đặt (FR-13) và tài khoản (FR-12). */
@Composable
fun SettingsScreen() {
    val app = LocalContext.current.app
    val prefs = app.prefs
    var vehicle by remember { mutableStateOf(prefs.vehicle) }
    var voice by remember { mutableStateOf(prefs.voice) }
    var contribute by remember { mutableStateOf(prefs.contribute) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var loggedIn by remember { mutableStateOf(prefs.session != null) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Loại xe")
        Row {
            listOf(VehicleType.CAR to "Ô tô con", VehicleType.BUS to "Xe khách", VehicleType.TRUCK to "Xe tải").forEach { (v, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(vehicle == v, { vehicle = v; prefs.vehicle = v })
                    Text(label)
                }
            }
        }
        Toggle("Cảnh báo bằng giọng nói", voice) { voice = it; prefs.voice = it }
        Toggle("Đóng góp dữ liệu cho bản đồ chung", contribute) { contribute = it; prefs.contribute = it }
        Toggle("Chỉ gửi dữ liệu qua Wi-Fi", wifiOnly) { wifiOnly = it; prefs.wifiOnly = it }
        Text("Quan sát chờ gửi: ${app.queue.size()}")
        HorizontalDivider()
        if (loggedIn) {
            Account(onLogout = { loggedIn = false })
        } else {
            AuthForm(onLogin = { loggedIn = true })
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked, onChange)
    }
}

/** Gọi API trên luồng nền, trả thông báo lỗi hoặc null nếu thành công. */
private suspend fun call(block: () -> Unit): String? =
    withContext(Dispatchers.IO) { runCatching(block).exceptionOrNull()?.message }

@Composable
private fun Account(onLogout: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Text("Đang đăng nhập: ${app.prefs.displayName.orEmpty()}")
    Text("Đổi mật khẩu")
    OutlinedTextField(oldPw, { oldPw = it }, label = { Text("Mật khẩu cũ") }, visualTransformation = PasswordVisualTransformation())
    OutlinedTextField(newPw, { newPw = it }, label = { Text("Mật khẩu mới (≥ 8 ký tự)") }, visualTransformation = PasswordVisualTransformation())
    Button(onClick = {
        scope.launch {
            message = call { app.api.request("PUT", "/users/me/password", JSONObject().put("oldPassword", oldPw).put("newPassword", newPw)) }
                ?: "Đã đổi mật khẩu"
        }
    }) { Text("Đổi mật khẩu") }
    message?.let { Text(it) }
    TextButton(onClick = {
        scope.launch {
            call { app.api.post("/auth/logout") }
            app.prefs.session = null
            onLogout()
        }
    }) { Text("Đăng xuất") }
}

@Composable
private fun AuthForm(onLogin: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun login() = scope.launch {
        message = call {
            val res = JSONObject(app.api.post("/auth/login", JSONObject().put("email", email).put("password", password)))
            app.prefs.session = res.getString("sessionId")
            app.prefs.displayName = res.getJSONObject("user").getString("displayName")
        }
        if (message == null) onLogin()
    }

    Text("Đăng nhập để đóng góp dữ liệu và báo cáo biển sai. Không đăng nhập vẫn dùng được nhận diện và cảnh báo.")
    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
    when (mode) {
        "login" -> {
            OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { login() }) { Text("Đăng nhập") }
        }
        "register" -> {
            OutlinedTextField(name, { name = it }, label = { Text("Tên hiển thị") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu (≥ 8 ký tự)") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = {
                scope.launch {
                    message = call { app.api.post("/auth/register", JSONObject().put("email", email).put("password", password).put("displayName", name)) }
                    if (message == null) login()
                }
            }) { Text("Đăng ký") }
        }
        else -> {
            Button(onClick = {
                scope.launch { message = call { app.api.post("/auth/password/forgot", JSONObject().put("email", email)) } ?: "Nếu email đã đăng ký, mã OTP đã được gửi" }
            }) { Text("Gửi mã OTP") }
            OutlinedTextField(otp, { otp = it }, label = { Text("Mã OTP") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu mới") }, visualTransformation = PasswordVisualTransformation())
            Button(onClick = {
                scope.launch {
                    message = call { app.api.post("/auth/password/reset", JSONObject().put("email", email).put("otp", otp).put("newPassword", password)) }
                        ?: "Đã đặt lại mật khẩu, hãy đăng nhập"
                    if (message?.startsWith("Đã") == true) mode = "login"
                }
            }) { Text("Đặt lại mật khẩu") }
        }
    }
    message?.let { Text(it) }
    Row {
        if (mode != "login") TextButton({ mode = "login" }) { Text("Đăng nhập") }
        if (mode != "register") TextButton({ mode = "register" }) { Text("Tạo tài khoản") }
        if (mode != "forgot") TextButton({ mode = "forgot" }) { Text("Quên mật khẩu") }
    }
}

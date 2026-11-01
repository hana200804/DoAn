package com.signa.app.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.signa.app.R
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SignaSectionCard

@Composable
fun AuthScreen(
    mode: String = "login",
    onRegister: () -> Unit = {},
    onForgot: () -> Unit = {},
    onReset: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    // Form data stays in memory; do not persist passwords in saved state.
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    val title = when (mode) {
        "register" -> R.string.register
        "forgot" -> R.string.forgot_password
        "reset" -> R.string.reset_password
        "change" -> R.string.change_password
        else -> R.string.login
    }
    SignaPage(stringResource(title), Modifier.imePadding()) {
        if (mode != "login") {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        Text(stringResource(R.string.auth_demo_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SignaSectionCard(stringResource(R.string.account_information)) {
            if (mode == "register") {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (mode != "change") {
                OutlinedTextField(
                    email, { email = it }, label = { Text(stringResource(R.string.email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            if (mode == "reset") {
                OutlinedTextField(
                    otp, { otp = it }, label = { Text(stringResource(R.string.otp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            if (mode == "change") {
                OutlinedTextField(
                    oldPassword, { oldPassword = it }, label = { Text(stringResource(R.string.old_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            if (mode != "forgot") {
                OutlinedTextField(
                    password, { password = it }, label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            if (mode in listOf("register", "reset", "change")) {
                OutlinedTextField(
                    confirmation, { confirmation = it }, label = { Text(stringResource(R.string.confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            SignaPrimaryButton(
                stringResource(if (mode == "forgot") R.string.send_otp else title), {},
                Modifier.fillMaxWidth(), enabled = false
            )
        }
        if (mode == "login") {
            TextButton(onClick = onRegister) { Text(stringResource(R.string.register)) }
            TextButton(onClick = onForgot) { Text(stringResource(R.string.forgot_password)) }
            TextButton(onClick = onChangePassword) { Text(stringResource(R.string.preview_change_password)) }
            Text(stringResource(R.string.guest_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (mode == "forgot") {
            TextButton(onClick = onReset) { Text(stringResource(R.string.preview_reset_password)) }
        }
    }
}

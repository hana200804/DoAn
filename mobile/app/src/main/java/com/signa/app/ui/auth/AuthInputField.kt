package com.signa.app.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.signa.app.R

@Composable
internal fun AuthInputField(
    mode: AuthMode, field: AuthField, value: String, error: AuthFieldError?,
    onValueChange: (String) -> Unit, enabled: Boolean = true
) {
    var passwordVisible by remember(field) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val isPassword = field in listOf(AuthField.Password, AuthField.Confirmation, AuthField.OldPassword)
    val label = stringResource(when (field) {
        AuthField.DisplayName -> R.string.display_name
        AuthField.Email -> R.string.email
        AuthField.OldPassword -> R.string.old_password
        AuthField.Confirmation -> R.string.confirm_password
        AuthField.Otp -> R.string.otp
        AuthField.Password -> if (mode == AuthMode.ChangePassword || mode == AuthMode.ResetPassword) R.string.new_password else R.string.password
    })
    val placeholder = stringResource(when (field) {
        AuthField.DisplayName -> R.string.hint_display_name
        AuthField.Email -> R.string.hint_email
        AuthField.OldPassword -> R.string.hint_old_password
        AuthField.Confirmation -> R.string.hint_confirm_password
        AuthField.Otp -> R.string.otp
        AuthField.Password -> if (mode == AuthMode.ChangePassword || mode == AuthMode.ResetPassword) R.string.hint_new_password else R.string.hint_password
    })
    val errorMessage = authFieldErrorMessage(error)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(enabled = enabled) { focus.requestFocus() })
        OutlinedTextField(
            value = value, onValueChange = onValueChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(10.dp), singleLine = true, isError = error != null,
            leadingIcon = {
                Icon(painterResource(when (field) {
                    AuthField.Email -> R.drawable.ic_mail
                    AuthField.DisplayName -> R.drawable.ic_account
                    else -> R.drawable.ic_lock
                }), null, Modifier.size(21.dp))
            },
            trailingIcon = if (isPassword) { {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(painterResource(R.drawable.ic_eye), stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password), Modifier.size(22.dp))
                }
            } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = when {
                isPassword -> KeyboardType.Password
                field == AuthField.Email -> KeyboardType.Email
                else -> KeyboardType.Text
            }),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline)
        )
        if (errorMessage != null) Text(stringResource(errorMessage), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        else if (mode == AuthMode.Register && field == AuthField.Password) {
            Text(stringResource(R.string.password_minimum_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun authFieldErrorMessage(error: AuthFieldError?): Int? = when (error) {
    AuthFieldError.Required -> R.string.auth_error_required
    AuthFieldError.InvalidEmail -> R.string.auth_error_email
    AuthFieldError.PasswordTooShort -> R.string.auth_error_password
    AuthFieldError.PasswordMismatch -> R.string.auth_error_confirmation
    AuthFieldError.InvalidOtp -> R.string.auth_error_otp
    null -> null
}

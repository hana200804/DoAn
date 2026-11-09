package com.signa.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.signa.app.R
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.components.SignaWordmark

@Composable
fun AuthScreen(
    mode: AuthMode, state: AuthUiState, onFieldChange: (AuthField, String) -> Unit,
    onSubmit: () -> Unit, onBack: () -> Unit = {}, onRegister: () -> Unit = {},
    onForgot: () -> Unit = {}, onReset: () -> Unit = {}, onGuest: () -> Unit = {},
    isFakeBackend: Boolean = true, onResend: () -> Unit = {}
) {
    val isRecovery = mode == AuthMode.ForgotPassword || mode == AuthMode.ResetPassword
    val enabled = !state.isSubmitting
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == AuthMode.Login) {
                Spacer(Modifier.height(32.dp))
                SignaWordmark()
                Spacer(Modifier.height(32.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                    if (mode == AuthMode.Register) SignaWordmark()
                }
                Spacer(Modifier.height(if (mode == AuthMode.Register) 16.dp else 24.dp))
            }
            if (mode == AuthMode.ForgotPassword) {
                RecoveryIllustration(Modifier.size(132.dp))
                Spacer(Modifier.height(24.dp))
            }
            Text(stringResource(when (mode) {
                AuthMode.Login -> R.string.welcome_back
                AuthMode.Register -> R.string.create_account
                AuthMode.ForgotPassword -> R.string.forgot_password_title
                AuthMode.ResetPassword -> R.string.reset_password
                AuthMode.ChangePassword -> R.string.change_password
            }), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (mode != AuthMode.Register && mode != AuthMode.ChangePassword) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(when (mode) {
                    AuthMode.ForgotPassword -> R.string.forgot_password_subtitle
                    AuthMode.ResetPassword -> R.string.reset_password_subtitle
                    else -> R.string.auth_subtitle
                }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(if (isRecovery) 40.dp else 28.dp))
            if (mode == AuthMode.ResetPassword) {
                if (state.form[AuthField.Email].isBlank()) {
                    AuthInputField(mode, AuthField.Email, state.form[AuthField.Email], state.errors[AuthField.Email], { onFieldChange(AuthField.Email, it) }, enabled)
                    Spacer(Modifier.height(16.dp))
                }
                OtpInput(state.form[AuthField.Otp], state.errors[AuthField.Otp], enabled) { onFieldChange(AuthField.Otp, it) }
                TextButton(onClick = onResend, enabled = enabled, modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(R.string.resend_otp), fontWeight = FontWeight.SemiBold)
                }
                state.resetChallenge?.demoCode?.let {
                    Text(stringResource(R.string.auth_demo_otp, it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                mode.fields.filterNot { mode == AuthMode.ResetPassword && (it == AuthField.Email || it == AuthField.Otp) }.forEach { field ->
                    AuthInputField(mode, field, state.form[field], state.errors[field], { onFieldChange(field, it) }, enabled)
                }
            }
            if (mode == AuthMode.Login) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onForgot, enabled = enabled) {
                        Text(stringResource(R.string.forgot_password_link), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(if (mode == AuthMode.Login) 28.dp else 32.dp))
            SignaPrimaryButton(
                stringResource(if (state.isSubmitting) R.string.submitting else when (mode) {
                    AuthMode.Login -> R.string.login
                    AuthMode.Register -> R.string.register
                    AuthMode.ForgotPassword -> R.string.send_confirmation
                    else -> R.string.confirm_action
                }), onSubmit, Modifier.fillMaxWidth(), enabled
            )
            state.requestError?.let {
                Text(stringResource(authErrorMessage(it)), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            when (mode) {
                AuthMode.Login -> {
                    AuthFooterLink(R.string.no_account_prompt, R.string.register, onRegister)
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = onGuest, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) { Text(stringResource(R.string.continue_guest), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                }
                AuthMode.Register -> AuthFooterLink(R.string.has_account_prompt, R.string.login, onBack)
                AuthMode.ForgotPassword -> {
                    state.resetChallenge?.let { challenge ->
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.auth_otp_sent), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        challenge.demoCode?.let { Text(stringResource(R.string.auth_demo_otp, it), color = MaterialTheme.colorScheme.primary) }
                        TextButton(onClick = onReset, enabled = enabled) { Text(stringResource(R.string.enter_otp)) }
                    }
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.back_to_login), fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> Unit
            }
            if (isFakeBackend) Text(
                stringResource(R.string.demo_mode_short), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AuthFooterLink(prompt: Int, action: Int, onClick: () -> Unit) {
    val promptText = stringResource(prompt)
    val actionText = stringResource(action)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(promptText); append(" ") }
            withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append(actionText) }
        }, textAlign = TextAlign.Center)
    }
}

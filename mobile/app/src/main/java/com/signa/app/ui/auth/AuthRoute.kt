package com.signa.app.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signa.app.BuildConfig

@Composable
fun AuthRoute(
    mode: AuthMode,
    onSignedIn: () -> Unit = {},
    onRegistered: () -> Unit = {},
    onPasswordReset: () -> Unit = {},
    onPasswordChanged: () -> Unit = {},
    onBack: () -> Unit = {},
    onRegister: () -> Unit = {},
    onForgot: () -> Unit = {},
    onReset: (String) -> Unit = {},
    onGuest: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestSignedIn by rememberUpdatedState(onSignedIn)
    val latestRegistered by rememberUpdatedState(onRegistered)
    val latestReset by rememberUpdatedState(onPasswordReset)
    val latestChanged by rememberUpdatedState(onPasswordChanged)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.SignedIn -> latestSignedIn()
                AuthEvent.Registered -> latestRegistered()
                AuthEvent.PasswordReset -> latestReset()
                AuthEvent.PasswordChanged -> latestChanged()
            }
        }
    }
    AuthScreen(
        mode = mode,
        state = state,
        onFieldChange = viewModel::setField,
        onSubmit = { viewModel.submit(mode) },
        onBack = onBack,
        onRegister = onRegister,
        onForgot = onForgot,
        onReset = { onReset(state.resetChallenge?.email.orEmpty()) },
        onGuest = onGuest,
        onResend = viewModel::resendOtp,
        isFakeBackend = BuildConfig.USE_FAKE_BACKEND
    )
}

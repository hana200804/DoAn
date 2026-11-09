package com.signa.app.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.signa.app.R
import com.signa.app.ui.components.SignaPage
import com.signa.app.ui.components.SignaPrimaryButton
import com.signa.app.ui.auth.authErrorMessage

@Composable
fun AccountScreen(
    state: AccountUiState,
    onLogin: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    onContributions: () -> Unit,
    onChangePassword: () -> Unit
) {
    val user = state.user
    if (!state.isLoading && user != null) {
        ProfileScreen(
            user, onLogout, onContributions, onChangePassword,
            errorMessage = state.operationError?.let(::authErrorMessage),
            isSigningOut = state.isSigningOut,
            vehicleType = state.vehicleType
        )
        return
    }
    SignaPage(stringResource(R.string.nav_account)) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.hasError -> Text(stringResource(R.string.data_load_error))
            else -> {
                AccountHeader("S", stringResource(R.string.guest_name), stringResource(R.string.guest_profile_hint))
                Text(stringResource(R.string.guest_account_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SignaPrimaryButton(stringResource(R.string.login), onLogin, Modifier.fillMaxWidth())
                OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.view_sample_profile))
                }
            }
        }
    }
}

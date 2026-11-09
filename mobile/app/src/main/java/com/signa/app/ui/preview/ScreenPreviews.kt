package com.signa.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.signa.app.ui.account.AccountScreen
import com.signa.app.ui.account.AccountUiState
import com.signa.app.ui.account.ProfileScreen
import com.signa.app.ui.auth.AuthField
import com.signa.app.ui.auth.AuthFormState
import com.signa.app.ui.contributions.ContributionsScreen
import com.signa.app.ui.contributions.ContributionsUiState
import com.signa.app.data.fake.FakeData
import com.signa.app.ui.auth.AuthMode
import com.signa.app.ui.auth.AuthScreen
import com.signa.app.ui.auth.AuthUiState
import com.signa.app.ui.map.MapScreen
import com.signa.app.ui.settings.SettingsScreen
import com.signa.app.ui.settings.SettingsUiState
import com.signa.app.ui.sign.SignDetailScreen
import com.signa.app.ui.sign.SignLookupScreen
import com.signa.app.ui.theme.SignaTheme

@Composable
private fun PreviewPage(content: @Composable () -> Unit) {
    SignaTheme {
        Scaffold { padding -> Box(Modifier.padding(padding)) { content() } }
    }
}

@Preview(showBackground = true, name = "Map")
@Preview(showBackground = true, name = "Map dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MapPreview() = PreviewPage { MapScreen(PreviewSamples.map, {}, {}) }

@Preview(showBackground = true, name = "Settings")
@Preview(showBackground = true, name = "Settings large text", fontScale = 1.5f)
@Composable
private fun SettingsPreview() = PreviewPage { SettingsScreen(SettingsUiState(), {}, {}, {}, {}) }

@Preview(showBackground = true, name = "Lookup")
@Composable
private fun LookupPreview() = PreviewPage { SignLookupScreen(PreviewSamples.lookup) }

@Preview(showBackground = true, name = "Sign detail")
@Composable
private fun DetailPreview() = PreviewPage { SignDetailScreen(PreviewSamples.detail, onBack = {}) }

@Preview(showBackground = true, name = "Login")
@Composable
private fun LoginPreview() = PreviewPage { AuthScreen(AuthMode.Login, AuthUiState(), { _, _ -> }, {}) }

@Preview(showBackground = true, name = "Register")
@Composable
private fun RegisterPreview() = PreviewPage { AuthScreen(AuthMode.Register, AuthUiState(), { _, _ -> }, {}) }

@Preview(showBackground = true, name = "Forgot password")
@Composable
private fun ForgotPreview() = PreviewPage { AuthScreen(AuthMode.ForgotPassword, AuthUiState(), { _, _ -> }, {}) }

@Preview(showBackground = true, name = "Reset password")
@Composable
private fun ResetPreview() = PreviewPage { AuthScreen(AuthMode.ResetPassword, AuthUiState(form = AuthFormState().withValue(AuthField.Email, PreviewSamples.profile.email)), { _, _ -> }, {}) }

@Preview(showBackground = true, name = "Change password")
@Composable
private fun ChangePreview() = PreviewPage { AuthScreen(AuthMode.ChangePassword, AuthUiState(), { _, _ -> }, {}) }

@Preview(showBackground = true, name = "Account")
@Preview(showBackground = true, name = "Account dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountPreview() = PreviewPage { ProfileScreen(PreviewSamples.profile, {}, {}, {}) }

@Preview(showBackground = true, name = "Guest account")
@Composable
private fun GuestAccountPreview() = PreviewPage { AccountScreen(AccountUiState(isLoading = false), {}, {}, {}, {}, {}) }

@Preview(showBackground = true, name = "Contributions")
@Composable
private fun ContributionsPreview() = PreviewPage {
    ContributionsScreen(ContributionsUiState(summary = FakeData.contributions, isLoading = false), {}, {})
}

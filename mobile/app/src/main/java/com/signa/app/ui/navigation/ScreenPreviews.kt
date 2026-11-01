package com.signa.app.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.signa.app.ui.auth.AuthScreen
import com.signa.app.ui.map.MapScreen
import com.signa.app.ui.settings.SettingsScreen
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
private fun MapPreview() = PreviewPage { MapScreen(onOpenSign = {}) }

@Preview(showBackground = true, name = "Settings")
@Preview(showBackground = true, name = "Settings large text", fontScale = 1.5f)
@Composable
private fun SettingsPreview() = PreviewPage { SettingsScreen() }

@Preview(showBackground = true, name = "Lookup")
@Composable
private fun LookupPreview() = PreviewPage { SignLookupScreen() }

@Preview(showBackground = true, name = "Sign detail")
@Composable
private fun DetailPreview() = PreviewPage { SignDetailScreen(onBack = {}) }

@Preview(showBackground = true, name = "Login")
@Composable
private fun LoginPreview() = PreviewPage { AuthScreen() }

@Preview(showBackground = true, name = "Register")
@Composable
private fun RegisterPreview() = PreviewPage { AuthScreen(mode = "register") }

@Preview(showBackground = true, name = "Forgot password")
@Composable
private fun ForgotPreview() = PreviewPage { AuthScreen(mode = "forgot") }

@Preview(showBackground = true, name = "Reset password")
@Composable
private fun ResetPreview() = PreviewPage { AuthScreen(mode = "reset") }

@Preview(showBackground = true, name = "Change password")
@Composable
private fun ChangePreview() = PreviewPage { AuthScreen(mode = "change") }

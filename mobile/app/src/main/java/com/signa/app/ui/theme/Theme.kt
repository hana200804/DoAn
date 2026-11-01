package com.signa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SignaBlue,
    onPrimary = Color.White,
    primaryContainer = SignaLightBlue,
    onPrimaryContainer = SignaNavy,
    secondary = SignaMuted,
    background = SignaBackground,
    onBackground = SignaNavy,
    surface = Color.White,
    onSurface = SignaNavy,
    surfaceVariant = Color(0xFFE9EFF7),
    onSurfaceVariant = SignaMuted,
    outline = SignaOutline,
    error = SignaRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAACBFF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF234571),
    onPrimaryContainer = SignaLightBlue,
    secondary = Color(0xFFBAC8DB),
    background = Color(0xFF101820),
    onBackground = Color(0xFFE2EAF5),
    surface = Color(0xFF192431),
    onSurface = Color(0xFFE2EAF5),
    surfaceVariant = Color(0xFF283645),
    onSurfaceVariant = Color(0xFFBAC8DB),
    outline = Color(0xFF66788D),
    error = Color(0xFFFFB4AB)
)

@Composable
fun SignaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = SignaShapes,
        content = content
    )
}

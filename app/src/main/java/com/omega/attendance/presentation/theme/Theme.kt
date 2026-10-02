package com.omega.attendance.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B), onPrimary = Color.White,
    secondary = Color(0xFF4D635D), background = Color(0xFFF7F9FC),
    surface = Color.White, error = Color(0xFFBA1A1A)
)
private val DarkColors = darkColorScheme(primary = Color(0xFF84D5C0), secondary = Color(0xFFB3CCC4))

@Composable
fun OmegaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

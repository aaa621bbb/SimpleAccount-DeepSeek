package com.simpleaccount.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D8CF0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF0B3E6E),
    secondary = Color(0xFF45B7D1),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    onPrimary = Color(0xFF0B3E6E),
    primaryContainer = Color(0xFF234A72),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFF4ECDC4),
    onSecondary = Color(0xFF0B3E6E),
    background = Color(0xFF121417),
    surface = Color(0xFF1C1E21),
    onSurface = Color(0xFFE3E4E6),
)

@Composable
fun SimpleAccountTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

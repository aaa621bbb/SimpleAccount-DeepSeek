package com.simpleaccount.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 配色与 App 图标的蓝紫主色呼应
private val LightColors = lightColorScheme(
    primary = Color(0xFF4C5FD7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E5FF),
    onPrimaryContainer = Color(0xFF0E1A64),
    secondary = Color(0xFF5B7BD5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6FB),
    onSecondaryContainer = Color(0xFF1A2E60),
    tertiary = Color(0xFF7A9AE3),
    background = Color(0xFFF3F5FA),
    onBackground = Color(0xFF1A1C22),
    surface = Color.White,
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE9EDF6),
    onSurfaceVariant = Color(0xFF5B6070),
    outline = Color(0xFFE2E6F0),
    error = Color(0xFFD9363E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC5FF),
    onPrimary = Color(0xFF1A2A7A),
    primaryContainer = Color(0xFF37459C),
    onPrimaryContainer = Color(0xFFE1E5FF),
    secondary = Color(0xFFA8BCF0),
    onSecondary = Color(0xFF20304F),
    secondaryContainer = Color(0xFF33456E),
    onSecondaryContainer = Color(0xFFDCE6FB),
    tertiary = Color(0xFF8FA9DE),
    background = Color(0xFF0F1116),
    onBackground = Color(0xFFE2E4EC),
    surface = Color(0xFF181B22),
    onSurface = Color(0xFFE2E4EC),
    surfaceVariant = Color(0xFF242833),
    onSurfaceVariant = Color(0xFFA6ABC0),
    outline = Color(0xFF2B3040),
    error = Color(0xFFEF8B90),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SimpleAccountTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}

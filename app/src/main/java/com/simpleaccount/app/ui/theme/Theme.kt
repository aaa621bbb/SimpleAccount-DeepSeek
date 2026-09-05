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

/**
 * 冷白 + 松绿主色。支出陶土、收入翠绿，整页同一套灰阶，不再混香槟金和墨青。
 */
object AppColors {
    val Pine = Color(0xFF1F6F5B)
    val PineSoft = Color(0xFF2F8A72)
    val Sand = Color(0xFFE8A87C)
    val Expense = Color(0xFFD4523E)
    val Income = Color(0xFF2A9D6E)
    val Gold = Sand
    val Champagne = Sand
    val Ink = Pine
    val Sage = Income
    val Terracotta = Expense
    val Paper = Color(0xFFF4F6F8)
    val Cream = Color(0xFFFFFFFF)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EFE7),
    onPrimaryContainer = Color(0xFF0C3D32),
    secondary = Color(0xFF5B6B73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8EB),
    onSecondaryContainer = Color(0xFF243038),
    tertiary = Color(0xFF2A9D6E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F3E4),
    onTertiaryContainer = Color(0xFF0D3B28),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF1A1F22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F22),
    surfaceVariant = Color(0xFFEAEEF1),
    onSurfaceVariant = Color(0xFF5A646A),
    outline = Color(0xFFD5DCE1),
    outlineVariant = Color(0xFFE8EDF0),
    error = Color(0xFFD4523E),
    onError = Color.White,
    errorContainer = Color(0xFFF8D8D3),
    onErrorContainer = Color(0xFF4A1C16),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DCFB6),
    onPrimary = Color(0xFF07382C),
    primaryContainer = Color(0xFF1A4A3E),
    onPrimaryContainer = Color(0xFFD7EFE7),
    secondary = Color(0xFFB7C2C8),
    onSecondary = Color(0xFF1C2428),
    secondaryContainer = Color(0xFF2A3338),
    onSecondaryContainer = Color(0xFFE3E8EB),
    tertiary = Color(0xFF7ED9AE),
    onTertiary = Color(0xFF0C241C),
    tertiaryContainer = Color(0xFF1E3D32),
    onTertiaryContainer = Color(0xFFD4F3E4),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE6EBEE),
    surface = Color(0xFF181C1F),
    onSurface = Color(0xFFE6EBEE),
    surfaceVariant = Color(0xFF242A2E),
    onSurfaceVariant = Color(0xFFA8B2B8),
    outline = Color(0xFF3A4248),
    outlineVariant = Color(0xFF2A3136),
    error = Color(0xFFE08B7E),
    onError = Color(0xFF3A1410),
    errorContainer = Color(0xFF5C2A24),
    onErrorContainer = Color(0xFFF6D6D1),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
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

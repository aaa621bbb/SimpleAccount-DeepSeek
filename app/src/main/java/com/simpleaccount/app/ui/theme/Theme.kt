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
 * 高级感配色：暖纸白 + 墨青主色 + 香槟金点缀（私人银行/精品账本气质）。
 * 支出用陶土红、收入用松柏绿，避免荧光色。
 */
object AppColors {
    val Ink = Color(0xFF1F3A4D)
    val InkDeep = Color(0xFF162A38)
    val Champagne = Color(0xFFC2A06A)
    val ChampagneSoft = Color(0xFFE8D5B0)
    val Sage = Color(0xFF3D8F73)
    val Terracotta = Color(0xFFC45C4A)
    val Paper = Color(0xFFF5F1E9)
    val Cream = Color(0xFFFFFCF7)
    val Expense = Terracotta
    val Income = Sage
    val Gold = Champagne
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3A4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EBE8),
    onPrimaryContainer = Color(0xFF132530),
    secondary = Color(0xFFC2A06A),
    onSecondary = Color(0xFF2A1F0C),
    secondaryContainer = Color(0xFFF3E6CF),
    onSecondaryContainer = Color(0xFF3A2C14),
    tertiary = Color(0xFF3D8F73),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5EDE3),
    onTertiaryContainer = Color(0xFF12382C),
    background = Color(0xFFF5F1E9),
    onBackground = Color(0xFF1C1915),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1C1915),
    surfaceVariant = Color(0xFFECE6DA),
    onSurfaceVariant = Color(0xFF5C564C),
    outline = Color(0xFFD9D1C4),
    outlineVariant = Color(0xFFE8E1D4),
    error = Color(0xFFB54A3C),
    onError = Color.White,
    errorContainer = Color(0xFFF6D6D1),
    onErrorContainer = Color(0xFF4A1C16),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4B483),
    onPrimary = Color(0xFF1A1408),
    primaryContainer = Color(0xFF3A3224),
    onPrimaryContainer = Color(0xFFF3E6CF),
    secondary = Color(0xFFE8D5B0),
    onSecondary = Color(0xFF1A1408),
    secondaryContainer = Color(0xFF3A3224),
    onSecondaryContainer = Color(0xFFF3E6CF),
    tertiary = Color(0xFF7EC9A8),
    onTertiary = Color(0xFF0C241C),
    tertiaryContainer = Color(0xFF1E3D32),
    onTertiaryContainer = Color(0xFFD5EDE3),
    background = Color(0xFF0C0B09),
    onBackground = Color(0xFFEDE6D9),
    surface = Color(0xFF161410),
    onSurface = Color(0xFFEDE6D9),
    surfaceVariant = Color(0xFF221F1A),
    onSurfaceVariant = Color(0xFFB8AFA0),
    outline = Color(0xFF3A342C),
    outlineVariant = Color(0xFF2A2620),
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

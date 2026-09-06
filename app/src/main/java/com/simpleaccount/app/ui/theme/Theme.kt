package com.simpleaccount.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.rememberReduceMotion

/**
 * 默认色（松绿）。Composable 内优先用 LocalAppPalette，保证用户自选配色即时生效。
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
    paletteId: String = ColorPalettes.DEFAULT_ID,
    content: @Composable () -> Unit,
) {
    val pal = ColorPalettes.byId(paletteId)
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(
        LocalAppPalette provides pal,
        LocalReduceMotion provides reduceMotion,
        LocalTokens provides Tokens.Default,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) pal.dark else pal.light,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

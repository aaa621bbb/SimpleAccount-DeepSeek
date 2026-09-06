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
    visualStyleId: String = VisualStyle.DEFAULT.id,
    content: @Composable () -> Unit,
) {
    val pal = ColorPalettes.byId(paletteId)
    val style = VisualStyle.fromId(visualStyleId)
    val reduceMotion = rememberReduceMotion()
    val base = if (darkTheme) pal.dark else pal.light
    val scheme = when (style) {
        VisualStyle.GLASS -> base.copy(
            surface = base.surface.copy(alpha = if (darkTheme) 0.42f else 0.58f),
            surfaceVariant = base.surfaceVariant.copy(alpha = if (darkTheme) 0.38f else 0.5f),
            primaryContainer = base.primaryContainer.copy(alpha = 0.55f),
            secondaryContainer = base.secondaryContainer.copy(alpha = 0.5f),
        )
        VisualStyle.DEPTH -> base
        VisualStyle.DEFAULT -> base
    }
    val shapes = when (style) {
        VisualStyle.GLASS -> Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(18.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        )
        VisualStyle.DEPTH -> Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        )
        VisualStyle.DEFAULT -> AppShapes
    }
    CompositionLocalProvider(
        LocalAppPalette provides pal,
        LocalReduceMotion provides reduceMotion,
        LocalTokens provides Tokens.forStyle(style),
        LocalVisualStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = shapes,
            typography = AppTypography,
        ) {
            AppSkinBackdrop(content)
        }
    }
}

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

private val DefaultShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val DepthShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SimpleAccountTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteId: String = ColorPalettes.DEFAULT_ID,
    uiSkinId: String = UiSkin.DEFAULT.id,
    content: @Composable () -> Unit,
) {
    val pal = ColorPalettes.byId(paletteId)
    val skin = UiSkin.from(uiSkinId)
    val tokens = UiTokens.tokensFor(skin)
    val shapes = when (skin) {
        UiSkin.GLASS -> GlassShapes
        UiSkin.DEPTH -> DepthShapes
        else -> DefaultShapes
    }
    val reduceMotion = rememberReduceMotion()
    // 针对玻璃拟态：surface 适度透一点，深色下更通透；并非仅表皮，按钮、卡片、输入框都会读取 LocalUiSkin
    val scheme = if (darkTheme) pal.dark else pal.light
    val skinnedScheme = when (skin) {
        UiSkin.GLASS -> scheme.copy(
            surface = scheme.surface.copy(alpha = if (darkTheme) 0.82f else 0.92f),
            surfaceVariant = scheme.surfaceVariant.copy(alpha = if (darkTheme) 0.78f else 0.88f),
            background = scheme.background.copy(alpha = 1f)
        )
        UiSkin.DEPTH -> scheme // 保持实色，靠阴影表达层次
        else -> scheme
    }
    CompositionLocalProvider(
        LocalAppPalette provides pal,
        LocalReduceMotion provides reduceMotion,
        LocalTokens provides tokens,
        LocalUiSkin provides skin,
    ) {
        MaterialTheme(
            colorScheme = skinnedScheme,
            shapes = shapes,
            typography = AppTypography,
            content = content,
        )
    }
}

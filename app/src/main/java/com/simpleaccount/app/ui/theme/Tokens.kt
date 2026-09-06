package com.simpleaccount.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全 App 设计令牌。页面、卡片、空态、底栏、弹层都必须引用这里，
 * 禁止在业务页随手写 13.dp / 22.dp / elevation 1.dp。
 *
 * 光影约定：主光源来自左上，投影只表达层级，不换色。
 */
data class DesignTokens(
    val space2: Dp = 2.dp,
    val space4: Dp = 4.dp,
    val space8: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space32: Dp = 32.dp,
    val radiusSm: Dp = 10.dp,
    val radiusMd: Dp = 14.dp,
    val radiusLg: Dp = 18.dp,
    val radiusXl: Dp = 24.dp,
    val radiusPill: Dp = 50.dp,
    val elevRest: Dp = 0.dp,
    val elevRaised: Dp = 2.dp,
    val elevOverlay: Dp = 6.dp,
    val elevNav: Dp = 8.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 20.dp,
    val iconLg: Dp = 24.dp,
    val hairline: Dp = 0.5.dp,
    val pagePad: Dp = 16.dp,
    val cardPad: Dp = 18.dp,
)

val LocalTokens = staticCompositionLocalOf { DesignTokens() }

object Tokens {
    val Default = DesignTokens()
    val Glass = DesignTokens(
        radiusSm = 14.dp,
        radiusMd = 18.dp,
        radiusLg = 22.dp,
        radiusXl = 28.dp,
        elevRest = 0.dp,
        elevRaised = 0.dp,
        elevOverlay = 0.dp,
        elevNav = 0.dp,
        hairline = 0.7.dp,
    )
    val Depth = DesignTokens(
        radiusSm = 8.dp,
        radiusMd = 12.dp,
        radiusLg = 16.dp,
        radiusXl = 20.dp,
        elevRest = 3.dp,
        elevRaised = 10.dp,
        elevOverlay = 16.dp,
        elevNav = 14.dp,
        hairline = 1.2.dp,
    )

    fun forStyle(style: VisualStyle): DesignTokens = when (style) {
        VisualStyle.GLASS -> Glass
        VisualStyle.DEPTH -> Depth
        VisualStyle.DEFAULT -> Default
    }
}

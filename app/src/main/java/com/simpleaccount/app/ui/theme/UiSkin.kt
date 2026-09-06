package com.simpleaccount.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 三套并行的顶级 UI 视觉方案
 * - DEFAULT：现行默认，克制干净
 * - GLASS：液态玻璃 / 玻璃拟态，半透明、羽化、光斑，下沉到每个元件
 * - DEPTH：3D 景深立体，高低错落、明暗差、真实层级压差与光影塑形
 *
 * 三套同为可用全局皮肤，切换无残留、深浅色与全部运行态下各成立。
 */
enum class UiSkin(val id: String, val label: String, val desc: String) {
    DEFAULT("default", "默认", "克制、干净，现有基线"),
    GLASS("glass", "液态玻璃", "通透、羽化、光斑在每个元件"),
    DEPTH("depth", "3D 景深", "高低错落、光影塑形、真实层级"),
    ;

    companion object {
        fun from(id: String?): UiSkin = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

val LocalUiSkin = staticCompositionLocalOf { UiSkin.DEFAULT }

/**
 * 不同皮肤下的设计令牌差异。基于 Tokens.Default 微调，保持同一套语义、不同质感。
 */
object UiTokens {
    fun tokensFor(skin: UiSkin): DesignTokens = when (skin) {
        UiSkin.DEFAULT -> Tokens.Default
        UiSkin.GLASS -> DesignTokens(
            radiusSm = 14.dp,
            radiusMd = 18.dp,
            radiusLg = 22.dp,
            radiusXl = 28.dp,
            radiusPill = 50.dp,
            elevRest = 0.dp,
            elevRaised = 0.dp,
            elevOverlay = 4.dp,
            elevNav = 4.dp,
        )
        UiSkin.DEPTH -> DesignTokens(
            radiusSm = 8.dp,
            radiusMd = 12.dp,
            radiusLg = 16.dp,
            radiusXl = 20.dp,
            radiusPill = 50.dp,
            elevRest = 0.dp,
            elevRaised = 6.dp,
            elevOverlay = 12.dp,
            elevNav = 16.dp,
        )
    }

    /** 皮肤对应的表面透明度与边框 */
    fun surfaceAlpha(skin: UiSkin): Float = when (skin) {
        UiSkin.GLASS -> 0.72f
        else -> 1f
    }

    fun glassBorderColor(light: Boolean): Color =
        if (light) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.18f)

    fun depthShadowAlpha(skin: UiSkin): Float = when (skin) {
        UiSkin.DEPTH -> 0.18f
        else -> 0.08f
    }
}

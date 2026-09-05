@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.simpleaccount.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.simpleaccount.app.R

/**
 * 全局字体：Inter（SIL OFL 1.1 开源，可自由再分发，见 docs/INTER-OFL.txt）。
 * 拉丁/数字用 Inter（表格数字 tnum，金额列对齐），中文自动回落系统字体（小米默认 MiSans）。
 * 材质：变量字体按 400/500/600/700 四档实例化。
 */
val AppFontFamily = FontFamily(
    Font(
        R.font.inter_variable, weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.inter_variable, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.inter_variable, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.inter_variable, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

/** 全套 Material 排版都挂 AppFontFamily；数字类样式启用 tnum 表格数字 */
val AppTypography: Typography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        displayMedium = base.displayMedium.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        displaySmall = base.displaySmall.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        headlineLarge = base.headlineLarge.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        headlineMedium = base.headlineMedium.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        headlineSmall = base.headlineSmall.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = AppFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = AppFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        bodySmall = base.bodySmall.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        labelLarge = base.labelLarge.copy(fontFamily = AppFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
        labelSmall = base.labelSmall.copy(fontFamily = AppFontFamily, fontFeatureSettings = "tnum"),
    )
}

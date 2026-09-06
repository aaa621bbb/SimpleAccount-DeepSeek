package com.simpleaccount.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 三套并行的全局皮肤，不是换配色。
 * default：现行平面令牌。
 * glass：液态玻璃，半透明、发丝高光、无厚投影。
 * depth：3D 景深，高低错落、塑形光影。
 */
enum class VisualStyle(val id: String, val title: String, val tagline: String) {
    DEFAULT("default", "现行默认", "干净平面，令牌圆角与发丝描边"),
    GLASS("glass", "液态玻璃", "半透明面板、内高光、背景透出"),
    DEPTH("depth", "景深立体", "层级压差、塑形光影、控件像被垫起来"),
    ;

    companion object {
        fun fromId(id: String?): VisualStyle =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

val LocalVisualStyle = staticCompositionLocalOf { VisualStyle.DEFAULT }

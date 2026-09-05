package com.simpleaccount.app.ui.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * 动效只走 compositor 的 transform / opacity（[androidx.compose.ui.graphics.graphicsLayer]），
 * 禁止用动画改 layout 尺寸或触发 measure/layout。
 *
 * 系统「动画时长缩放 = 0」或过渡缩放为 0 时视为减弱动态效果，全部瞬时到位。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Motion {
    /** 指针吸附 / 日格弹回：带一点过冲的弹簧 */
    val snapSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.52f,
        stiffness = 520f,
    )

    /** 入场上浮、月份飞入 */
    val softSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 240f,
    )

    /** 高亮游标跟随 */
    val cursorSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.88f,
        stiffness = 380f,
    )

    /** 按下形变 */
    val pressSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 700f,
    )

    /** 勾选舒展 */
    val checkSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 420f,
    )

    val standardEasing: Easing = FastOutSlowInEasing

    const val ENTER_MS = 280
    const val MONTH_MS = 340
    const val FADE_MS = 180
    const val PRESS_MS = 90
    const val SUCCESS_MS = 420

    fun dur(reduce: Boolean, ms: Int): Int = if (reduce) 0 else ms

    fun <T> tweenOrSnap(reduce: Boolean, ms: Int, easing: Easing = standardEasing): TweenSpec<T> {
        return tween(durationMillis = dur(reduce, ms), easing = easing)
    }

    fun springOrSnap(reduce: Boolean, spec: SpringSpec<Float>) =
        if (reduce) snap<Float>() else spec
}

fun readReduceMotion(context: Context): Boolean {
    return try {
        val resolver = context.contentResolver
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        animator == 0f || transition == 0f
    } catch (_: Exception) {
        false
    }
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readReduceMotion(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = readReduceMotion(context)
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return state.value
}

@Composable
fun rememberHapticView(): View = LocalView.current

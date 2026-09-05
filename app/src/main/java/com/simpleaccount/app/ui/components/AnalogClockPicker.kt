package com.simpleaccount.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion
import com.simpleaccount.app.ui.motion.rememberHapticView
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 经典表盘时钟：指针跟手旋转，松手后吸附到整分并带轻微阻尼回弹。
 * 指针用 [graphicsLayer] 旋转，表盘静态 Canvas，不改 layout。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalogClockSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val now = java.time.LocalTime.now()
    val parts = initial.split(":")
    var hour by remember {
        mutableIntStateOf((parts.getOrNull(0)?.toIntOrNull() ?: now.hour).coerceIn(0, 23))
    }
    var minute by remember {
        mutableIntStateOf((parts.getOrNull(1)?.toIntOrNull() ?: now.minute).coerceIn(0, 59))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选时间 · 2.18 圆盘",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    hour = now.hour
                    minute = now.minute
                }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour, minute)) }) { Text("确定") }
            }
            Text(
                "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "当前时间 %02d点%02d分".format(hour, minute)
                },
            )
            Spacer(Modifier.height(4.dp))
            AnalogClockFace(
                hour = hour,
                minute = minute,
                onHourMinute = { h, m ->
                    hour = h
                    minute = m
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isAm = hour < 12
                PeriodChip("上午", selected = isAm) {
                    if (hour >= 12) hour -= 12
                }
                PeriodChip("下午", selected = !isAm) {
                    if (hour < 12) hour += 12
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
    }
}

private enum class ClockHand { Hour, Minute }

@Composable
fun AnalogClockFace(
    hour: Int,
    minute: Int,
    onHourMinute: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val haptic = rememberHapticView()
    val scope = rememberCoroutineScope()
    val hourRef = rememberUpdatedState(hour)
    val minuteRef = rememberUpdatedState(minute)
    val onChangeRef = rememberUpdatedState(onHourMinute)
    val minuteAnim = remember { Animatable(minute * 6f) }
    val hourAnim = remember { Animatable(((hour % 12) + minute / 60f) * 30f) }
    var dragging by remember { mutableStateOf<ClockHand?>(null) }
    var lastMinute by remember { mutableIntStateOf(minute) }

    // 外部（「现在」按钮）改值时，非拖动状态下弹簧跟上
    androidx.compose.runtime.LaunchedEffect(hour, minute, dragging) {
        if (dragging != null) return@LaunchedEffect
        val targetM = minute * 6f
        val targetH = ((hour % 12) + minute / 60f) * 30f
        if (reduce) {
            minuteAnim.snapTo(targetM)
            hourAnim.snapTo(targetH)
        } else {
            launch { minuteAnim.animateTo(shortest(minuteAnim.value, targetM), Motion.snapSpring) }
            launch { hourAnim.animateTo(shortest(hourAnim.value, targetH), Motion.snapSpring) }
        }
    }

    val tick = MaterialTheme.colorScheme.onSurface
    val tickMuted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val face = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val accent = MaterialTheme.colorScheme.primary
    val minuteColor = MaterialTheme.colorScheme.onSurface
    val hourColor = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .size(252.dp)
            .semantics { contentDescription = "表盘时钟，拖动指针设置时间" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(252.dp)) {
            val r = minOf(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = face, radius = r)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90.0)
                val major = i % 5 == 0
                val inner = r * if (major) 0.78f else 0.88f
                val outer = r * 0.97f
                val col = if (major) tick.copy(alpha = 0.85f) else tickMuted
                drawLine(
                    color = col,
                    start = Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                    end = Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
                    strokeWidth = if (major) 4.5f else 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // 时针：graphicsLayer 旋转，合成器线程
        ClockHandView(
            lengthFraction = 0.48f,
            widthDp = 6f,
            color = hourColor,
            rotation = hourAnim.value,
        )
        ClockHandView(
            lengthFraction = 0.70f,
            widthDp = 3.5f,
            color = minuteColor,
            rotation = minuteAnim.value,
        )
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(accent),
        )

        Box(
            Modifier
                .matchParentSize()
                .pointerInput(reduce) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = hypot(dx, dy)
                        val r = minOf(size.width, size.height) / 2f
                        val angle = angleFrom12(dx, dy)
                        if (dist > r * 0.28f) {
                            val m = ((angle / 6f).roundToInt() + 60) % 60
                            onChangeRef.value(hourRef.value, m)
                        } else {
                            val h12 = ((angle / 30f).roundToInt() + 12) % 12
                            val curH = hourRef.value
                            val h = if (curH >= 12) {
                                if (h12 == 0) 12 else h12 + 12
                            } else {
                                if (h12 == 0) 0 else h12
                            }.coerceIn(0, 23)
                            onChangeRef.value(h, minuteRef.value)
                        }
                    }
                }
                .pointerInput(reduce) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val dist = hypot(dx, dy)
                            val r = minOf(size.width, size.height) / 2f
                            // 外圈更大：半径 28% 以外都算分针，点到即命中
                            dragging = if (dist > r * 0.28f) ClockHand.Minute else ClockHand.Hour
                            lastMinute = minuteRef.value
                        },
                        onDragEnd = {
                            val hand = dragging
                            dragging = null
                            val mNow = minuteRef.value
                            val hNow = hourRef.value
                            val snapM = mNow * 6f
                            val snapH = ((hNow % 12) + mNow / 60f) * 30f
                            scope.launch {
                                if (reduce) {
                                    minuteAnim.snapTo(snapM)
                                    hourAnim.snapTo(snapH)
                                } else {
                                    launch { minuteAnim.animateTo(shortest(minuteAnim.value, snapM), Motion.snapSpring) }
                                    launch { hourAnim.animateTo(shortest(hourAnim.value, snapH), Motion.snapSpring) }
                                }
                            }
                            if (hand != null && !reduce) {
                                haptic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                        onDragCancel = { dragging = null },
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val angle = angleFrom12(change.position.x - cx, change.position.y - cy)
                            val hand = dragging ?: return@detectDragGestures
                            scope.launch {
                                when (hand) {
                                    ClockHand.Minute -> {
                                        val m = ((angle / 6f).roundToInt() + 60) % 60
                                        minuteAnim.snapTo(m * 6f)
                                        var h = hourRef.value
                                        if (lastMinute > 50 && m < 10) h = (h + 1) % 24
                                        if (lastMinute < 10 && m > 50) h = (h + 23) % 24
                                        if (m != lastMinute && m % 5 == 0 && !reduce) {
                                            haptic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        lastMinute = m
                                        hourAnim.snapTo(((h % 12) + m / 60f) * 30f)
                                        onChangeRef.value(h, m)
                                    }
                                    ClockHand.Hour -> {
                                        hourAnim.snapTo(angle)
                                        val h12 = ((angle / 30f).roundToInt() + 12) % 12
                                        val curH = hourRef.value
                                        val h = if (curH >= 12) {
                                            if (h12 == 0) 12 else h12 + 12
                                        } else {
                                            if (h12 == 0) 0 else h12
                                        }.let { if (it == 24) 12 else it }.let { v ->
                                            if (curH >= 12 && v < 12) v + 12 else v
                                        }.coerceIn(0, 23)
                                        onChangeRef.value(h, minuteRef.value)
                                    }
                                }
                            }
                        },
                    )
                },
        )
    }
}

@Composable
private fun ClockHandView(
    lengthFraction: Float,
    widthDp: Float,
    color: Color,
    rotation: Float,
) {
    Box(
        Modifier
            .size(252.dp)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.TopCenter,
    ) {
        // 从圆心向上伸出。pivot 默认中心，rotationZ 走 compositor。
        Spacer(Modifier.height((252.dp * (1f - lengthFraction) / 2f)))
        Box(
            Modifier
                .width(widthDp.dp)
                .height(252.dp * lengthFraction / 2f)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** 12 点为 0°，顺时针为正。 */
private fun angleFrom12(dx: Float, dy: Float): Float {
    val deg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    return (deg + 360f) % 360f
}

private fun shortest(current: Float, target: Float): Float {
    var t = target
    while (t - current > 180f) t -= 360f
    while (t - current < -180f) t += 360f
    return t
}

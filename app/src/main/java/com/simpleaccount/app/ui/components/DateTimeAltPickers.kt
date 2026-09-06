package com.simpleaccount.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 胶片条：日子横滑，月份左右飞入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilmSheet(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    val today = LocalDate.now()
    val parts = initialDate.split("-").mapNotNull { it.toIntOrNull() }
    var selected by remember {
        mutableStateOf(
            runCatching {
                LocalDate.of(
                    parts.getOrElse(0) { today.year },
                    parts.getOrElse(1) { today.monthValue },
                    parts.getOrElse(2) { today.dayOfMonth },
                )
            }.getOrDefault(today),
        )
    }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val reduce = LocalReduceMotion.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 胶片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = today; month = YearMonth.from(today) }) { Text("今天") }
                TextButton(onClick = { onConfirm(selected.year, selected.monthValue, selected.dayOfMonth) }) { Text("确定") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    "${month.year}年${month.monthValue}月",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                }
            }
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val fwd = targetState > initialState
                    val enter = slideInHorizontally(Motion.tweenOrSnap(reduce, Motion.MONTH_MS)) { if (fwd) it else -it } +
                        fadeIn(Motion.tweenOrSnap(reduce, Motion.FADE_MS))
                    val exit = slideOutHorizontally(Motion.tweenOrSnap(reduce, Motion.MONTH_MS)) { if (fwd) -it / 3 else it / 3 } +
                        fadeOut(Motion.tweenOrSnap(reduce, Motion.FADE_MS))
                    enter togetherWith exit
                },
                label = "film-month",
            ) { ym ->
                val days = ym.lengthOfMonth()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (d in 1..days) {
                        val date = ym.atDay(d)
                        val sel = date == selected
                        val scale by animateFloatAsState(
                            if (sel) 1.12f else 1f,
                            Motion.springOrSnap(reduce, Motion.snapSpring),
                            label = "film-$d",
                        )
                        Column(
                            Modifier
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .width(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                )
                                .clickable { selected = date }
                                .padding(vertical = 12.dp)
                                .semantics { contentDescription = "${ym.monthValue}月${d}日" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1],
                                fontSize = 10.sp,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$d",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 月份叠卡：点一张展开日子。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateStackSheet(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    val today = LocalDate.now()
    val parts = initialDate.split("-").mapNotNull { it.toIntOrNull() }
    var selected by remember {
        mutableStateOf(
            runCatching {
                LocalDate.of(
                    parts.getOrElse(0) { today.year },
                    parts.getOrElse(1) { today.monthValue },
                    parts.getOrElse(2) { today.dayOfMonth },
                )
            }.getOrDefault(today),
        )
    }
    var openMonth by remember { mutableStateOf(YearMonth.from(selected)) }
    val reduce = LocalReduceMotion.current
    val months = remember {
        (-2..3).map { YearMonth.now().plusMonths(it.toLong()) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 叠卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = today; openMonth = YearMonth.from(today) }) { Text("今天") }
                TextButton(onClick = { onConfirm(selected.year, selected.monthValue, selected.dayOfMonth) }) { Text("确定") }
            }
            months.forEach { ym ->
                val open = ym == openMonth
                val scale by animateFloatAsState(
                    if (open) 1f else 0.97f,
                    Motion.springOrSnap(reduce, Motion.softSpring),
                    label = "stack-$ym",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (open) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        )
                        .clickable { openMonth = ym }
                        .padding(12.dp),
                ) {
                    Text(
                        "${ym.year}年${ym.monthValue}月",
                        fontWeight = FontWeight.SemiBold,
                        color = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        val days = ym.lengthOfMonth()
                        val rows = (1..days).chunked(7)
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { d ->
                                    val date = ym.atDay(d)
                                    val sel = date == selected
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(if (sel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                            .clickable { selected = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$d",
                                            fontSize = 13.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 弧轨时间：大拇指拖到刻度即吸附。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeArcSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val now = java.time.LocalTime.now()
    val parts = initial.split(":")
    var hour by remember { mutableIntStateOf((parts.getOrNull(0)?.toIntOrNull() ?: now.hour).coerceIn(0, 23)) }
    var minute by remember { mutableIntStateOf((parts.getOrNull(1)?.toIntOrNull() ?: now.minute).coerceIn(0, 59)) }
    var mode by remember { mutableStateOf(true) } // true=minute
    val reduce = LocalReduceMotion.current
    val haptic = rememberHapticView()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 弧轨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { hour = now.hour; minute = now.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour, minute)) }) { Text("确定") }
            }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                PeriodChipMini("拨分钟", mode) { mode = true }
                PeriodChipMini("拨小时", !mode) { mode = false }
            }
            ArcTrack(
                value = if (mode) minute / 59f else (hour % 12) / 11f,
                accent = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.surfaceVariant,
                onValue = { t ->
                    if (mode) {
                        val m = (t * 59f).roundToInt().coerceIn(0, 59)
                        if (m != minute && m % 5 == 0 && !reduce) {
                            haptic.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        minute = m
                    } else {
                        val h12 = (t * 11f).roundToInt().coerceIn(0, 11)
                        hour = if (hour >= 12) (if (h12 == 0) 12 else h12 + 12).coerceIn(12, 23) else h12
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PeriodChipMini("上午", hour < 12) { if (hour >= 12) hour -= 12 }
                PeriodChipMini("下午", hour >= 12) { if (hour < 12) hour += 12 }
            }
        }
    }
}

@Composable
private fun PeriodChipMini(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ArcTrack(
    value: Float,
    accent: androidx.compose.ui.graphics.Color,
    track: androidx.compose.ui.graphics.Color,
    onValue: (Float) -> Unit,
) {
    val v = value.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(Unit) {
                fun from(p: Offset) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.95f
                    val deg = Math.toDegrees(atan2((p.x - cx).toDouble(), (cy - p.y).toDouble())).toFloat()
                    val t = ((deg + 90f) / 180f).coerceIn(0f, 1f)
                    onValue(t)
                }
                detectTapGestures { from(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val cx = size.width / 2f
                    val cy = size.height * 0.95f
                    val deg = Math.toDegrees(atan2((change.position.x - cx).toDouble(), (cy - change.position.y).toDouble())).toFloat()
                    onValue(((deg + 90f) / 180f).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val stroke = 18.dp.toPx()
            val r = size.minDimension * 0.48f
            val c = Offset(size.width / 2f, size.height * 0.92f)
            drawArc(
                color = track,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = 180f,
                sweepAngle = 180f * v,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val a = Math.toRadians(180.0 + 180.0 * v)
            val thumb = Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
            drawCircle(accent, 14.dp.toPx(), thumb)
        }
    }
}

/** 翻页数字：复用滚轮，视觉放大成机场钟。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeFlipSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val now = java.time.LocalTime.now()
    val parts = initial.split(":")
    var h = remember { mutableIntStateOf((parts.getOrNull(0)?.toIntOrNull() ?: now.hour).coerceIn(0, 23)) }
    var min = remember { mutableIntStateOf((parts.getOrNull(1)?.toIntOrNull() ?: now.minute).coerceIn(0, 59)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 翻页", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { h.intValue = now.hour; min.intValue = now.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(h.intValue, min.intValue)) }) { Text("确定") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WheelColumn(
                    items = (0..23).map { "%02d".format(it) },
                    selectedIndex = h.intValue,
                    onSelected = { h.intValue = it },
                    modifier = Modifier.weight(1f),
                )
                WheelColumn(
                    items = (0..59).map { "%02d".format(it) },
                    selectedIndex = min.intValue,
                    onSelected = { min.intValue = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

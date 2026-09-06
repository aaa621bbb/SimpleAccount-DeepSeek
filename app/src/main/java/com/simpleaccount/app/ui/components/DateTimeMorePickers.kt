package com.simpleaccount.app.ui.components

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
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
private fun SheetHead(title: String, onNow: () -> Unit, nowLabel: String, onOk: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onNow) { Text(nowLabel) }
        TextButton(onClick = onOk) { Text("确定") }
    }
}

private fun parseYmd(initial: String): LocalDate {
    val today = LocalDate.now()
    val p = initial.split("-").mapNotNull { it.toIntOrNull() }
    return runCatching {
        LocalDate.of(p.getOrElse(0) { today.year }, p.getOrElse(1) { today.monthValue }, p.getOrElse(2) { today.dayOfMonth })
    }.getOrDefault(today)
}

private fun parseHm(initial: String): Pair<Int, Int> {
    val now = java.time.LocalTime.now()
    val p = initial.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: now.hour).coerceIn(0, 23) to
        (p.getOrNull(1)?.toIntOrNull() ?: now.minute).coerceIn(0, 59)
}

/** 纵向时间轴：当前日最大，上下拨。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimelineSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    var selected by remember { mutableStateOf(parseYmd(initialDate)) }
    val reduce = LocalReduceMotion.current
    val window = remember(selected) { (-4..4).map { selected.plusDays(it.toLong()) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHead("选日期 · 时间轴", { selected = LocalDate.now() }, "今天") {
                onConfirm(selected.year, selected.monthValue, selected.dayOfMonth)
            }
            window.forEach { d ->
                val dist = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(selected, d)).toInt()
                val scale by animateFloatAsState(
                    if (d == selected) 1.18f else (1f - dist * 0.08f).coerceAtLeast(0.72f),
                    Motion.springOrSnap(reduce, Motion.snapSpring),
                    label = "tl-$d",
                )
                val alpha = if (d == selected) 1f else (1f - dist * 0.16f).coerceAtLeast(0.35f)
                Text(
                    "${d.monthValue}月${d.dayOfMonth}日 周${listOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]}",
                    fontSize = if (d == selected) 22.sp else 15.sp,
                    fontWeight = if (d == selected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clickable { selected = d }
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = "${d.year}年${d.monthValue}月${d.dayOfMonth}日" },
                )
            }
        }
    }
}

/** 算盘珠：三串圆点。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateBeadsSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val today = LocalDate.now()
    var y by remember { mutableIntStateOf(parseYmd(initialDate).year) }
    var m by remember { mutableIntStateOf(parseYmd(initialDate).monthValue) }
    var d by remember { mutableIntStateOf(parseYmd(initialDate).dayOfMonth) }
    val dim = java.time.YearMonth.of(y, m).lengthOfMonth()
    val day = minOf(d, dim)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            SheetHead("选日期 · 算盘珠", {
                y = today.year; m = today.monthValue; d = today.dayOfMonth
            }, "今天") { onConfirm(y, m, day) }
            Text("%04d-%02d-%02d".format(y, m, day), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            BeadRow("年", (y - 4..y + 2).toList(), y) { y = it }
            BeadRow("月", (1..12).toList(), m) { m = it }
            BeadRow("日", (1..dim).toList(), day) { d = it }
        }
    }
}

@Composable
private fun BeadRow(label: String, items: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(28.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { n ->
                val on = n == selected
                Box(
                    Modifier
                        .size(if (on) 44.dp else 36.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (label == "年") n.toString().takeLast(2) else n.toString(),
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/** 远近地平：选中近、两侧远。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateHorizonSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    var selected by remember { mutableStateOf(parseYmd(initialDate)) }
    val reduce = LocalReduceMotion.current
    val days = remember(selected) { (-3..3).map { selected.plusDays(it.toLong()) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHead("选日期 · 地平", { selected = LocalDate.now() }, "今天") {
                onConfirm(selected.year, selected.monthValue, selected.dayOfMonth)
            }
            Text("${selected.year}年${selected.monthValue}月", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                days.forEach { d ->
                    val dist = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(selected, d)).toInt()
                    val scale by animateFloatAsState(
                        if (d == selected) 1f else (1f - dist * 0.18f).coerceAtLeast(0.46f),
                        Motion.springOrSnap(reduce, Motion.softSpring),
                        label = "hz-$d",
                    )
                    Column(
                        Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = (1f - dist * 0.18f).coerceAtLeast(0.4f) }
                            .padding(horizontal = 4.dp)
                            .clickable { selected = d },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${d.dayOfMonth}", fontSize = if (d == selected) 28.sp else 16.sp, fontWeight = FontWeight.Bold)
                        Text(listOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1], fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** 双柱：高低即时刻。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeBarsSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(parseHm(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm(initial).second) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHead("选时间 · 双柱", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }, "现在") { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                VertBar("时", hour, 23, { hour = it }, Modifier.weight(1f))
                VertBar("分", minute, 59, { minute = it }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VertBar(label: String, value: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(track)
                .pointerInput(max) {
                    fun fromY(y: Float) {
                        val t = (1f - (y / size.height).coerceIn(0f, 1f))
                        onChange((t * max).roundToInt().coerceIn(0, max))
                    }
                    detectTapGestures { fromY(it.y) }
                    detectDragGestures { change, _ ->
                        change.consume()
                        fromY(change.position.y)
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(((value.toFloat() / max.coerceAtLeast(1)) * 180f).dp.coerceAtLeast(8.dp))
                    .align(Alignment.BottomCenter)
                    .background(accent, RoundedCornerShape(18.dp)),
            )
        }
        Text("$value", fontWeight = FontWeight.SemiBold)
    }
}

/** 双环轨道：珠子在环上走，不是指针。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOrbitSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(parseHm(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm(initial).second) }
    val accent = MaterialTheme.colorScheme.primary
    val hourCol = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHead("选时间 · 双环", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }, "现在") { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .size(260.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val dx = p.x - c.x
                            val dy = p.y - c.y
                            val r = kotlin.math.hypot(dx, dy)
                            val ang = ((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360).toFloat()
                            val maxR = minOf(size.width, size.height) / 2f
                            // 修复误触：外环>55%为分，内环<35%为时，中间缓冲不触发
                            when {
                                r > maxR * 0.55f -> minute = ((ang / 6f).roundToInt() + 60) % 60
                                r < maxR * 0.35f -> hour = ((ang / 15f).roundToInt() + 24) % 24
                                else -> Unit
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val dx = change.position.x - c.x
                            val dy = change.position.y - c.y
                            val r = kotlin.math.hypot(dx, dy)
                            val ang = ((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360).toFloat()
                            val maxR = minOf(size.width, size.height) / 2f
                            when {
                                r > maxR * 0.55f -> minute = ((ang / 6f).roundToInt() + 60) % 60
                                r < maxR * 0.35f -> hour = ((ang / 15f).roundToInt() + 24) % 24
                                else -> Unit
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(260.dp)) {
                    val c = center
                    val rM = size.minDimension * 0.42f
                    val rH = size.minDimension * 0.26f
                    drawCircle(track, rM, c, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(track, rH, c, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                    val aM = Math.toRadians(minute * 6.0 - 90.0)
                    val aH = Math.toRadians(hour * 15.0 - 90.0)
                    drawCircle(accent, 11.dp.toPx(), Offset(c.x + (cos(aM) * rM).toFloat(), c.y + (sin(aM) * rM).toFloat()))
                    drawCircle(hourCol, 10.dp.toPx(), Offset(c.x + (cos(aH) * rH).toFloat(), c.y + (sin(aH) * rH).toFloat()))
                }
            }
            Text("外环分钟 · 内环小时", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 时间直尺：24h 横尺。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRulerSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(parseHm(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm(initial).second) }
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            SheetHead("选时间 · 直尺", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }, "现在") { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("小时", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..23).forEach { h ->
                    val on = h == hour
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) accent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { hour = h },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("%02d".format(h), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("分钟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..59).forEach { m ->
                    val on = m == minute
                    val major = m % 5 == 0
                    Box(
                        Modifier
                            .width(if (major) 28.dp else 10.dp)
                            .height(if (on) 40.dp else if (major) 28.dp else 16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (on) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = if (major) 0.45f else 0.2f))
                            .clickable { minute = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (major) Text("%02d".format(m), fontSize = 9.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

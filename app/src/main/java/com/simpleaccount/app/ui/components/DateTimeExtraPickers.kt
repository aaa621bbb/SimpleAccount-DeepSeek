package com.simpleaccount.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
private fun ExtraHead(title: String, now: String, onNow: () -> Unit, onOk: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onNow) { Text(now) }
        TextButton(onClick = onOk) { Text("确定") }
    }
}

private fun ymd(initial: String): LocalDate {
    val today = LocalDate.now()
    val p = initial.split("-").mapNotNull { it.toIntOrNull() }
    return runCatching {
        LocalDate.of(p.getOrElse(0) { today.year }, p.getOrElse(1) { today.monthValue }, p.getOrElse(2) { today.dayOfMonth })
    }.getOrDefault(today)
}

private fun hm(initial: String): Pair<Int, Int> {
    val now = java.time.LocalTime.now()
    val p = initial.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: now.hour).coerceIn(0, 23) to
        (p.getOrNull(1)?.toIntOrNull() ?: now.minute).coerceIn(0, 59)
}

/** 扇骨：月份成扇面，日子排在选中那根骨上。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFanSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val today = LocalDate.now()
    var y by remember { mutableIntStateOf(ymd(initialDate).year) }
    var m by remember { mutableIntStateOf(ymd(initialDate).monthValue) }
    var d by remember { mutableIntStateOf(ymd(initialDate).dayOfMonth) }
    val dim = YearMonth.of(y, m).lengthOfMonth()
    val day = minOf(d, dim)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            ExtraHead("选日期 · 扇骨", "今天", { y = today.year; m = today.monthValue; d = today.dayOfMonth }) { onConfirm(y, m, day) }
            Text("%04d-%02d-%02d".format(y, m, day), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (today.year - 2..today.year + 1).forEach { yy ->
                    val on = yy == y
                    Text(
                        "${yy}年",
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { y = yy }.padding(8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..12).forEach { mm ->
                    val on = mm == m
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(if (on) 80.dp else 36.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { m = mm },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text("$mm", fontSize = 10.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..dim).forEach { dd ->
                    val on = dd == day
                    Box(
                        Modifier.size(if (on) 40.dp else 32.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { d = dd },
                        contentAlignment = Alignment.Center,
                    ) { Text("$dd", fontSize = 12.sp, color = if (on) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

/** 螺线：日子从中心旋出。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSpiralSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    var selected by remember { mutableStateOf(ymd(initialDate)) }
    val days = remember(selected) { (-8..8).map { selected.plusDays(it.toLong()) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选日期 · 螺线", "今天", { selected = LocalDate.now() }) {
                onConfirm(selected.year, selected.monthValue, selected.dayOfMonth)
            }
            Text("${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                days.forEachIndexed { i, d ->
                    val t = i / 16f
                    val ang = t * 5.2f
                    val rad = 18f + t * 96f
                    val x = (kotlin.math.cos(ang.toDouble()) * rad).dp
                    val y = (kotlin.math.sin(ang.toDouble()) * rad).dp
                    val on = d == selected
                    Box(
                        Modifier
                            .padding(start = 130.dp + x, top = 130.dp + y)
                            .size(if (on) 36.dp else 24.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = d },
                        contentAlignment = Alignment.Center,
                    ) { Text("${d.dayOfMonth}", fontSize = 10.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

/** 三柱碑：年/月/日高度即数值。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePillarsSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val today = LocalDate.now()
    var y by remember { mutableIntStateOf(ymd(initialDate).year) }
    var m by remember { mutableIntStateOf(ymd(initialDate).monthValue) }
    var d by remember { mutableIntStateOf(ymd(initialDate).dayOfMonth) }
    val dim = YearMonth.of(y, m).lengthOfMonth()
    val day = minOf(d, dim)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            ExtraHead("选日期 · 三柱", "今天", { y = today.year; m = today.monthValue; d = today.dayOfMonth }) { onConfirm(y, m, day) }
            Text("%04d-%02d-%02d".format(y, m, day), fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pillar("年", y - 2015, 20, { y = 2015 + it })
                Pillar("月", m, 12, { m = it.coerceIn(1, 12) })
                Pillar("日", day, dim, { d = it.coerceIn(1, dim) })
            }
        }
    }
}

@Composable
private fun Pillar(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(max) {
                    fun fromY(yy: Float) {
                        val t = (1f - (yy / size.height).coerceIn(0f, 1f))
                        onChange((t * max).roundToInt().coerceIn(if (label == "月" || label == "日") 1 else 0, max))
                    }
                    detectTapGestures { fromY(it.y) }
                    detectDragGestures { c, _ -> c.consume(); fromY(c.position.y) }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(((value.toFloat() / max.coerceAtLeast(1)) * 160f).dp.coerceAtLeast(8.dp))
                    .align(Alignment.BottomCenter)
                    .background(accent),
            )
        }
        Text("$value", fontWeight = FontWeight.SemiBold)
    }
}

/** 罗盘：外圈月份，内圈日子。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCompassSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val today = LocalDate.now()
    var y by remember { mutableIntStateOf(ymd(initialDate).year) }
    var m by remember { mutableIntStateOf(ymd(initialDate).monthValue) }
    var d by remember { mutableIntStateOf(ymd(initialDate).dayOfMonth) }
    val dim = YearMonth.of(y, m).lengthOfMonth()
    val day = minOf(d, dim)
    val accent = MaterialTheme.colorScheme.primary
    val inner = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选日期 · 罗盘", "今天", { y = today.year; m = today.monthValue; d = today.dayOfMonth }) { onConfirm(y, m, day) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { y -= 1 }) { Text("${y - 1}") }
                Text("${y}年", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                TextButton(onClick = { y += 1 }) { Text("${y + 1}") }
            }
            Box(
                Modifier.size(260.dp).pointerInput(dim) {
                    fun hit(p: Offset) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = p.x - c.x
                        val dy = p.y - c.y
                        val r = kotlin.math.hypot(dx, dy)
                        val ang = ((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360).toFloat()
                        val maxR = size.minDimension / 2f
                        if (r > maxR * 0.62f) m = ((ang / 30f).roundToInt() % 12).let { if (it == 0) 12 else it }
                        else d = (((ang / 360f) * dim).roundToInt().coerceIn(1, dim))
                    }
                    detectTapGestures { hit(it) }
                    detectDragGestures { c, _ -> c.consume(); hit(c.position) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(260.dp)) {
                    val c = center
                    val rM = size.minDimension * 0.42f
                    val rD = size.minDimension * 0.26f
                    drawCircle(track, rM, c, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(track, rD, c, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                    val aM = Math.toRadians(m * 30.0 - 90.0)
                    val aD = Math.toRadians(day * (360.0 / dim) - 90.0)
                    drawCircle(accent, 12.dp.toPx(), Offset(c.x + (cos(aM) * rM).toFloat(), c.y + (sin(aM) * rM).toFloat()))
                    drawCircle(inner, 10.dp.toPx(), Offset(c.x + (cos(aD) * rD).toFloat(), c.y + (sin(aD) * rD).toFloat()))
                }
                Text("%02d月%02d日".format(m, day), fontWeight = FontWeight.Bold)
            }
            Text("外圈月份 · 内圈日子", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 账页：一页一天，左右翻。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateLedgerSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    var selected by remember { mutableStateOf(ymd(initialDate)) }
    val week = listOf("一", "二", "三", "四", "五", "六", "日")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选日期 · 账页", "今天", { selected = LocalDate.now() }) {
                onConfirm(selected.year, selected.monthValue, selected.dayOfMonth)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = selected.minusDays(1) }) { Text("上一页") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selected = selected.plusDays(1) }) { Text("下一页") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(20.dp)
                    .pointerInput(selected) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            if (amount.x < -40) selected = selected.plusDays(1)
                            if (amount.x > 40) selected = selected.minusDays(1)
                        }
                    },
            ) {
                Column {
                    Text("${selected.year} 年 ${selected.monthValue} 月", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${selected.dayOfMonth}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text("星期${week[selected.dayOfWeek.value - 1]}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text("左右滑翻页，像翻账本。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 沙漏：沙子高度是分钟，点翻转跨小时。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSandSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(hm(initial).first) }
    var minute by remember { mutableIntStateOf(hm(initial).second) }
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选时间 · 沙漏", "现在", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }) { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .width(120.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        fun fromY(y: Float) {
                            val t = (y / size.height).coerceIn(0f, 1f)
                            minute = (t * 59).roundToInt()
                        }
                        detectTapGestures { fromY(it.y) }
                        detectDragGestures { c, _ -> c.consume(); fromY(c.position.y) }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(((minute / 59f) * 220f).dp.coerceAtLeast(6.dp))
                        .align(Alignment.BottomCenter)
                        .background(accent.copy(alpha = 0.85f)),
                )
            }
            Row {
                TextButton(onClick = { hour = (hour + 23) % 24 }) { Text("−1 时") }
                TextButton(onClick = { hour = (hour + 1) % 24 }) { Text("+1 时") }
            }
            Text("上下拖沙子改分钟，点两侧换小时。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 双鼓：两个圆柱滚小时/分。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDrumSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(hm(initial).first) }
    var minute by remember { mutableIntStateOf(hm(initial).second) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选时间 · 双鼓", "现在", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }) { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Drum("时", hour, 23, { hour = it }, Modifier.weight(1f))
                Drum("分", minute, 59, { minute = it }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Drum(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(80.dp))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ((value - 2)..(value + 2)).forEach { raw ->
                val n = ((raw % (max + 1)) + (max + 1)) % (max + 1)
                val on = n == value
                Text(
                    "%02d".format(n),
                    fontSize = if (on) 28.sp else 16.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onChange(n) }.padding(6.dp),
                )
            }
        }
    }
}

/** 日晷：影子角度即分钟，点晷面换小时。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSundialSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(hm(initial).first) }
    var minute by remember { mutableIntStateOf(hm(initial).second) }
    val accent = MaterialTheme.colorScheme.primary
    val face = MaterialTheme.colorScheme.surfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选时间 · 日晷", "现在", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }) { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Box(
                Modifier.size(240.dp).clip(CircleShape).background(face).pointerInput(Unit) {
                    var locked = false
                    fun hit(p: Offset, start: Boolean) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = p.x - c.x
                        val dy = p.y - c.y
                        val r = kotlin.math.hypot(dx, dy)
                        val ang = ((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360).toFloat()
                        val maxR = size.minDimension / 2f
                        if (start) locked = r < maxR * 0.35f
                        if (locked) hour = ((ang / 15f).roundToInt() + 24) % 24
                        else minute = ((ang / 6f).roundToInt() + 60) % 60
                    }
                    detectTapGestures { hit(it, true) }
                    detectDragGestures(
                        onDragStart = { hit(it, true) },
                        onDrag = { c, _ -> c.consume(); hit(c.position, false) },
                    )
                },
            ) {
                Canvas(Modifier.size(240.dp)) {
                    val c = center
                    val a = Math.toRadians(minute * 6.0 - 90.0)
                    drawLine(
                        accent,
                        c,
                        Offset(c.x + (cos(a) * size.minDimension * 0.42).toFloat(), c.y + (sin(a) * size.minDimension * 0.42).toFloat()),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(accent, 8.dp.toPx(), c)
                }
            }
            Text("外圈拖影子改分，圆心附近改小时。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 节拍器：左右摆分钟，点底座换小时。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeMetronomeSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(hm(initial).first) }
    var minute by remember { mutableIntStateOf(hm(initial).second) }
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选时间 · 节拍器", "现在", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }) { onConfirm("%02d:%02d".format(hour, minute)) }
            Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { c, _ ->
                            c.consume()
                            val t = (c.position.x / size.width).coerceIn(0f, 1f)
                            minute = (t * 59).roundToInt()
                        }
                        detectTapGestures { p ->
                            if (p.y > size.height * 0.7f) {
                                val t = (p.x / size.width).coerceIn(0f, 1f)
                                hour = (t * 23).roundToInt()
                            } else {
                                val t = (p.x / size.width).coerceIn(0f, 1f)
                                minute = (t * 59).roundToInt()
                            }
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    val baseY = size.height * 0.92f
                    val pivot = Offset(size.width / 2f, baseY)
                    val swing = (minute / 59f - 0.5f) * 70.0
                    val rad = Math.toRadians(swing - 90.0)
                    val len = size.height * 0.78f
                    drawLine(
                        accent,
                        pivot,
                        Offset(pivot.x + (cos(rad) * len).toFloat(), pivot.y + (sin(rad) * len).toFloat()),
                        8.dp.toPx(),
                        StrokeCap.Round,
                    )
                    drawCircle(accent, 10.dp.toPx(), pivot)
                }
            }
            Text("左右拖摆锤改分钟，点底座改小时。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 积木：四块数字，点加减。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeBlocksSheet(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(hm(initial).first) }
    var minute by remember { mutableIntStateOf(hm(initial).second) }
    val digits = listOf(hour / 10, hour % 10, minute / 10, minute % 10)
    fun setDigits(i: Int, delta: Int) {
        val d = digits.toMutableList()
        d[i] = when (i) {
            0 -> (d[i] + delta + 3) % 3
            1 -> if (d[0] == 2) (d[i] + delta + 4) % 4 else (d[i] + delta + 10) % 10
            2 -> (d[i] + delta + 6) % 6
            else -> (d[i] + delta + 10) % 10
        }
        hour = (d[0] * 10 + d[1]).coerceIn(0, 23)
        minute = (d[2] * 10 + d[3]).coerceIn(0, 59)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExtraHead("选时间 · 积木", "现在", {
                val n = java.time.LocalTime.now(); hour = n.hour; minute = n.minute
            }) { onConfirm("%02d:%02d".format(hour, minute)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                digits.forEachIndexed { i, n ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { setDigits(i, 1) }) { Text("+") }
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$n", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { setDigits(i, -1) }) { Text("−") }
                    }
                    if (i == 1) Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("四块积木各自加减，像车站翻牌但按块点。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

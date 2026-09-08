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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun parseYmd2(initial: String): LocalDate {
    val today = LocalDate.now()
    val p = initial.split("-").mapNotNull { it.toIntOrNull() }
    return runCatching { LocalDate.of(p.getOrElse(0) { today.year }, p.getOrElse(1) { today.monthValue }, p.getOrElse(2) { today.dayOfMonth }) }.getOrDefault(today)
}
private fun parseHm2(initial: String): Pair<Int,Int> {
    val now = java.time.LocalTime.now()
    val p = initial.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: now.hour).coerceIn(0,23) to (p.getOrNull(1)?.toIntOrNull() ?: now.minute).coerceIn(0,59)
}

// ============ 新增日期 5 ============

/** 层叠瀑布：年份像瀑布层叠倾泻 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCascadeSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int,Int,Int)->Unit) {
    var selected by remember { mutableStateOf(parseYmd2(initialDate)) }
    val years = (LocalDate.now().year - 5 .. LocalDate.now().year + 2).toList()
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 层叠瀑布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = LocalDate.now() }) { Text("今天") }
                TextButton(onClick = { onConfirm(selected.year, selected.monthValue, selected.dayOfMonth) }) { Text("确定") }
            }
            Text("点年份瀑布块，月份在水池中展开", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy((-12).dp)) {
                years.forEach { y ->
                    val isSelYear = y == selected.year
                    val months = if (isSelYear) (1..12).toList() else emptyList()
                    Box(
                        Modifier.fillMaxWidth()
                            .height(if (isSelYear) 110.dp else 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelYear) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { selected = LocalDate.of(y, selected.monthValue.coerceIn(1,12), 1).let { d -> 
                                val dim = YearMonth.of(y, d.monthValue).lengthOfMonth()
                                LocalDate.of(y, d.monthValue, selected.dayOfMonth.coerceIn(1, dim))
                            }}
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("$y 年", fontWeight = FontWeight.Bold, color = if (isSelYear) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            if (isSelYear) {
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    months.forEach { m ->
                                        val sel = m == selected.monthValue
                                        Box(
                                            Modifier.size(36.dp).clip(CircleShape)
                                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .clickable {
                                                    val dim = YearMonth.of(y, m).lengthOfMonth()
                                                    selected = LocalDate.of(y, m, selected.dayOfMonth.coerceIn(1, dim))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) { Text("$m", fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 日选择
            val dim = YearMonth.of(selected.year, selected.monthValue).lengthOfMonth()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..dim).forEach { d ->
                    val sel = d == selected.dayOfMonth
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = LocalDate.of(selected.year, selected.monthValue, d) },
                        contentAlignment = Alignment.Center
                    ) { Text("$d", fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

/** 螺旋年轮：螺旋展开 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSpiralSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int,Int,Int)->Unit) {
    var selected by remember { mutableStateOf(parseYmd2(initialDate)) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 螺旋年轮", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = LocalDate.now() }) { Text("今天") }
                TextButton(onClick = { onConfirm(selected.year, selected.monthValue, selected.dayOfMonth) }) { Text("确定") }
            }
            Text("中心是今天，螺旋向外为未来", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(260.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                // 螺旋点：未来15天螺旋排布
                val days = (-7..14).map { selected.plusDays(it.toLong()) }
                Canvas(Modifier.size(260.dp)) {
                    val c = center
                    days.forEachIndexed { idx, d ->
                        val angle = idx * 28f - 90f
                        val radius = 18f + idx * 7f
                        val rad = Math.toRadians(angle.toDouble())
                        val x = c.x + (cos(rad) * radius).toFloat()
                        val y = c.y + (sin(rad) * radius).toFloat()
                        val isSel = d == selected
                        drawCircle(
                            color = if (isSel) Color(0xFF1F6F5B) else Color(0xFF90A4AE).copy(alpha = 0.5f),
                            radius = if (isSel) 14.dp.toPx() else 9.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
                // 覆盖可点击点
                Box(Modifier.size(260.dp)) {
                    days.forEachIndexed { idx, d ->
                        val angle = idx * 28f - 90f
                        val radius = 18f + idx * 7f
                        val rad = Math.toRadians(angle.toDouble())
                        // 用 Box 定位
                        val cx = 130f + (cos(rad) * radius).toFloat()
                        val cy = 130f + (sin(rad) * radius).toFloat()
                        Box(
                            Modifier
                                .size(28.dp)
                                .graphicsLayer { translationX = cx - 14.dp.toPx(); translationY = cy - 14.dp.toPx() }
                                .clip(CircleShape)
                                .clickable { selected = d }
                        ) {}
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("周${listOf("一","二","三","四","五","六","日")[selected.dayOfWeek.value-1]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 九宫格：3×4 月份墙 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateGridSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int,Int,Int)->Unit) {
    var y by remember { mutableIntStateOf(parseYmd2(initialDate).year) }
    var m by remember { mutableIntStateOf(parseYmd2(initialDate).monthValue) }
    var d by remember { mutableIntStateOf(parseYmd2(initialDate).dayOfMonth) }
    var showDays by remember { mutableStateOf(false) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 九宫格", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val t = LocalDate.now(); y=t.year; m=t.monthValue; d=t.dayOfMonth }) { Text("今天") }
                TextButton(onClick = { onConfirm(y,m,d.coerceIn(1, YearMonth.of(y,m).lengthOfMonth())) }) { Text("确定") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$y 年", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { y-- }) { Text("去年") }
                TextButton(onClick = { y++ }) { Text("明年") }
            }
            Spacer(Modifier.height(8.dp))
            if (!showDays) {
                // 3x4 月份宫格
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..12).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { mm ->
                                val sel = mm == m
                                Box(
                                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable { m = mm; showDays = true },
                                    contentAlignment = Alignment.Center
                                ) { Text("${mm}月", fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${m}月", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showDays = false }) { Text("选月份") }
                }
                val dim = YearMonth.of(y,m).lengthOfMonth()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..dim).chunked(7).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { dd ->
                                val sel = dd == d
                                Box(
                                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { d = dd },
                                    contentAlignment = Alignment.Center
                                ) { Text("$dd", color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** 年轮转盘：外圈年份 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialYearSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int,Int,Int)->Unit) {
    var selected by remember { mutableStateOf(parseYmd2(initialDate)) }
    var yearOffset by remember { mutableIntStateOf(0) } // 相对当前年的偏移
    val baseYear = LocalDate.now().year
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 年轮转盘", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = LocalDate.now(); yearOffset = 0 }) { Text("今天") }
                TextButton(onClick = { onConfirm(selected.year, selected.monthValue, selected.dayOfMonth) }) { Text("确定") }
            }
            Text("转外圈拨年，点内环选月日", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val displayYear = baseYear + yearOffset
            Box(
                Modifier.size(220.dp).pointerInput(yearOffset) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = dragAmount.x
                        if (dx > 30) yearOffset++
                        if (dx < -30) yearOffset--
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(220.dp)) {
                    val c = center
                    val r = size.minDimension/2f * 0.88f
                    drawCircle(color = Color(0xFFE0E0E0), radius = r, style = Stroke(width = 28.dp.toPx()))
                    // 年份刻度
                    for (i in -2..2) {
                        val a = Math.toRadians((i*30).toDouble() - 90)
                        val x = c.x + (cos(a)*r).toFloat()
                        val y = c.y + (sin(a)*r).toFloat()
                        drawCircle(color = if (i==0) Color(0xFF1F6F5B) else Color(0xFF90A4AE), radius = if (i==0) 14.dp.toPx() else 8.dp.toPx(), center = Offset(x,y))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$displayYear", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("滑动拨年", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..6).forEach { mm ->
                            val sel = mm == selected.monthValue && displayYear == selected.year
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val dim = YearMonth.of(displayYear, mm).lengthOfMonth()
                                        selected = LocalDate.of(displayYear, mm, selected.dayOfMonth.coerceIn(1, dim))
                                        yearOffset = displayYear - baseYear
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text("$mm", fontSize = 11.sp, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (7..12).forEach { mm ->
                            val sel = mm == selected.monthValue && displayYear == selected.year
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val dim = YearMonth.of(displayYear, mm).lengthOfMonth()
                                        selected = LocalDate.of(displayYear, mm, selected.dayOfMonth.coerceIn(1, dim))
                                        yearOffset = displayYear - baseYear
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text("$mm", fontSize = 11.sp, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val dim = YearMonth.of(selected.year, selected.monthValue).lengthOfMonth()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..dim).forEach { dd ->
                    val sel = dd == selected.dayOfMonth && selected.year == displayYear
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = LocalDate.of(displayYear, selected.monthValue, dd) },
                        contentAlignment = Alignment.Center
                    ) { Text("$dd", fontSize = 12.sp, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("已选 ${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** 波浪起伏：正弦波 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateWaveSheet(initialDate: String, onDismiss: () -> Unit, onConfirm: (Int,Int,Int)->Unit) {
    var selected by remember { mutableStateOf(parseYmd2(initialDate)) }
    val days = remember(selected) { (-6..6).map { selected.plusDays(it.toLong()) } }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期 · 波浪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = LocalDate.now() }) { Text("今天") }
                TextButton(onClick = { onConfirm(selected.year, selected.monthValue, selected.dayOfMonth) }) { Text("确定") }
            }
            Text("波峰是选中日，左右抚动如琴弦", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .pointerInput(days) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount.x < -20) selected = selected.plusDays(1)
                            if (dragAmount.x > 20) selected = selected.minusDays(1)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, h/2f)
                    for (x in 0..w.toInt() step 4) {
                        val t = x / w * 2 * Math.PI
                        val y = h/2f + (sin(t*1.5 + Math.PI/2) * 28f).toFloat()
                        path.lineTo(x.toFloat(), y)
                    }
                    drawPath(path, color = Color(0xFF1F6F5B).copy(alpha = 0.85f), style = Stroke(width = 3.dp.toPx()))
                    // 波峰点
                    days.forEachIndexed { idx, d ->
                        val isSel = d == selected
                        val x = w * idx / (days.size -1).toFloat()
                        val y = h/2f + (sin(idx/(days.size-1f)*2*Math.PI*1.5 + Math.PI/2)*28f).toFloat()
                        drawCircle(color = if (isSel) Color(0xFF1F6F5B) else Color(0xFFB0BEC5), radius = if (isSel) 10.dp.toPx() else 6.dp.toPx(), center = Offset(x,y))
                    }
                }
                Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    days.forEach { d ->
                        Box(
                            Modifier.size(40.dp).clickable { selected = d },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${d.dayOfMonth}", fontWeight = if (d==selected) FontWeight.Bold else FontWeight.Normal, fontSize = if (d==selected) 16.sp else 12.sp, color = if (d==selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Text("${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日 周${listOf("一","二","三","四","五","六","日")[selected.dayOfWeek.value-1]}", fontWeight = FontWeight.Bold)
        }
    }
}

// ============ 新增时间 5 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeWaveSheet(initial: String, onDismiss: ()->Unit, onConfirm:(String)->Unit) {
    var hour by remember { mutableIntStateOf(parseHm2(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm2(initial).second) }
    var focusingHour by remember { mutableStateOf(true) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 波浪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val n=java.time.LocalTime.now(); hour=n.hour; minute=n.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour,minute)) }) { Text("确定") }
            }
            Text("%02d:%02d".format(hour,minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("波峰拨分", fontSize = 12.sp, color = if (!focusingHour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (!focusingHour) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable{ focusingHour=false }.padding(horizontal = 12.dp, vertical = 6.dp))
                Text("波谷切时", fontSize = 12.sp, color = if (focusingHour) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (focusingHour) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable{ focusingHour=true }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(120.dp)
                    .pointerInput(focusingHour) {
                        detectDragGestures { change,_ ->
                            change.consume()
                            val t = (change.position.x / size.width).coerceIn(0f,1f)
                            if (focusingHour) hour = (t*23f).roundToInt().coerceIn(0,23)
                            else minute = (t*59f).roundToInt().coerceIn(0,59)
                        }
                    }
                    .pointerInput(focusingHour) {
                        detectTapGestures { offset ->
                            val t = (offset.x / size.width).coerceIn(0f,1f)
                            if (focusingHour) hour = (t*23f).roundToInt().coerceIn(0,23)
                            else minute = (t*59f).roundToInt().coerceIn(0,59)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, h/2f)
                    for (x in 0..w.toInt() step 4) {
                        val y = h/2f + (sin(x/w * 2*Math.PI*2) * 22f).toFloat()
                        if (x==0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
                    }
                    drawPath(path, color = Color(0xFF1F6F5B), style = Stroke(3.dp.toPx()))
                    val prog = if (focusingHour) hour/23f else minute/59f
                    val px = w*prog
                    val py = h/2f + (sin(prog*2*Math.PI*2)*22f).toFloat()
                    drawCircle(color = Color(0xFF1F6F5B), radius = 10.dp.toPx(), center = Offset(px, py))
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(px, py))
                }
            }
            Text(if (focusingHour) "拖动波形调小时" else "拖动波形调分钟", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDrumSheet(initial: String, onDismiss: ()->Unit, onConfirm:(String)->Unit) {
    var hour by remember { mutableIntStateOf(parseHm2(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm2(initial).second) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 鼓面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val n=java.time.LocalTime.now(); hour=n.hour; minute=n.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour,minute)) }) { Text("确定") }
            }
            Text("%02d:%02d".format(hour,minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("左右滚时 · 上下滚分 — 像老式收音机", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // 鼓-时：横向条带 + 透视
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("时", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.x < -18) hour = (hour+1)%24
                                    if (dragAmount.x > 18) hour = (hour+23)%24
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (delta in -1..1) {
                                val h = (hour+delta+24)%24
                                val isCenter = delta==0
                                Box(
                                    Modifier.size(if (isCenter) 56.dp else 44.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (isCenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .clickable { hour = h },
                                    contentAlignment = Alignment.Center
                                ) { Text("%02d".format(h), fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal, color = if (isCenter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("分", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y < -18) minute = (minute+1)%60
                                    if (dragAmount.y > 18) minute = (minute+59)%60
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            for (delta in -1..1) {
                                val m = (minute+delta+60)%60
                                val isCenter = delta==0
                                Box(
                                    Modifier.size(if (isCenter) 56.dp else 44.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (isCenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .clickable { minute = m },
                                    contentAlignment = Alignment.Center
                                ) { Text("%02d".format(m), fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal, color = if (isCenter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePieSheet(initial: String, onDismiss: ()->Unit, onConfirm:(String)->Unit) {
    var hour by remember { mutableIntStateOf(parseHm2(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm2(initial).second) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 饼切", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val n=java.time.LocalTime.now(); hour=n.hour; minute=n.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour,minute)) }) { Text("确定") }
            }
            Text("%02d:%02d".format(hour,minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("点外圈饼块选时，点内环切分选 0/15/30/45 分", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(240.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val cx = size.width/2f; val cy=size.height/2f
                            val dx=offset.x-cx; val dy=offset.y-cy
                            val dist = kotlin.math.hypot(dx,dy)
                            val r = minOf(size.width,size.height)/2f
                            val ang = ((Math.toDegrees(kotlin.math.atan2(dx.toDouble(), -dy.toDouble()))+360)%360).toFloat()
                            if (dist > r*0.55f) {
                                hour = ((ang/15f).roundToInt()+24)%24
                            } else if (dist > r*0.25f) {
                                val q = ((ang/90f).roundToInt()+4)%4
                                minute = q*15
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(240.dp)) {
                    val c=center; val r=size.minDimension/2f
                    for (i in 0 until 24) {
                        val sweep=15f
                        val start = i*15f -90f
                        val isSel = i==hour
                        drawArc(color = if (isSel) Color(0xFF1F6F5B) else Color(0xFFE0E0E0), startAngle = start, sweepAngle = sweep-1f, useCenter = true, topLeft = Offset(c.x-r, c.y-r), size = androidx.compose.ui.geometry.Size(r*2, r*2))
                    }
                    // 内环
                    drawCircle(color = Color.White, radius = r*0.48f, center = c)
                    for (q in 0 until 4) {
                        val ang = q*90f -90f
                        val rad = Math.toRadians(ang.toDouble())
                        val ir = r*0.36f
                        val x = c.x + (cos(rad)*ir).toFloat()
                        val y = c.y + (sin(rad)*ir).toFloat()
                        val sel = minute/15==q
                        drawCircle(color = if (sel) Color(0xFF1F6F5B) else Color(0xFFB0BEC5), radius = if (sel) 12.dp.toPx() else 8.dp.toPx(), center = Offset(x,y))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0,15,30,45).forEach { qm ->
                    val sel = minute==qm
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { minute = qm }.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("%02d".format(qm), color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeBlocksSheet(initial: String, onDismiss: ()->Unit, onConfirm:(String)->Unit) {
    var hour by remember { mutableIntStateOf(parseHm2(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm2(initial).second) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 方块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val n=java.time.LocalTime.now(); hour=n.hour; minute=n.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour,minute)) }) { Text("确定") }
            }
            Text("%02d:%02d".format(hour,minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("6 行(每行4小时) × 12 列(每列5分钟)，点亮即时刻", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 6) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until 12) {
                            val blockHour = row*4 + col/3
                            if (blockHour>23) {
                                Spacer(Modifier.weight(1f))
                                continue
                            }
                            val blockMin = (col%3)*20 + (col%3)*0 // 0,20,40 but we map to 5* ?
                            // 简化：每块代表 5 分钟台阶，小时由行列共同决定
                            val isSel = hour==blockHour && minute/5==col%12
                            Box(
                                Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .clickable {
                                        hour = blockHour
                                        minute = (col*5)%60
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) Text("●", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("提示：横为 5 分钟，纵跨天", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSpectrumSheet(initial: String, onDismiss: ()->Unit, onConfirm:(String)->Unit) {
    var hour by remember { mutableIntStateOf(parseHm2(initial).first) }
    var minute by remember { mutableIntStateOf(parseHm2(initial).second) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间 · 色谱", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { val n=java.time.LocalTime.now(); hour=n.hour; minute=n.minute }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(hour,minute)) }) { Text("确定") }
            }
            Text("%02d:%02d".format(hour,minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("冷色早 暖色晚，磁吸整 15 分钟", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            // 色谱条
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color(0xFF5DADE2), Color(0xFFF7DC6F), Color(0xFFE74C3C), Color(0xFF6C3483))
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val t = (offset.x/size.width).coerceIn(0f,1f)
                            hour = (t*23f).roundToInt().coerceIn(0,23)
                        }
                        detectDragGestures { change, _ ->
                            change.consume()
                            val t = (change.position.x/size.width).coerceIn(0f,1f)
                            hour = (t*23f).roundToInt().coerceIn(0,23)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // 滑块
                val prog = hour/23f
                Box(
                    Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val x = size.width*prog
                        drawCircle(color = Color.White, radius = 14.dp.toPx(), center = Offset(x, size.height/2f))
                        drawCircle(color = Color(0xFF1F6F5B), radius = 9.dp.toPx(), center = Offset(x, size.height/2f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..59 step 5).forEach { m ->
                    val sel = minute==m
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { minute = m }.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("%02d".format(m), color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("0 时", fontSize = 11.sp, color = Color(0xFF5DADE2))
                Spacer(Modifier.weight(1f))
                Text("12 时", fontSize = 11.sp, color = Color(0xFFF7DC6F))
                Spacer(Modifier.weight(1f))
                Text("23 时", fontSize = 11.sp, color = Color(0xFF6C3483))
            }
        }
    }
}

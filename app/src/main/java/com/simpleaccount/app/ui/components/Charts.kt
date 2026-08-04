package com.simpleaccount.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.ui.stats.PieSlice
import com.simpleaccount.app.ui.stats.TrendPoint

/**
 * 环形饼图（Canvas 自绘）。中心显示总计（保留两位小数）。
 */
@Composable
fun PieChartView(
    slices: List<PieSlice>,
    centerLabel: String,
    centerValue: String,
) {
    val total = slices.sumOf { it.value }
    // 在 @Composable 上下文先取色（不能在 DrawScope 内调用 MaterialTheme）
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(180.dp)) {
            val strokeWidth = 36.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            if (total <= 0) {
                drawArc(
                    color = surfaceVariant,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            } else {
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = slice.value.toFloat() / total * 360f
                    drawArc(
                        color = parseColor(slice.colorHex),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerValue,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(centerLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 折线趋势图（Canvas 自绘）。X 轴按"月"，绘制 支出(红)/收入(绿) 两条线。
 * 带 Y 轴刻度金额 + X 轴月份标注，网格、数据点，美观易读。
 */
@Composable
fun LineTrendView(
    trend: List<TrendPoint>,
    expenseColor: Color = Color(0xFFFF6B6B),
    incomeColor: Color = Color(0xFF2ECC71),
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val padLeft = 38.dp
    val padRight = 12.dp
    val padTop = 10.dp
    val padBottom = 22.dp
    val gridRows = 4

    BoxWithConstraints(Modifier.fillMaxWidth().height(245.dp)) {
        val density = LocalDensity.current
        val pl = with(density) { padLeft.toPx() }
        val pr = with(density) { padRight.toPx() }
        val pt = with(density) { padTop.toPx() }
        val pb = with(density) { padBottom.toPx() }
        val plotW = constraints.maxWidth - pl - pr
        val plotH = constraints.maxHeight - pt - pb
        val maxValue = trend.maxOfOrNull { maxOf(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L

        // 画布：网格 + 折线 + 数据点
        Canvas(Modifier.fillMaxSize()) {
            fun xFor(i: Int): Float = pl + plotW * i / (trend.size - 1).coerceAtLeast(1)
            fun yFor(v: Long): Float = pt + plotH * (1f - v.toFloat() / maxValue)
            for (gi in 0..gridRows) {
                val gy = pt + plotH * gi / gridRows
                drawLine(if (gi == 0) gridColor.copy(alpha = 0.4f) else gridColor,
                    Offset(pl, gy), Offset(pl + plotW, gy), strokeWidth = if (gi == 0) 1.2f else 1f)
            }
            if (trend.size >= 2) {
                fun drawSeries(sel: (TrendPoint) -> Long, color: Color) {
                    val pts = trend.mapIndexed { i, p -> Offset(xFor(i), yFor(sel(p))) }
                    for (i in 0 until pts.size - 1)
                        drawLine(color, pts[i], pts[i + 1], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    pts.forEach { drawCircle(color, 3.dp.toPx(), it) }
                }
                drawSeries({ it.expense }, expenseColor)
                drawSeries({ it.income }, incomeColor)
            }
        }

        // Y 轴刻度标签（左侧）
        Column(Modifier.align(Alignment.TopStart)) {
            for (gi in gridRows downTo 0) {
                val v = maxValue * gi / gridRows
                Box(
                    Modifier
                        .weight(1f)
                        .width(36.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(formatAxisValue(v), fontSize = 9.sp, color = labelColor)
                }
            }
        }

        // X 轴月份标注（底部，从 padLeft 起，等宽分布）
        if (trend.isNotEmpty()) {
            val cellW = plotW / trend.size
            Row(Modifier.align(Alignment.BottomStart).offset(x = padLeft).width(plotW.dp)) {
                trend.forEach { p ->
                    Text(
                        p.month.substring(5),  // "MM"
                        fontSize = 9.sp,
                        color = labelColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .width(cellW.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

/** 金额(分) → 轴标签（元，量大时缩略） */
private fun formatAxisValue(fen: Long): String {
    val yuan = fen / 100.0
    return when {
        yuan >= 10000 -> "${(yuan / 10000).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(it) }}万"
        yuan >= 1000 -> "%.0f".format(yuan)
        else -> if (yuan == 0.0) "0" else "%.0f".format(yuan)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackgroundGrid(size: Size, gridColor: Color) {
    val padLeft = 34.dp.toPx()
    val padRight = 10.dp.toPx()
    val padTop = 16.dp.toPx()
    val padBottom = 28.dp.toPx()
    val plotW = size.width - padLeft - padRight
    val plotH = size.height - padTop - padBottom
    for (gi in 0..4) {
        val gy = padTop + plotH * gi / 4f
        drawLine(gridColor, Offset(padLeft, gy), Offset(padLeft + plotW, gy), strokeWidth = 1f)
    }
}

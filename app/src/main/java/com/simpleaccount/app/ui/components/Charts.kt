package com.simpleaccount.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
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
 */
@Composable
fun LineTrendView(
    trend: List<TrendPoint>,
    expenseColor: Color = Color(0xFFFF6B6B),
    incomeColor: Color = Color(0xFF2ECC71),
) {
    // 在 @Composable 上下文先取色
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        if (trend.size < 2) {
            // 数据不足，只画背景网格
            drawBackgroundGrid(this.size, gridColor)
            return@Canvas
        }
        val maxValue = trend.maxOf { maxOf(it.expense, it.income) }.coerceAtLeast(1L)
        val padLeft = 34.dp.toPx()
        val padRight = 10.dp.toPx()
        val padTop = 16.dp.toPx()
        val padBottom = 28.dp.toPx()
        val plotW = size.width - padLeft - padRight
        val plotH = size.height - padTop - padBottom

        fun xFor(i: Int) = padLeft + plotW * i / (trend.size - 1).coerceAtLeast(1)
        fun yFor(v: Long) = padTop + plotH * (1f - v.toFloat() / maxValue)

        drawBackgroundGrid(size, gridColor)

        fun drawSeries(points: List<Offset>, color: Color) {
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        val expensePts = trend.mapIndexed { i, p -> Offset(xFor(i), yFor(p.expense)) }
        val incomePts = trend.mapIndexed { i, p -> Offset(xFor(i), yFor(p.income)) }
        drawSeries(expensePts, expenseColor)
        drawSeries(incomePts, incomeColor)
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

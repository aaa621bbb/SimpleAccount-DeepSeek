package com.simpleaccount.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.ui.stats.PieSlice
import com.simpleaccount.app.ui.stats.TrendPoint
import com.simpleaccount.app.ui.theme.AppColors
import kotlin.math.abs

/**
 * 环形饼图：宽环 + 段间留白 + 内圈高光 + 中心总额。
 */
@Composable
fun PieChartView(
    slices: List<PieSlice>,
    centerLabel: String,
    centerValue: String,
) {
    val total = slices.sumOf { it.value }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val holeColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(168.dp)) {
            val strokeWidth = 28.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            // 外圈淡影
            drawArc(
                color = Color.Black.copy(alpha = 0.06f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft + Offset(0f, 3.dp.toPx()),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )

            if (total > 0) {
                val gapAngle = if (slices.size > 1) 2.6f else 0f
                var startAngle = -90f + gapAngle / 2f
                slices.forEach { slice ->
                    val base = parseColor(slice.colorHex)
                    val sweep = slice.value.toFloat() / total * 360f
                    val sweepDraw = (sweep - gapAngle).coerceAtLeast(1.2f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(base.copy(alpha = 0.82f), base, base.copy(alpha = 0.95f))
                        ),
                        startAngle = startAngle,
                        sweepAngle = sweepDraw,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    // 内缘高光
                    drawArc(
                        color = Color.White.copy(alpha = 0.28f),
                        startAngle = startAngle,
                        sweepAngle = sweepDraw,
                        useCenter = false,
                        topLeft = topLeft + Offset(strokeWidth / 3.2f, strokeWidth / 3.2f),
                        size = Size(diameter - strokeWidth / 1.6f, diameter - strokeWidth / 1.6f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }

            // 中心实心圆，让环更干净
            val holeR = (diameter - strokeWidth) / 2f - 2.dp.toPx()
            drawCircle(holeColor, radius = holeR.coerceAtLeast(8f), center = center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerValue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                centerLabel,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 趋势图：平滑曲线 + 渐变填充 + 虚线网格。
 */
@Composable
fun LineTrendView(
    trend: List<TrendPoint>,
    expenseColor: Color = AppColors.Expense,
    incomeColor: Color = AppColors.Income,
    onPointClick: (String) -> Unit = {},
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardSurface = MaterialTheme.colorScheme.surface
    val padLeft = 40.dp
    val padRight = 14.dp
    val padTop = 14.dp
    val padBottom = 26.dp
    val gridRows = 4

    BoxWithConstraints(Modifier.fillMaxWidth().height(210.dp)) {
        val density = LocalDensity.current
        val pl = with(density) { padLeft.toPx() }
        val pr = with(density) { padRight.toPx() }
        val pt = with(density) { padTop.toPx() }
        val pb = with(density) { padBottom.toPx() }
        val plotW = constraints.maxWidth - pl - pr
        val plotH = constraints.maxHeight - pt - pb
        val maxValue = trend.maxOfOrNull { maxOf(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L

        fun xFor(i: Int): Float = pl + plotW * i / (trend.size - 1).coerceAtLeast(1)
        fun yFor(v: Long): Float = pt + plotH * (1f - v.toFloat() / maxValue)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(trend) {
                    detectTapGestures { tap ->
                        var best = -1
                        var bestDist = Float.MAX_VALUE
                        for (i in trend.indices) {
                            val d = abs(xFor(i) - tap.x)
                            if (d < bestDist) { bestDist = d; best = i }
                        }
                        if (best >= 0 && bestDist <= plotW / (trend.size * 2f)) {
                            onPointClick(trend[best].month)
                        }
                    }
                }
        ) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 9f))
            for (gi in 0..gridRows) {
                val gy = pt + plotH * gi / gridRows
                drawLine(
                    gridColor,
                    Offset(pl, gy), Offset(pl + plotW, gy),
                    strokeWidth = 1f, pathEffect = dash
                )
            }

            if (trend.size >= 2) {
                fun smoothPath(pts: List<Offset>): Path {
                    val path = Path()
                    path.moveTo(pts.first().x, pts.first().y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts.getOrElse(i - 1) { pts[i] }
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
                        val c1x = p1.x + (p2.x - p0.x) / 6f
                        val c1y = p1.y + (p2.y - p0.y) / 6f
                        val c2x = p2.x - (p3.x - p1.x) / 6f
                        val c2y = p2.y - (p3.y - p1.y) / 6f
                        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
                    }
                    return path
                }

                fun drawSeries(sel: (TrendPoint) -> Long, color: Color, fill: Boolean) {
                    val pts = trend.mapIndexed { i, p -> Offset(xFor(i), yFor(sel(p))) }
                    val path = smoothPath(pts)
                    if (fill) {
                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(pts.last().x, pt + plotH)
                            lineTo(pts.first().x, pt + plotH)
                            close()
                        }
                        drawPath(
                            fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                                startY = pt,
                                endY = pt + plotH
                            )
                        )
                    }
                    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    pts.forEach {
                        drawCircle(cardSurface, 5.dp.toPx(), it)
                        drawCircle(color, 3.4.dp.toPx(), it)
                    }
                }
                drawSeries({ it.income }, incomeColor, fill = false)
                drawSeries({ it.expense }, expenseColor, fill = true)
            }
        }

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

        if (trend.isNotEmpty()) {
            val labelW = with(density) { 26.dp.toPx() }
            trend.forEachIndexed { i, p ->
                val cx = (xFor(i) - labelW / 2f)
                    .coerceIn(pl - labelW / 2f, pl + plotW - labelW / 2f)
                Text(
                    p.month.substring(5),
                    fontSize = 9.sp,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = with(density) { cx.toDp() }, y = 186.dp)
                        .width(with(density) { labelW.toDp() })
                )
            }
        }
    }
}

@Composable
fun BarChartView(
    trend: List<TrendPoint>,
    expenseColor: Color = AppColors.Expense,
    incomeColor: Color = AppColors.Income,
    onBarClick: (String) -> Unit = {},
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val padBottom = 20.dp
    val padTop = 6.dp

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(trend) {
                detectTapGestures { tap ->
                    if (trend.isEmpty()) return@detectTapGestures
                    val cellW = size.width / trend.size
                    val idx = (tap.x / cellW).toInt().coerceIn(0, trend.size - 1)
                    onBarClick(trend[idx].month)
                }
            }
    ) {
        val density = LocalDensity.current
        val pb = with(density) { padBottom.toPx() }
        val pt = with(density) { padTop.toPx() }
        val plotH = constraints.maxHeight - pt - pb
        val cellW = constraints.maxWidth.toFloat() / trend.size.coerceAtLeast(1)
        val barW = cellW * 0.26f
        val maxValue = trend.maxOfOrNull { maxOf(it.expense, it.income) }?.coerceAtLeast(1L) ?: 1L

        Canvas(Modifier.fillMaxSize()) {
            if (trend.isEmpty()) return@Canvas
            trend.forEachIndexed { i, p ->
                val cx = cellW * (i + 0.5f)
                val gap = barW * 0.24f
                val expH = plotH * p.expense.toFloat() / maxValue
                if (p.expense > 0) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(cx - barW - gap / 2f, pt + plotH - expH),
                        size = Size(barW, expH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                    )
                }
                val incH = plotH * p.income.toFloat() / maxValue
                if (p.income > 0) {
                    drawRoundRect(
                        color = incomeColor.copy(alpha = 0.8f),
                        topLeft = Offset(cx + gap / 2f, pt + plotH - incH),
                        size = Size(barW, incH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }

        trend.forEachIndexed { i, p ->
            Text(
                p.month.substring(5),
                fontSize = 9.sp,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = with(density) { (cellW * i).toDp() }, y = 158.dp)
                    .width(with(density) { cellW.toDp() })
            )
        }
    }
}

private fun formatAxisValue(fen: Long): String {
    val yuan = fen / 100.0
    return when {
        yuan >= 10000 -> "${(yuan / 10000).let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(it) }}万"
        yuan >= 1000 -> "%.0f".format(yuan)
        else -> if (yuan == 0.0) "0" else "%.0f".format(yuan)
    }
}

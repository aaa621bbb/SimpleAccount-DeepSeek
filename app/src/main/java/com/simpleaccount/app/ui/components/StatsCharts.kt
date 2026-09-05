package com.simpleaccount.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.ui.stats.ConcUi
import com.simpleaccount.app.ui.stats.ElasticPoint
import com.simpleaccount.app.ui.stats.FlowPart
import com.simpleaccount.app.ui.stats.FreqPoint
import com.simpleaccount.app.ui.stats.HeatCell
import com.simpleaccount.app.ui.stats.MomDelta
import com.simpleaccount.app.ui.stats.ParetoPoint
import com.simpleaccount.app.ui.stats.RadarUi
import com.simpleaccount.app.ui.stats.ShareMonth
import com.simpleaccount.app.ui.stats.SparkPoint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FreqCurveView(points: List<FreqPoint>, color: Color) {
    val max = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = size.width * i / (points.size - 1).coerceAtLeast(1)
                val y = size.height * (1f - p.count.toFloat() / max)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { i, p ->
                val x = size.width * i / (points.size - 1).coerceAtLeast(1)
                val y = size.height * (1f - p.count.toFloat() / max)
                drawCircle(color, 3.dp.toPx(), Offset(x, y))
            }
        }
        if (points.isNotEmpty()) {
            Text(
                "${points.first().day} → ${points.last().day} · 峰值 ${points.maxOf { it.count }} 笔",
                fontSize = 11.sp,
                color = labelColor,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            )
        }
    }
}

@Composable
fun ShareAreaView(months: List<ShareMonth>) {
    val palette = listOf(
        Color(0xFF1F6F5B), Color(0xFFE8A87C), Color(0xFFD4523E),
        Color(0xFF5B8DEF), Color(0xFF9B8AA6),
    )
    val keys = months.firstOrNull()?.shares?.map { it.first }.orEmpty()
    Box(Modifier.fillMaxWidth().height(160.dp).padding(8.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (months.size < 2 || keys.isEmpty()) return@Canvas
            val n = months.size
            keys.forEachIndexed { ki, key ->
                val tops = FloatArray(n)
                val bots = FloatArray(n)
                months.forEachIndexed { mi, m ->
                    val below = m.shares.take(ki).sumOf { it.second.toDouble() }.toFloat()
                    val self = m.shares.getOrNull(ki)?.second ?: 0f
                    bots[mi] = below
                    tops[mi] = below + self
                }
                val path = Path()
                months.indices.forEach { i ->
                    val x = size.width * i / (n - 1)
                    val y = size.height * (1f - tops[i])
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (i in months.indices.reversed()) {
                    val x = size.width * i / (n - 1)
                    val y = size.height * (1f - bots[i])
                    path.lineTo(x, y)
                }
                path.close()
                drawPath(path, palette[ki % palette.size].copy(alpha = 0.72f))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        keys.take(5).forEachIndexed { i, k ->
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(palette[i % palette.size]))
            Spacer(Modifier.width(4.dp))
            Text(k, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
fun MomWaterView(points: List<MomDelta>, pos: Color, neg: Color) {
    val maxAbs = points.maxOfOrNull { kotlin.math.abs(it.deltaFen) }?.coerceAtLeast(1L) ?: 1L
    Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.isEmpty()) return@Canvas
            val mid = size.height / 2f
            val cell = size.width / points.size.coerceAtLeast(1)
            drawLine(Color.Gray.copy(alpha = 0.25f), Offset(0f, mid), Offset(size.width, mid), 1.5f)
            points.forEachIndexed { i, p ->
                val h = size.height * 0.45f * (kotlin.math.abs(p.deltaFen).toFloat() / maxAbs)
                val x = cell * (i + 0.5f)
                val color = if (p.deltaFen >= 0) pos else neg
                val top = if (p.deltaFen >= 0) mid - h else mid
                drawRect(color, Offset(x - cell * 0.18f, top), Size(cell * 0.36f, h.coerceAtLeast(1f)))
            }
        }
    }
}

@Composable
fun LorenzView(conc: ConcUi, color: Color) {
    Box(Modifier.fillMaxWidth().height(150.dp).padding(12.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, 0f), 1.5f)
            val pts = conc.lorenz
            if (pts.size < 2) return@Canvas
            val path = Path()
            pts.forEachIndexed { i, (x, y) ->
                val px = size.width * x
                val py = size.height * (1f - y)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ScatterElasticView(points: List<ElasticPoint>, color: Color) {
    Box(Modifier.fillMaxWidth().height(160.dp).padding(12.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val midX = size.width / 2f
            val midY = size.height / 2f
            drawLine(Color.Gray.copy(alpha = 0.25f), Offset(0f, midY), Offset(size.width, midY), 1f)
            drawLine(Color.Gray.copy(alpha = 0.25f), Offset(midX, 0f), Offset(midX, size.height), 1f)
            val span = 1.2f
            points.forEach { p ->
                val x = ((p.catMom + span) / (2 * span)).coerceIn(0.05f, 0.95f) * size.width
                val y = (1f - p.share.coerceIn(0.04f, 0.96f)) * size.height
                drawCircle(color, 5.dp.toPx(), Offset(x, y))
            }
        }
    }
}

@Composable
fun ParetoCurveView(points: List<ParetoPoint>, color: Color) {
    Box(Modifier.fillMaxWidth().height(150.dp).padding(12.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas
            val y80 = size.height * 0.2f
            drawLine(Color.Gray.copy(alpha = 0.35f), Offset(0f, y80), Offset(size.width, y80), 1.5f)
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = size.width * i / (points.size - 1).coerceAtLeast(1)
                val y = size.height * (1f - p.cumShare)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun HeatGridView(cells: List<HeatCell>, color: Color) {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    val buckets = listOf("晨", "午", "下午", "晚", "夜")
    val max = cells.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
    val map = cells.associate { (it.weekday to it.bucket) to it.amount }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Spacer(Modifier.width(22.dp))
            for (b in buckets) {
                Text(b, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        for (w in days.indices) {
            val name = days[w]
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 10.sp, modifier = Modifier.width(22.dp))
                for (b in buckets.indices) {
                    val v = map[w to b] ?: 0L
                    val a = if (v <= 0) 0.06f else (0.18f + 0.82f * (v.toFloat() / max)).coerceIn(0.18f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(18.dp)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (v <= 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else color.copy(alpha = a)),
                    )
                }
            }
        }
    }
}

@Composable
fun SparklineView(points: List<SparkPoint>, color: Color) {
    val max = points.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
    Box(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = size.width * i / (points.size - 1)
                val y = size.height * (1f - p.amount.toFloat() / max)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.18f))
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun FlowColumnsView(income: List<FlowPart>, expense: List<FlowPart>, incColor: Color, expColor: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("收入来源", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val max = income.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
            income.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontSize = 11.sp, modifier = Modifier.width(56.dp), maxLines = 1)
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth((p.amount.toFloat() / max).coerceIn(0.04f, 1f))
                                .height(8.dp).clip(RoundedCornerShape(4.dp)).background(incColor),
                        )
                    }
                }
            }
            if (income.isEmpty()) Text("本月无收入", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("支出去向", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val max = expense.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
            expense.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontSize = 11.sp, modifier = Modifier.width(56.dp), maxLines = 1)
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth((p.amount.toFloat() / max).coerceIn(0.04f, 1f))
                                .height(8.dp).clip(RoundedCornerShape(4.dp)).background(expColor),
                        )
                    }
                }
            }
            if (expense.isEmpty()) Text("本月无支出", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RadarView(radar: RadarUi, nowColor: Color, prevColor: Color) {
    Box(Modifier.fillMaxWidth().height(200.dp).padding(12.dp), contentAlignment = Alignment.Center) {
        val n = radar.labels.size
        Canvas(Modifier.size(180.dp)) {
            if (n < 3) return@Canvas
            val r = min(size.width, size.height) / 2f * 0.82f
            val c = Offset(size.width / 2f, size.height / 2f)
            fun pt(i: Int, v: Float): Offset {
                val ang = Math.toRadians((-90f + i * 360f / n).toDouble())
                return Offset(c.x + (r * v * cos(ang)).toFloat(), c.y + (r * v * sin(ang)).toFloat())
            }
            for (ring in 1..3) {
                val path = Path()
                for (i in 0 until n) {
                    val p = pt(i, ring / 3f)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, Color.Gray.copy(alpha = 0.18f), style = Stroke(1.dp.toPx()))
            }
            fun poly(values: List<Float>, color: Color, fill: Boolean) {
                if (values.size != n) return
                val path = Path()
                values.forEachIndexed { i, v ->
                    val p = pt(i, v.coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                if (fill) drawPath(path, color.copy(alpha = 0.22f))
                drawPath(path, color, style = Stroke(2.5.dp.toPx()))
            }
            poly(radar.previous, prevColor, fill = false)
            poly(radar.current, nowColor, fill = true)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        radar.labels.forEach { l ->
            Text(l, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp), maxLines = 1)
        }
    }
}

@Composable
fun ConcNumbers(conc: ConcUi) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        val cells = listOf(
            "CR1" to "${"%.0f".format(conc.cr1 * 100)}%",
            "CR3" to "${"%.0f".format(conc.cr3 * 100)}%",
            "HHI" to "${"%.0f".format(conc.hhi)}",
            "商家数" to "${conc.merchantCount}",
        )
        for ((k, v) in cells) {
            Column(Modifier.weight(1f)) {
                Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

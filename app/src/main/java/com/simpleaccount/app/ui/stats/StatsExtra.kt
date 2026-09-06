package com.simpleaccount.app.ui.stats

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.DateUtil
import java.time.YearMonth

data class FreqPoint(val day: String, val count: Int)
data class ShareMonth(val month: String, val shares: List<Pair<String, Float>>)
data class MomDelta(val month: String, val deltaFen: Long, val prevFen: Long)
data class ConcUi(
    val cr1: Float = 0f,
    val cr3: Float = 0f,
    val hhi: Float = 0f,
    val merchantCount: Int = 0,
    val lorenz: List<Pair<Float, Float>> = emptyList(),
)
data class ElasticPoint(val category: String, val catMom: Float, val share: Float)
data class ParetoPoint(val name: String, val amount: Long, val cumShare: Float)
data class HeatCell(val weekday: Int, val bucket: Int, val amount: Long)
data class SparkPoint(val day: Int, val amount: Long)
data class FlowPart(val name: String, val amount: Long)
data class RadarUi(
    val labels: List<String> = emptyList(),
    val current: List<Float> = emptyList(),
    val previous: List<Float> = emptyList(),
)

data class ExtraStats(
    val freqPoints: List<FreqPoint> = emptyList(),
    val shareMonths: List<ShareMonth> = emptyList(),
    val momDeltas: List<MomDelta> = emptyList(),
    val conc: ConcUi = ConcUi(),
    val elastic: List<ElasticPoint> = emptyList(),
    val pareto: List<ParetoPoint> = emptyList(),
    val heat: List<HeatCell> = emptyList(),
    val spark: List<SparkPoint> = emptyList(),
    val flowIncome: List<FlowPart> = emptyList(),
    val flowExpense: List<FlowPart> = emptyList(),
    val radar: RadarUi = RadarUi(),
)

object StatsExtra {

    fun compute(
        month: String,
        type: String,
        txs: List<Transaction>,
        filtered: List<Transaction>,
        trend: List<TrendPoint>,
        calPrefix: String,
    ): ExtraStats {
        val calYm = runCatching { YearMonth.parse(calPrefix) }.getOrDefault(YearMonth.now())
        val lastPrefix = calYm.minusMonths(1).toString()
        val monthTx = txs.filter { it.date.startsWith(calPrefix) }
        val lastTx = txs.filter { it.date.startsWith(lastPrefix) }

        return ExtraStats(
            freqPoints = freq(filtered),
            shareMonths = share(txs, type),
            momDeltas = mom(trend, type),
            conc = conc(filtered),
            elastic = elastic(monthTx, lastTx, type),
            pareto = pareto(filtered),
            heat = heat(filtered),
            spark = spark(monthTx.filter { it.type == type }, calYm.lengthOfMonth()),
            flowIncome = flow(monthTx.filter { it.type == Transaction.TYPE_INCOME }),
            flowExpense = flow(monthTx.filter { it.type == Transaction.TYPE_EXPENSE }),
            radar = radar(monthTx.filter { it.type == type }, lastTx.filter { it.type == type }),
        )
    }

    private fun freq(filtered: List<Transaction>): List<FreqPoint> {
        if (filtered.isEmpty()) return emptyList()
        return filtered.groupBy { it.date }.toSortedMap().map { (d, list) ->
            FreqPoint(if (d.length >= 10) d.substring(5) else d, list.size)
        }.takeLast(31)
    }

    private fun share(txs: List<Transaction>, type: String): List<ShareMonth> {
        val months = DateUtil.recentMonths(6).reversed()
        val typed = txs.filter { it.type == type }
        val top = typed.groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }
        if (top.isEmpty()) return emptyList()
        return months.map { m ->
            val slice = typed.filter { it.date.startsWith(m) }
            val tot = slice.sumOf { it.amount }.coerceAtLeast(1L)
            val by = slice.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
            val parts = top.map { c -> c to ((by[c] ?: 0L).toFloat() / tot) }
            val other = (1f - parts.sumOf { it.second.toDouble() }.toFloat()).coerceAtLeast(0f)
            ShareMonth(m, parts + ("其他" to other))
        }
    }

    private fun mom(trend: List<TrendPoint>, type: String): List<MomDelta> {
        fun v(p: TrendPoint) = if (type == Transaction.TYPE_INCOME) p.income else p.expense
        return trend.mapIndexed { i, p ->
            val prev = if (i == 0) 0L else v(trend[i - 1])
            MomDelta(p.month, v(p) - prev, prev)
        }
    }

    private fun conc(filtered: List<Transaction>): ConcUi {
        val by = filtered.filter { it.merchant.isNotBlank() }
            .groupBy { it.merchant.trim() }
            .mapValues { it.value.sumOf { t -> t.amount } }
        val tot = by.values.sum().toFloat().coerceAtLeast(1f)
        val sorted = by.values.sortedDescending()
        if (sorted.isEmpty()) return ConcUi()
        val cr1 = sorted.first() / tot
        val cr3 = sorted.take(3).sum() / tot
        val hhi = by.values.sumOf { (it / tot).toDouble().let { s -> s * s } }.toFloat() * 10_000f
        val lorenz = mutableListOf(0f to 0f)
        var acc = 0f
        val n = sorted.size.coerceAtLeast(1)
        sorted.reversed().forEachIndexed { i, v ->
            acc += v
            lorenz += ((i + 1f) / n) to (acc / tot)
        }
        return ConcUi(cr1, cr3, hhi, by.size, lorenz)
    }

    private fun elastic(
        thisTx: List<Transaction>,
        lastTx: List<Transaction>,
        type: String,
    ): List<ElasticPoint> {
        fun byCat(list: List<Transaction>) = list.filter { it.type == type }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
        val now = byCat(thisTx)
        val prev = byCat(lastTx)
        val nowTot = now.values.sum().coerceAtLeast(1L)
        val prevTot = prev.values.sum()
        if (prevTot <= 0L || now.isEmpty()) return emptyList()
        return now.keys.union(prev.keys).map { cat ->
            val a = now[cat] ?: 0L
            val b = prev[cat] ?: 0L
            val catMom = if (b <= 0L) if (a > 0) 1f else 0f else (a - b).toFloat() / b
            ElasticPoint(cat, catMom, a.toFloat() / nowTot)
        }.sortedByDescending { kotlin.math.abs(it.catMom) }.take(8)
    }

    private fun pareto(filtered: List<Transaction>): List<ParetoPoint> {
        val by = filtered.filter { it.merchant.isNotBlank() }
            .groupBy { it.merchant.trim() }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
        val tot = by.sumOf { it.second }.toFloat().coerceAtLeast(1f)
        var acc = 0L
        return by.take(12).map { (name, amt) ->
            acc += amt
            ParetoPoint(name, amt, acc / tot)
        }
    }

    private fun heat(filtered: List<Transaction>): List<HeatCell> {
        val acc = Array(7) { LongArray(5) }
        filtered.forEach { t ->
            val w = runCatching { java.time.LocalDate.parse(t.date).dayOfWeek.value }.getOrDefault(1) - 1
            if (w !in 0..6) return@forEach
            val h = t.time.substringBefore(':').toIntOrNull() ?: return@forEach
            val b = when {
                h in 6..10 -> 0
                h in 11..13 -> 1
                h in 14..17 -> 2
                h in 18..21 -> 3
                else -> 4
            }
            acc[w][b] += t.amount
        }
        val out = mutableListOf<HeatCell>()
        for (w in 0..6) for (b in 0..4) {
            if (acc[w][b] > 0) out += HeatCell(w, b, acc[w][b])
        }
        return out
    }

    private fun spark(monthTyped: List<Transaction>, days: Int): List<SparkPoint> {
        val by = monthTyped.groupBy {
            it.date.substringAfterLast('-').toIntOrNull() ?: 0
        }.mapValues { it.value.sumOf { t -> t.amount } }
        return (1..days).map { d -> SparkPoint(d, by[d] ?: 0L) }
    }

    private fun flow(list: List<Transaction>): List<FlowPart> {
        return list.groupBy { it.category.ifBlank { "未分类" } }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { FlowPart(it.first, it.second) }
    }

    private fun radar(now: List<Transaction>, prev: List<Transaction>): RadarUi {
        fun byCat(list: List<Transaction>) = list.groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
        val a = byCat(now)
        val b = byCat(prev)
        val labels = a.toList().sortedByDescending { it.second }.take(6).map { it.first }
        if (labels.size < 3) return RadarUi()
        val max = labels.maxOf { cat -> maxOf(a[cat] ?: 0L, b[cat] ?: 0L) }.toFloat().coerceAtLeast(1f)
        return RadarUi(
            labels = labels,
            current = labels.map { (a[it] ?: 0L) / max },
            previous = labels.map { (b[it] ?: 0L) / max },
        )
    }
}

package com.simpleaccount.app.data.insights

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import java.time.YearMonth

/**
 * 本月体检 + 订阅雷达：纯本地计算，不调模型。
 * 这是和市面记账 App 拉开差距的「会看账」能力——环比、异常日、固定支出一眼看到。
 */
data class SubscriptionHint(
    val merchant: String,
    val typicalFen: Long,
    val months: Int,
    val lastDate: String,
)

data class MonthHealth(
    val month: String,
    val expense: Long,
    val income: Long,
    val lastExpense: Long,
    val momPct: Double?,
    val topCategories: List<Pair<String, Long>>,
    val biggestDay: Pair<String, Long>?,
    val unusualDays: List<Pair<String, Long>>,
    val subscriptions: List<SubscriptionHint>,
    val budgetFen: Long,
    val tips: List<String>,
    val weekdayAvgFen: Long = 0L,
    val weekendAvgFen: Long = 0L,
    val nightFen: Long = 0L,
    val todayFen: Long = 0L,
    val projectedFen: Long = 0L,
    val todayDupes: List<String> = emptyList(),
) {
    val headline: String
        get() = when {
            momPct == null -> "本月已支出 ¥${MoneyUtil.fenToYuan(expense)}"
            momPct <= -10 -> "比上月少花 ${"%.0f".format(-momPct)}%"
            momPct >= 15 -> "比上月多花 ${"%.0f".format(momPct)}%"
            else -> "本月支出与上月持平"
        }

    val subline: String
        get() {
            val bits = mutableListOf<String>()
            if (subscriptions.isNotEmpty()) {
                val sum = subscriptions.sumOf { it.typicalFen }
                bits.add("订阅 ${subscriptions.size} 笔 · ¥${MoneyUtil.fenToYuan(sum)}/月")
            }
            topCategories.firstOrNull()?.let {
                bits.add("最多是${it.first} ¥${MoneyUtil.fenToYuan(it.second)}")
            }
            if (weekendAvgFen > 0 && weekdayAvgFen > 0 && weekendAvgFen > weekdayAvgFen * 14 / 10) {
                bits.add("周末日均更高")
            }
            return bits.joinToString(" · ").ifBlank { "记几笔之后这里会有洞察" }
        }
}

object InsightsEngine {

    fun compute(all: List<Transaction>, budgetFen: Long = 0L, month: String = DateUtil.thisMonth()): MonthHealth {
        val ym = runCatching { YearMonth.parse(month) }.getOrDefault(YearMonth.now())
        val last = ym.minusMonths(1).toString()
        val thisTx = all.filter { it.date.startsWith(month) }
        val lastTx = all.filter { it.date.startsWith(last) }

        fun sum(list: List<Transaction>, type: String) =
            list.filter { it.type == type }.sumOf { it.amount }

        val expense = sum(thisTx, Transaction.TYPE_EXPENSE)
        val income = sum(thisTx, Transaction.TYPE_INCOME)
        val lastExpense = sum(lastTx, Transaction.TYPE_EXPENSE)
        val momPct = if (lastExpense > 0) (expense - lastExpense) * 100.0 / lastExpense else null

        val topCategories = thisTx.filter { it.type == Transaction.TYPE_EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val byDay = thisTx.filter { it.type == Transaction.TYPE_EXPENSE }
            .groupBy { it.date }
            .mapValues { it.value.sumOf { t -> t.amount } }
        val biggestDay = byDay.maxByOrNull { it.value }?.toPair()
        val dayAvg = if (byDay.isNotEmpty()) byDay.values.average() else 0.0
        val unusualDays = byDay.filter { it.value > dayAvg * 2.2 && it.value > 5000 }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        val subscriptions = detectSubscriptions(all)
        val lifestyle = lifestyle(thisTx)
        val today = DateUtil.today()
        val todayFen = thisTx.filter { it.date == today && it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        val dayOfMonth = java.time.LocalDate.now().dayOfMonth.coerceAtLeast(1)
        val daysInMonth = ym.lengthOfMonth()
        val projected = expense * daysInMonth / dayOfMonth
        val todayDupes = thisTx.filter { it.date == today && it.type == Transaction.TYPE_EXPENSE }
            .groupBy { "${it.merchant}|${it.amount}" }
            .filter { it.value.size >= 2 && it.key.substringBefore('|').isNotBlank() }
            .map { (k, v) -> "${k.substringBefore('|')} ¥${MoneyUtil.fenToYuan(v.first().amount)} ×${v.size}" }
            .take(3)
        val tips = buildTips(
            expense, lastExpense, momPct, budgetFen, topCategories, subscriptions, unusualDays,
            lifestyle, projected, todayDupes,
        )

        return MonthHealth(
            month = month,
            expense = expense,
            income = income,
            lastExpense = lastExpense,
            momPct = momPct,
            topCategories = topCategories,
            biggestDay = biggestDay,
            unusualDays = unusualDays,
            subscriptions = subscriptions,
            budgetFen = budgetFen,
            tips = tips,
            weekdayAvgFen = lifestyle.first,
            weekendAvgFen = lifestyle.second,
            nightFen = lifestyle.third,
            todayFen = todayFen,
            projectedFen = projected,
            todayDupes = todayDupes,
        )
    }

    /** @return Triple(工作日日均, 周末日均, 夜间支出) 单位分 */
    private fun lifestyle(monthTx: List<Transaction>): Triple<Long, Long, Long> {
        val exp = monthTx.filter { it.type == Transaction.TYPE_EXPENSE }
        var weekSum = 0L; var weekDays = 0
        var endSum = 0L; var endDays = 0
        exp.groupBy { it.date }.forEach { (date, txs) ->
            val dow = runCatching { java.time.LocalDate.parse(date).dayOfWeek.value }.getOrDefault(1)
            val sum = txs.sumOf { it.amount }
            if (dow >= 6) { endSum += sum; endDays++ } else { weekSum += sum; weekDays++ }
        }
        val night = exp.filter { t ->
            val h = t.time.substringBefore(':').toIntOrNull() ?: return@filter false
            h >= 22 || h < 5
        }.sumOf { it.amount }
        val wAvg = if (weekDays > 0) weekSum / weekDays else 0L
        val eAvg = if (endDays > 0) endSum / endDays else 0L
        return Triple(wAvg, eAvg, night)
    }

    /** 连续 ≥3 个月、金额波动 ≤25% 的商家 → 订阅/固定支出 */
    fun detectSubscriptions(all: List<Transaction>): List<SubscriptionHint> {
        val exp = all.filter { it.type == Transaction.TYPE_EXPENSE && it.merchant.isNotBlank() }
        return exp.groupBy { it.merchant.trim() }.mapNotNull { (merchant, txs) ->
            val byMonth = txs.groupBy { it.date.take(7) }.mapValues { it.value.sumOf { t -> t.amount } }
            if (byMonth.size < 3) return@mapNotNull null
            val amounts = byMonth.values.sorted()
            val median = amounts[amounts.size / 2]
            if (median < 300) return@mapNotNull null // 不到 3 元的忽略
            val stable = amounts.count { kotlin.math.abs(it - median) <= median * 0.25 } 
            if (stable < 3 && stable < byMonth.size * 0.7) return@mapNotNull null
            SubscriptionHint(
                merchant = merchant,
                typicalFen = median,
                months = byMonth.size,
                lastDate = txs.maxOf { it.date },
            )
        }.sortedByDescending { it.typicalFen }.take(8)
    }

    fun toMarkdown(h: MonthHealth): String {
        val sb = StringBuilder()
        sb.appendLine("### ${h.month} 花销体检")
        sb.appendLine()
        sb.appendLine("- 本月支出 **¥${MoneyUtil.fenToYuan(h.expense)}**，收入 **¥${MoneyUtil.fenToYuan(h.income)}**")
        h.momPct?.let {
            val dir = if (it >= 0) "多花" else "少花"
            sb.appendLine("- 较上月（¥${MoneyUtil.fenToYuan(h.lastExpense)}）**$dir ${"%.1f".format(kotlin.math.abs(it))}%**")
        }
        if (h.budgetFen > 0) {
            val pct = (h.expense.toDouble() / h.budgetFen * 100).coerceAtMost(999.0)
            sb.appendLine("- 预算进度 **${"%.0f".format(pct)}%**（¥${MoneyUtil.fenToYuan(h.expense)} / ¥${MoneyUtil.fenToYuan(h.budgetFen)}）")
        }
        if (h.topCategories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### 分类排行")
            sb.appendLine()
            sb.appendLine("| 分类 | 金额 | 占比 |")
            sb.appendLine("| --- | --- | --- |")
            h.topCategories.forEach { (cat, amt) ->
                val p = if (h.expense > 0) amt * 100.0 / h.expense else 0.0
                sb.appendLine("| $cat | ¥${MoneyUtil.fenToYuan(amt)} | ${"%.0f".format(p)}% |")
            }
        }
        if (h.subscriptions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### 订阅雷达")
            sb.appendLine()
            sb.appendLine("| 商家 | 大约每月 | 已连续 |")
            sb.appendLine("| --- | --- | --- |")
            h.subscriptions.forEach {
                sb.appendLine("| ${it.merchant} | ¥${MoneyUtil.fenToYuan(it.typicalFen)} | ${it.months} 个月 |")
            }
        }
        if (h.projectedFen > 0) {
            sb.appendLine("- 按当前速度，月底预计支出 **¥${MoneyUtil.fenToYuan(h.projectedFen)}**")
        }
        if (h.weekdayAvgFen > 0 || h.weekendAvgFen > 0) {
            sb.appendLine("- 工作日日均 ¥${MoneyUtil.fenToYuan(h.weekdayAvgFen)} · 周末日均 ¥${MoneyUtil.fenToYuan(h.weekendAvgFen)}")
        }
        if (h.nightFen > 0) {
            sb.appendLine("- 夜间（22:00–05:00）已花 **¥${MoneyUtil.fenToYuan(h.nightFen)}**")
        }
        h.biggestDay?.let {
            sb.appendLine()
            sb.appendLine("- 花钱最多的一天：**${it.first}** ¥${MoneyUtil.fenToYuan(it.second)}")
        }
        if (h.unusualDays.isNotEmpty()) {
            sb.appendLine("- 异常日：" + h.unusualDays.joinToString("、") { "${it.first} ¥${MoneyUtil.fenToYuan(it.second)}" })
        }
        if (h.todayDupes.isNotEmpty()) {
            sb.appendLine("- 今天可能重复记账：" + h.todayDupes.joinToString("、"))
        }
        if (h.tips.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### 建议")
            h.tips.forEach { sb.appendLine("- $it") }
        }
        return sb.toString().trim()
    }

    private fun buildTips(
        expense: Long,
        lastExpense: Long,
        momPct: Double?,
        budgetFen: Long,
        top: List<Pair<String, Long>>,
        subs: List<SubscriptionHint>,
        unusual: List<Pair<String, Long>>,
        lifestyle: Triple<Long, Long, Long>,
        projected: Long,
        todayDupes: List<String>,
    ): List<String> {
        val tips = mutableListOf<String>()
        if (budgetFen > 0 && expense > budgetFen) {
            tips.add("已经超预算 ¥${MoneyUtil.fenToYuan(expense - budgetFen)}，剩下几天尽量压一压最大的那一类。")
        } else if (budgetFen > 0) {
            val left = budgetFen - expense
            val day = java.time.LocalDate.now().dayOfMonth
            val days = java.time.YearMonth.now().lengthOfMonth()
            val remainDays = (days - day).coerceAtLeast(1)
            tips.add("预算还剩 ¥${MoneyUtil.fenToYuan(left)}，按 ${remainDays} 天摊大约每天 ¥${MoneyUtil.fenToYuan(left / remainDays)}。")
            if (projected > budgetFen) {
                tips.add("按这速度月底会到 ¥${MoneyUtil.fenToYuan(projected)}，会超预算。")
            }
        }
        if (momPct != null && momPct >= 20) {
            tips.add("这个月比上个月多花了两成，打开分类排行看是哪一块涨上去的。")
        }
        top.firstOrNull()?.let {
            if (expense > 0 && it.second * 100 / expense >= 45) {
                tips.add("${it.first}占了支出近一半，想省钱从这里下手最快。")
            }
        }
        if (subs.isNotEmpty()) {
            val sum = subs.sumOf { it.typicalFen }
            tips.add("检测到 ${subs.size} 笔固定支出约 ¥${MoneyUtil.fenToYuan(sum)}/月，到期前可以考虑有没有用不到的。")
        }
        if (unusual.isNotEmpty()) {
            tips.add("${unusual.first().first} 那天花得特别猛，回头对一下是不是有大额或重复记账。")
        }
        val (weekAvg, endAvg, night) = lifestyle
        if (endAvg > 0 && weekAvg > 0 && endAvg > weekAvg * 14 / 10) {
            tips.add("周末日均 ¥${MoneyUtil.fenToYuan(endAvg)}，比工作日高，聚餐外卖可以提前定个上限。")
        }
        if (night > 5000) {
            tips.add("夜间（22 点后）已经花了 ¥${MoneyUtil.fenToYuan(night)}，夜宵最容易不知不觉。")
        }
        if (todayDupes.isNotEmpty()) {
            tips.add("今天「${todayDupes.first()}」记了不止一次，点进去核对一下是不是重复了。")
        }
        if (tips.isEmpty() && expense > 0) tips.add("账记得挺稳，继续保持就好。")
        return tips.take(4)
    }
}

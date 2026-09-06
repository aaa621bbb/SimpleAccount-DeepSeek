package com.simpleaccount.app.data.insights

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import java.time.YearMonth
import kotlin.math.abs

/**
 * 本月体检：对照 / 漂移 / 超额 / 周期 / 动作。纯本地计算。
 * 只出可决策数字；编不出就不出，禁止「账记得挺稳」这类空话。
 */
data class SubscriptionHint(
    val merchant: String,
    val typicalFen: Long,
    val months: Int,
    val lastDate: String,
)

/** 一条可点开的建议：title 给人看，body 讲依据，evidenceIds 对应流水。 */
data class InsightTip(
    val title: String,
    val body: String,
    val evidenceIds: List<Long> = emptyList(),
    val section: String = "动作",
)

data class MonthHealth(
    val month: String,
    val expense: Long,
    val income: Long,
    val lastExpense: Long,
    val lastYearExpense: Long = 0,
    val lastPaceFen: Long = 0,
    val momPct: Double?,
    val topCategories: List<Pair<String, Long>>,
    val biggestDay: Pair<String, Long>?,
    val unusualDays: List<Pair<String, Long>>,
    val subscriptions: List<SubscriptionHint>,
    val budgetFen: Long,
    val tips: List<String>,
    val evidenceTips: List<InsightTip> = emptyList(),
    val weekdayAvgFen: Long = 0L,
    val weekendAvgFen: Long = 0L,
    val nightFen: Long = 0L,
    val todayFen: Long = 0L,
    val projectedFen: Long = 0L,
    val todayDupes: List<String> = emptyList(),
    val score: Int = 70,
    val grade: String = "B",
    val dayOfMonth: Int = 1,
    val daysInMonth: Int = 30,
) {
    val headline: String
        get() {
            val over = budgetFen > 0 && expense > budgetFen
            val paceOver = budgetFen > 0 && projectedFen > budgetFen && dayOfMonth < daysInMonth
            val momUp = momPct != null && momPct >= 12
            val top = topCategories.firstOrNull()
            return when {
                expense == 0L && income == 0L -> "这个月还没记账"
                over && top != null ->
                    "已超预算 ¥${MoneyUtil.fenToYuan(expense - budgetFen)}，最大头是「${top.first}」"
                paceOver && top != null ->
                    "按这速度月底超预算约 ¥${MoneyUtil.fenToYuan(projectedFen - budgetFen)}，先看「${top.first}」"
                momUp && lastExpense > 0 ->
                    "比上月多花 ¥${MoneyUtil.fenToYuan(expense - lastExpense)}（+${momPct!!.toInt()}%）"
                top != null && expense > 0 ->
                    "本月已花 ¥${MoneyUtil.fenToYuan(expense)}，「${top.first}」占 ${top.second * 100 / expense}%"
                else -> "本月已花 ¥${MoneyUtil.fenToYuan(expense)}"
            }
        }

    val subline: String
        get() {
            val out = mutableListOf<String>()
            if (lastPaceFen > 0) {
                val d = expense - lastPaceFen
                out.add(
                    if (d >= 0) "同期比上月多 ¥${MoneyUtil.fenToYuan(d)}"
                    else "同期比上月少 ¥${MoneyUtil.fenToYuan(-d)}"
                )
            } else if (momPct != null) {
                val dir = if (momPct >= 0) "多" else "少"
                out.add("较上月${dir}${abs(momPct).toInt()}%")
            }
            if (budgetFen > 0) {
                val left = budgetFen - expense
                out.add(
                    if (left >= 0) "预算还剩 ¥${MoneyUtil.fenToYuan(left)}"
                    else "已超 ¥${MoneyUtil.fenToYuan(-left)}"
                )
            } else if (projectedFen > 0 && dayOfMonth < daysInMonth) {
                out.add("按这速度月底约 ¥${MoneyUtil.fenToYuan(projectedFen)}")
            }
            return out.joinToString(" · ").ifBlank { "点开看对照、漂移和下一步该压哪一类" }
        }
}

object InsightsEngine {

    fun compute(all: List<Transaction>, budgetFen: Long = 0L, month: String = DateUtil.thisMonth()): MonthHealth {
        val ym = runCatching { YearMonth.parse(month) }.getOrDefault(YearMonth.now())
        val last = ym.minusMonths(1).toString()
        val lastYear = ym.minusYears(1).toString()
        val thisTx = all.filter { it.date.startsWith(month) }
        val lastTx = all.filter { it.date.startsWith(last) }

        fun sum(list: List<Transaction>, type: String) =
            list.filter { it.type == type }.sumOf { it.amount }

        val expense = sum(thisTx, Transaction.TYPE_EXPENSE)
        val income = sum(thisTx, Transaction.TYPE_INCOME)
        val lastExpense = sum(lastTx, Transaction.TYPE_EXPENSE)
        val lastYearExpense = all.filter { it.date.startsWith(lastYear) && it.type == Transaction.TYPE_EXPENSE }
            .sumOf { it.amount }
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
        val unusualDays = byDay.filter { it.value > dayAvg * 2.2 && it.value > 8_000 }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        val subscriptions = detectSubscriptions(all)
        val lifestyle = lifestyle(thisTx)
        val today = DateUtil.today()
        val todayFen = thisTx.filter { it.date == today && it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        val dayOfMonth = if (month == DateUtil.thisMonth()) java.time.LocalDate.now().dayOfMonth.coerceAtLeast(1)
        else ym.lengthOfMonth()
        val daysInMonth = ym.lengthOfMonth()
        val projected = if (dayOfMonth > 0) expense * daysInMonth / dayOfMonth else 0L
        val lastPaceFen = lastTx.filter {
            it.type == Transaction.TYPE_EXPENSE &&
                runCatching { java.time.LocalDate.parse(it.date).dayOfMonth <= dayOfMonth }.getOrDefault(false)
        }.sumOf { it.amount }
        val todayDupes = thisTx.filter { it.date == today && it.type == Transaction.TYPE_EXPENSE }
            .groupBy { "${it.merchant}|${it.amount}" }
            .filter { it.value.size >= 2 && it.key.substringBefore('|').isNotBlank() }
            .map { (k, v) -> "${k.substringBefore('|')} ¥${MoneyUtil.fenToYuan(v.first().amount)} ×${v.size}" }
            .take(3)
        val lastCats = lastTx.filter { it.type == Transaction.TYPE_EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
        val evidenceTips = buildEvidenceTips(
            thisTx, expense, income, lastExpense, lastYearExpense, lastPaceFen, momPct, budgetFen,
            topCategories, lastCats, unusualDays, lifestyle, projected, todayDupes, biggestDay,
            dayOfMonth, daysInMonth,
        )
        val tips = evidenceTips.map { "${it.title}：${it.body}" }
        val scored = scoreOf(expense, income, momPct, budgetFen, topCategories, todayDupes, lifestyle)

        return MonthHealth(
            month = month,
            expense = expense,
            income = income,
            lastExpense = lastExpense,
            lastYearExpense = lastYearExpense,
            lastPaceFen = lastPaceFen,
            momPct = momPct,
            topCategories = topCategories,
            biggestDay = biggestDay,
            unusualDays = unusualDays,
            subscriptions = subscriptions,
            budgetFen = budgetFen,
            tips = tips,
            evidenceTips = evidenceTips,
            weekdayAvgFen = lifestyle.first,
            weekendAvgFen = lifestyle.second,
            nightFen = lifestyle.third,
            todayFen = todayFen,
            projectedFen = projected,
            todayDupes = todayDupes,
            score = scored.first,
            grade = scored.second,
            dayOfMonth = dayOfMonth,
            daysInMonth = daysInMonth,
        )
    }

    private fun scoreOf(
        expense: Long,
        income: Long,
        momPct: Double?,
        budgetFen: Long,
        top: List<Pair<String, Long>>,
        dupes: List<String>,
        lifestyle: Triple<Long, Long, Long>,
    ): Pair<Int, String> {
        var s = 72
        if (income > 0) {
            val save = (income - expense).toDouble() / income
            s += (save * 18).toInt().coerceIn(-12, 16)
        }
        momPct?.let {
            s += when {
                it <= -15 -> 8
                it <= -8 -> 4
                it >= 25 -> -12
                it >= 15 -> -6
                else -> 0
            }
        }
        if (budgetFen > 0) {
            s += if (expense > budgetFen) -14 else if (expense < budgetFen * 8 / 10) 5 else 0
        }
        top.firstOrNull()?.let { (_, amt) ->
            if (expense > 0 && amt * 100 / expense >= 55) s -= 8
            else if (expense > 0 && amt * 100 / expense >= 40) s -= 3
        }
        if (dupes.isNotEmpty()) s -= 8
        val night = lifestyle.third
        if (expense > 0 && night > expense * 15 / 100) s -= 5
        s = s.coerceIn(35, 98)
        val grade = when {
            s >= 85 -> "A"
            s >= 70 -> "B"
            s >= 55 -> "C"
            else -> "D"
        }
        return s to grade
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

    /** 连续 ≥3 个月、金额波动 ≤25% 的商家。管家内部可用，体检卡片不再当指标。 */
    fun detectSubscriptions(all: List<Transaction>): List<SubscriptionHint> {
        val exp = all.filter { it.type == Transaction.TYPE_EXPENSE && it.merchant.isNotBlank() }
        return exp.groupBy { it.merchant.trim() }.mapNotNull { (merchant, txs) ->
            val byMonth = txs.groupBy { it.date.take(7) }.mapValues { it.value.sumOf { t -> t.amount } }
            if (byMonth.size < 3) return@mapNotNull null
            val amounts = byMonth.values.sorted()
            val median = amounts[amounts.size / 2]
            if (median < 300) return@mapNotNull null
            val stable = amounts.count { abs(it - median) <= median * 0.25 }
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
        sb.appendLine(h.headline)
        if (h.subline.isNotBlank()) sb.appendLine(h.subline)
        sb.appendLine()
        sb.appendLine("- 本月支出 **¥${MoneyUtil.fenToYuan(h.expense)}**，收入 **¥${MoneyUtil.fenToYuan(h.income)}**")
        h.momPct?.let {
            val dir = if (it >= 0) "多花" else "少花"
            sb.appendLine("- 较上月（¥${MoneyUtil.fenToYuan(h.lastExpense)}）**$dir ${"%.1f".format(abs(it))}%**")
        }
        if (h.lastPaceFen > 0) {
            val d = h.expense - h.lastPaceFen
            sb.appendLine("- 前 ${h.dayOfMonth} 天对照上月同期：本月 ¥${MoneyUtil.fenToYuan(h.expense)}，上月同期 ¥${MoneyUtil.fenToYuan(h.lastPaceFen)}（差 ${if (d >= 0) "+" else ""}¥${MoneyUtil.fenToYuan(abs(d))}）")
        }
        if (h.lastYearExpense > 0) {
            sb.appendLine("- 去年同月整月 ¥${MoneyUtil.fenToYuan(h.lastYearExpense)}")
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
        if (h.evidenceTips.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### 可执行项")
            h.evidenceTips.filter { it.section == "动作" }.forEach { sb.appendLine("- **${it.title}** ${it.body}") }
        }
        return sb.toString().trim()
    }

    private fun fen(v: Long) = MoneyUtil.fenToYuan(v)

    private fun buildEvidenceTips(
        thisTx: List<Transaction>,
        expense: Long,
        income: Long,
        lastExpense: Long,
        lastYearExpense: Long,
        lastPaceFen: Long,
        momPct: Double?,
        budgetFen: Long,
        top: List<Pair<String, Long>>,
        lastCats: Map<String, Long>,
        unusual: List<Pair<String, Long>>,
        lifestyle: Triple<Long, Long, Long>,
        projected: Long,
        todayDupes: List<String>,
        biggestDay: Pair<String, Long>?,
        dayOfMonth: Int,
        daysInMonth: Int,
    ): List<InsightTip> {
        val tips = mutableListOf<InsightTip>()
        val exp = thisTx.filter { it.type == Transaction.TYPE_EXPENSE }
        fun idsOf(pred: (Transaction) -> Boolean, limit: Int = 12) =
            exp.filter(pred).sortedByDescending { it.amount }.take(limit).map { it.id }

        if (expense == 0L && income == 0L) return emptyList()

        // —— 对照：整月环比 + 进度对齐 + 去年同月 ——
        if (lastExpense > 0) {
            val d = expense - lastExpense
            val pct = momPct ?: 0.0
            tips.add(
                InsightTip(
                    title = if (d >= 0) "较上月多 ¥${fen(d)}（+${pct.toInt()}%）"
                    else "较上月少 ¥${fen(-d)}（${pct.toInt()}%）",
                    body = "本月已花 ¥${fen(expense)}，上月整月 ¥${fen(lastExpense)}。整月对整月。",
                    evidenceIds = idsOf({ true }, 8),
                    section = "对照",
                )
            )
        } else if (expense > 0) {
            tips.add(
                InsightTip(
                    title = "本月已花 ¥${fen(expense)}",
                    body = "上月没有支出可对照。",
                    evidenceIds = idsOf({ true }, 8),
                    section = "对照",
                )
            )
        }
        if (lastPaceFen > 0 && dayOfMonth < daysInMonth) {
            val d = expense - lastPaceFen
            tips.add(
                InsightTip(
                    title = if (d >= 0) "前 ${dayOfMonth} 天比上月同期多 ¥${fen(d)}"
                    else "前 ${dayOfMonth} 天比上月同期少 ¥${fen(-d)}",
                    body = "本月至今 ¥${fen(expense)}，上月 1–${dayOfMonth} 日 ¥${fen(lastPaceFen)}。这是进度对齐，不是整月对整月。",
                    evidenceIds = idsOf({ true }, 8),
                    section = "对照",
                )
            )
        }
        if (lastYearExpense > 0) {
            val d = expense - lastYearExpense
            tips.add(
                InsightTip(
                    title = if (d >= 0) "较去年同月多 ¥${fen(d)}" else "较去年同月少 ¥${fen(-d)}",
                    body = "去年同月整月 ¥${fen(lastYearExpense)}。本月还没过完时只作参考。",
                    evidenceIds = emptyList(),
                    section = "对照",
                )
            )
        }

        // —— 漂移：类目占比百分点，阈值才出 ——
        top.take(5).forEach { (cat, amt) ->
            val share = if (expense > 0) amt * 100.0 / expense else 0.0
            val prev = lastCats[cat] ?: 0L
            val prevShare = if (lastExpense > 0) prev * 100.0 / lastExpense else 0.0
            val pp = share - prevShare
            val dAmt = amt - prev
            if (abs(pp) >= 3.0 || abs(dAmt) >= 5_000) {
                val ppTxt = (if (pp >= 0) "+" else "") + "${pp.toInt()} 个百分点"
                tips.add(
                    InsightTip(
                        title = "$cat 占比 ${share.toInt()}%（$ppTxt）",
                        body = "本月 ¥${fen(amt)}，上月 ¥${fen(prev)}，金额差 ${if (dAmt >= 0) "+" else ""}¥${fen(abs(dAmt))}。",
                        evidenceIds = idsOf({ it.category == cat }),
                        section = "漂移",
                    )
                )
            }
        }

        // —— 超额：必须有预算才出，禁止无据预警 ——
        if (budgetFen > 0) {
            if (expense > budgetFen) {
                val over = expense - budgetFen
                val topCat = top.firstOrNull()
                val extra = topCat?.let { (c, a) ->
                    val back = (a - (lastCats[c] ?: 0L)).coerceAtLeast(0)
                    "最大头「$c」¥${fen(a)}" + if (back > 0) "，压回上月可少花 ¥${fen(back)}。" else "。"
                } ?: "。"
                tips.add(
                    InsightTip(
                        title = "已超预算 ¥${fen(over)}",
                        body = "支出 ¥${fen(expense)} − 你设的预算 ¥${fen(budgetFen)}。$extra",
                        evidenceIds = idsOf({ true }, 10),
                        section = "超额",
                    )
                )
            } else {
                val left = budgetFen - expense
                val remainDays = (daysInMonth - dayOfMonth).coerceAtLeast(1)
                val cap = left / remainDays
                val used = (expense * 100 / budgetFen).toInt()
                tips.add(
                    InsightTip(
                        title = "预算还剩 ¥${fen(left)}",
                        body = "已用 $used%。剩余 $remainDays 天，今天建议上限 ¥${fen(cap)}（剩余预算÷剩余天数）。",
                        evidenceIds = idsOf({ true }, 6),
                        section = "超额",
                    )
                )
                if (projected > budgetFen && dayOfMonth < daysInMonth) {
                    tips.add(
                        InsightTip(
                            title = "按这速度月底超约 ¥${fen(projected - budgetFen)}",
                            body = "算法：已花 × 本月天数 ÷ 今天是几号 = ¥${fen(projected)}，对比预算 ¥${fen(budgetFen)}。还没花的日子按前几天平均估，不是断言。",
                            evidenceIds = idsOf({ true }, 6),
                            section = "超额",
                        )
                    )
                }
            }
        }

        // —— 周期 ——
        val (weekAvg, endAvg, night) = lifestyle
        biggestDay?.let { (d, amt) ->
            val share = if (expense > 0) amt * 100 / expense else 0
            tips.add(
                InsightTip(
                    title = "花钱最多的一天是 $d",
                    body = "当天 ¥${fen(amt)}，占本月支出 $share%。",
                    evidenceIds = idsOf({ it.date == d }),
                    section = "周期",
                )
            )
        }
        if (endAvg > 0 && weekAvg > 0 && endAvg > weekAvg * 14 / 10) {
            tips.add(
                InsightTip(
                    title = "周末日均比工作日高 ${((endAvg - weekAvg) * 100 / weekAvg).toInt()}%",
                    body = "周末有账日平均 ¥${fen(endAvg)}，工作日 ¥${fen(weekAvg)}（按账单日期的星期几）。",
                    evidenceIds = idsOf({
                        runCatching { java.time.LocalDate.parse(it.date).dayOfWeek.value }.getOrDefault(1) >= 6
                    }),
                    section = "周期",
                )
            )
        }
        if (expense > 0 && night > expense * 5 / 100 && night > 5_000) {
            tips.add(
                InsightTip(
                    title = "夜里（22:00–05:00）¥${fen(night)}",
                    body = "占本月支出 ${night * 100 / expense}%。按账单上的时刻统计。",
                    evidenceIds = idsOf({
                        val h = it.time.substringBefore(':').toIntOrNull() ?: return@idsOf false
                        h >= 22 || h < 5
                    }),
                    section = "周期",
                )
            )
        }
        unusual.firstOrNull()?.let { (d, amt) ->
            if (biggestDay?.first != d) {
                tips.add(
                    InsightTip(
                        title = "$d 高于有账日日均 2.2 倍",
                        body = "当天 ¥${fen(amt)}。只在「当天 > 日均×2.2 且超过 80 元」时出现。",
                        evidenceIds = idsOf({ it.date == d }),
                        section = "周期",
                    )
                )
            }
        }
        if (todayDupes.isNotEmpty()) {
            val today = DateUtil.today()
            tips.add(
                InsightTip(
                    title = "今天同一商家同一金额记了两次",
                    body = "规则：今天、同商家、同金额 ≥2 笔。" + todayDupes.joinToString("、"),
                    evidenceIds = idsOf({ it.date == today }),
                    section = "周期",
                )
            )
        }

        // —— 动作：可执行，禁止空话 ——
        top.firstOrNull()?.let { (cat, amt) ->
            val prev = lastCats[cat] ?: 0L
            if (prev > 0 && amt > prev) {
                tips.add(
                    InsightTip(
                        title = "若「$cat」回到上月水平，本月少花 ¥${fen(amt - prev)}",
                        body = "本月 $cat ¥${fen(amt)}，上月 ¥${fen(prev)}。",
                        evidenceIds = idsOf({ it.category == cat }),
                        section = "动作",
                    )
                )
            } else if (expense > 0 && amt * 100 / expense >= 30) {
                tips.add(
                    InsightTip(
                        title = "想压支出先盯「$cat」",
                        body = "占支出 ${amt * 100 / expense}%，共 ¥${fen(amt)}。",
                        evidenceIds = idsOf({ it.category == cat }),
                        section = "动作",
                    )
                )
            }
        }
        if (budgetFen > 0 && expense < budgetFen && dayOfMonth < daysInMonth) {
            val cap = (budgetFen - expense) / (daysInMonth - dayOfMonth).coerceAtLeast(1)
            if (tips.none { it.section == "动作" && it.title.contains("不超过") }) {
                tips.add(
                    InsightTip(
                        title = "今天建议不超过 ¥${fen(cap)}",
                        body = "剩余预算 ÷ 剩余天数。不是猜测。",
                        evidenceIds = emptyList(),
                        section = "动作",
                    )
                )
            }
        }
        return tips.take(12)
    }
}

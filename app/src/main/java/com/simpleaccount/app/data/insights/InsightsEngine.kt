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

// 分类排行：优先二级分类维度（有二级用「一级/二级」，无二级退回一级）
        val topCategories = thisTx.filter { it.type == Transaction.TYPE_EXPENSE }
            .groupBy { t ->
                if (t.subCategory.isNotBlank()) "${t.category}/${t.subCategory}" else t.category
            }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(8)

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
        // v2.31.0：月底测算修正——一次性/低频支出只计一次，不按剩余天数日均摊销；
        // 地铁/餐饮等常态日频支出仍按日折算。严禁一刀切豁免。
        val projected = projectMonthEnd(thisTx, dayOfMonth, daysInMonth)
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
            .groupBy { t ->
                if (t.subCategory.isNotBlank()) "${t.category}/${t.subCategory}" else t.category
            }
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

/**
     * 月底支出预估（v2.31.0 修正）：
     * - **可复发（日频）支出**：地铁、公交、餐饮外卖等，按「已发生日均 × 剩余天数」外推；
     * - **一次性/低频**：月初话费、单次火车票、大额装修等，只计已发生金额一次，不摊销。
     * 判定从严：仅当「本月出现 ≤2 天 且 非交通/餐饮日频类」才豁免日均；地铁等仍按日折算。
     */
    fun projectMonthEnd(monthTx: List<Transaction>, dayOfMonth: Int, daysInMonth: Int): Long {
        if (dayOfMonth <= 0) return 0L
        val exp = monthTx.filter { it.type == Transaction.TYPE_EXPENSE }
        if (exp.isEmpty()) return 0L
        val remainDays = (daysInMonth - dayOfMonth).coerceAtLeast(0)
        if (remainDays == 0) return exp.sumOf { it.amount }

        // 按「商家|分类|金额桶」聚类，识别一次性
        data class Cluster(
            val days: Set<String>,
            val total: Long,
            val dailyLike: Boolean,
            val oneShotHint: Boolean,
        )
        val clusters = exp.groupBy { t ->
            val amtBucket = t.amount / 100 // 元级
            "${t.merchant.ifBlank { t.category }}|${t.category}|$amtBucket"
        }.map { (_, list) ->
            val days = list.map { it.date }.toSet()
            val cat = list.first().category
            val product = list.joinToString(" ") { it.product + it.merchant + it.note }
            Cluster(
                days = days,
                total = list.sumOf { it.amount },
                dailyLike = isDailyLikeExpense(cat, product),
                oneShotHint = isOneShotHint(cat, product),
            )
        }

        var oneShot = 0L
        var recurring = 0L
        for (c in clusters) {
            // 从严：显式一次性关键词 → 只计一次；
            // 或（非日频 且 本月出现 ≤2 天）→ 一次性。地铁等 dailyLike 永不豁免。
            val treatAsOneShot = when {
                c.dailyLike -> false
                c.oneShotHint -> true
                c.days.size <= 2 -> true
                else -> false
            }
            if (treatAsOneShot) oneShot += c.total else recurring += c.total
        }
        // 可复发部分：按已发生日数日均 × 整月天数
        val recurringProjected = if (dayOfMonth > 0) recurring * daysInMonth / dayOfMonth else recurring
        return oneShot + recurringProjected
    }

    /**
     * 日频/常态支出：地铁公交餐饮外卖等，不得一次性豁免。
     * 注意：不把整个「通讯」当日常——话费/月租是低频，见 [isOneShotHint]。
     */
    fun isDailyLikeExpense(category: String, haystack: String): Boolean {
        if (isOneShotHint(category, haystack)) return false
        val cat = category
        if (cat in setOf("餐饮", "交通")) return true
        val h = haystack
        val keys = listOf(
            "地铁", "公交", "巴士", "共享单车", "哈啰", "美团单车", "青桔",
            "打车", "出租车", "滴滴", "高德打车", "曹操",
            "外卖", "堂食", "早餐", "午餐", "晚饭", "奶茶", "咖啡",
            "便利店", "超市", "菜场",
        )
        return keys.any { h.contains(it) || cat.contains(it) }
    }

    /** 一次性/低频提示：话费、火车票、机票、房租等。 */
    fun isOneShotHint(category: String, haystack: String): Boolean {
        val h = "$category $haystack"
        val keys = listOf(
            "话费", "手机费", "充值", "月租",
            "火车票", "高铁", "动车", "机票", "机票款", "飞机票",
            "房租", "物业费", "水电", "燃气费", "宽带",
            "保险", "学费", "医疗", "装修",
        )
        return keys.any { h.contains(it) }
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

// —— 漂移：类目占比百分点（二级维度键 cat 可能是「一级/二级」）——
        top.take(5).forEach { (cat, amt) ->
            val share = if (expense > 0) amt * 100.0 / expense else 0.0
            // 优先同键（一级/二级），否则回退到一级合计
            val parent = cat.substringBefore('/')
            val prev = lastCats[cat]
                ?: lastCats.filterKeys { it == parent || it.startsWith("$parent/") }.values.sum()
            val prevShare = if (lastExpense > 0) prev * 100.0 / lastExpense else 0.0
            val pp = share - prevShare
            val dAmt = amt - prev
            if (abs(pp) >= 3.0 || abs(dAmt) >= 5_000) {
                val ppTxt = (if (pp >= 0) "+" else "") + "${pp.toInt()} 个百分点"
                tips.add(
                    InsightTip(
                        title = "$cat 占比 ${share.toInt()}%（$ppTxt）",
                        body = "本月 ¥${fen(amt)}，上月同级约 ¥${fen(prev)}，金额差 ${if (dAmt >= 0) "+" else ""}¥${fen(abs(dAmt))}。",
                        evidenceIds = idsOf({ t ->
                            val key = if (t.subCategory.isNotBlank()) "${t.category}/${t.subCategory}" else t.category
                            key == cat || t.category == cat
                        }),
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
                            body = "算法：日频支出按日均外推，一次性/低频（话费、单次火车票等）只计一次不摊销；地铁等常态仍按日折算。预计 ¥${fen(projected)}，对比预算 ¥${fen(budgetFen)}。",
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

// —— 动作：可执行，禁止空话（cat 为二级维度键）——
        top.firstOrNull()?.let { (cat, amt) ->
            val parent = cat.substringBefore('/')
            val prev = lastCats[cat]
                ?: lastCats.filterKeys { it == parent || it.startsWith("$parent/") }.values.sum()
            fun matchDim(t: Transaction): Boolean {
                val key = if (t.subCategory.isNotBlank()) "${t.category}/${t.subCategory}" else t.category
                return key == cat || t.category == cat
            }
            if (prev > 0 && amt > prev) {
                tips.add(
                    InsightTip(
                        title = "若「$cat」回到上月水平，本月少花 ¥${fen(amt - prev)}",
                        body = "本月 $cat ¥${fen(amt)}，上月同级约 ¥${fen(prev)}。",
                        evidenceIds = idsOf(::matchDim),
                        section = "动作",
                    )
                )
            } else if (expense > 0 && amt * 100 / expense >= 30) {
                tips.add(
                    InsightTip(
                        title = "想压支出先盯「$cat」",
                        body = "占支出 ${amt * 100 / expense}%，共 ¥${fen(amt)}。",
                        evidenceIds = idsOf(::matchDim),
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

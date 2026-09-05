package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.ClassificationService
import com.simpleaccount.app.util.DateResolver
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地会计：只拦截「查实数 / 记一笔」这类题。
 *
 * 判定标准（宁可漏给模型，也不截胡分析题）：
 * 1. 含判断/建议词（分析、怎么办、值不值、怎么样……）→ 一律交给模型。
 * 2. 体检 / 月报 / 花哪了：模型可用时交给模型（数字已在快照里，模型负责写观察）；
 *    没配 Key 才本地出 Markdown，保证「不开 AI 也能看账」。
 * 3. 其余必须整句匹配「某天/某月/某类/某商家花了多少」或「帮我记 X 元」，
 *    模糊包含不算。质量不会因为走本地而下降——这类题模型反而常把日期搞错。
 */
@Singleton
class LocalAccountant @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val classificationService: ClassificationService,
) {

    suspend fun tryAnswer(userMessage: String, modelAvailable: Boolean): String? {
        val s = userMessage.trim().trim('？', '?', '。', '！', '!')
        if (s.isEmpty() || s.length > 48) return null
        if (isJudgment(s)) return null

        // 口语记账：有明确金额才本地落账（比模型调工具稳）
        parseAdd(s)?.let { return it }

        val all = accountRepository.getAll()

        daySpend(s, all)?.let { return it }
        monthSpend(s, all)?.let { return it }
        categorySpend(s, all)?.let { return it }
        topCategory(s, all)?.let { return it }
        merchantSpend(s, all)?.let { return it }

        // 体检类：没模型才本地出；有模型让它基于快照写建议
        if (!modelAvailable && isHealthAsk(s)) {
            return InsightsEngine.toMarkdown(
                InsightsEngine.compute(all, settingsRepository.monthlyBudget())
            )
        }
        return null
    }

    /** 要观点、对比、规划 → 模型。数字题即使带「多少」只要夹了这些词也不截。 */
    private fun isJudgment(s: String): Boolean {
        val keys = listOf(
            "分析", "建议", "规划", "对比", "比较", "为什么", "怎么办", "怎么省",
            "该不该", "要不要", "值不值", "划不划算", "划算", "能不能", "好不好",
            "怎么样", "如何", "评价", "点评", "解读", "总结", "有没有必要",
            "省钱", "超了吗", "正常吗", "合理吗", "太多了", "会不会",
        )
        return keys.any { s.contains(it) }
    }

    private fun isHealthAsk(s: String): Boolean =
        Regex("体检|月报|花哪了|消费报告|花钱体检|订阅|固定支出").containsMatchIn(s)

    private fun daySpend(s: String, all: List<Transaction>): String? {
        val m = Regex(
            "^(?:请问|帮我看|查一下)?(大前天|前天|昨天|昨日|今天|今日)(?:一共|总共)?(?:花了|支出|用了|消费)多少(?:钱|元)?(?:啊|呢|呀)?$"
        ).find(s) ?: return null
        val date = DateResolver.resolveFlexible(m.groupValues[1]) ?: return null
        return formatDay(date, all.filter { it.date == date })
    }

    private fun monthSpend(s: String, all: List<Transaction>): String? {
        if (!Regex("^(?:请问|帮我看|查一下)?(这个月|本月|上个月|上月)(?:一共|总共)?(?:花了|支出|用了)多少(?:钱|元)?(?:啊|呢|呀)?$")
                .matches(s)
        ) return null
        val month = DateResolver.resolveMonth(s) ?: DateUtil.thisMonth()
        return formatMonth(month, all.filter { it.date.startsWith(month) })
    }

    private suspend fun categorySpend(s: String, all: List<Transaction>): String? {
        val cats = categoryRepository.getAll().map { it.name }.filter { it.length >= 2 }.sortedByDescending { it.length }
        val hit = cats.firstOrNull { cat ->
            Regex("^(?:请问|帮我看)?(?:这个月|本月|上个月|上月)?${Regex.escape(cat)}(?:一共|总共)?(?:花了|支出了|用了)多少(?:钱|元)?(?:啊|呢|呀)?$")
                .matches(s)
        } ?: return null
        val month = DateResolver.resolveMonth(s)
        val txs = all.filter { it.category == hit }
            .filter { month == null || it.date.startsWith(month) }
            .filter { it.type == Transaction.TYPE_EXPENSE }
        val sum = txs.sumOf { it.amount }
        val label = month ?: "全部时间"
        if (txs.isEmpty()) return "【$hit】在 $label 没有支出记录。"
        return "### $hit · $label\n\n共 **${txs.size}** 笔，合计 **¥${MoneyUtil.fenToYuan(sum)}**。\n\n" +
            txs.sortedByDescending { it.date }.take(8).joinToString("\n") {
                "- ${it.date} ${it.merchant.ifBlank { it.product.ifBlank { hit } }}  ¥${MoneyUtil.fenToYuan(it.amount)}"
            }
    }

    private fun topCategory(s: String, all: List<Transaction>): String? {
        if (!Regex("^(?:这个月|本月|上个月)?(?:哪类|哪个分类|什么)(?:支出)?最多(?:啊|呢|呀)?$").matches(s)) return null
        val month = DateResolver.resolveMonth(s) ?: DateUtil.thisMonth()
        val h = InsightsEngine.compute(all, 0, month)
        val top = h.topCategories.firstOrNull() ?: return "$month 还没有支出。"
        return "$month 花得最多的是 **${top.first}**，¥${MoneyUtil.fenToYuan(top.second)}。"
    }

    private fun merchantSpend(s: String, all: List<Transaction>): String? {
        val m = Regex(
            "^(?:请问)?(?:这个月|本月|上个月)?(?:在|给)?([\\u4e00-\\u9fa5A-Za-z0-9]{2,12})(?:一共|总共)?(?:花了|用了)多少(?:钱|元)?(?:啊|呢|呀)?$"
        ).find(s) ?: return null
        val raw = m.groupValues[1]
        if (raw in listOf("这个月", "本月", "上个月", "上月", "哪里", "哪儿", "什么")) return null
        val month = DateResolver.resolveMonth(s)
        val txs = all.filter { it.merchant.contains(raw) || it.product.contains(raw) }
            .filter { month == null || it.date.startsWith(month) }
        if (txs.isEmpty()) return "没找到和「$raw」有关的账单。"
        val exp = txs.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        return "### $raw\n\n共 **${txs.size}** 笔，支出 **¥${MoneyUtil.fenToYuan(exp)}**。\n\n" +
            txs.sortedByDescending { it.date }.take(8).joinToString("\n") {
                "- ${it.date} ${it.merchant.ifBlank { it.product }}  ¥${MoneyUtil.fenToYuan(it.amount)}"
            }
    }

    private suspend fun parseAdd(s: String): String? {
        if (!Regex("记(?:一笔|上|账)|帮我记").containsMatchIn(s)) return null
        val amountFen = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*元").find(s)?.groupValues?.get(1)
            ?.let { MoneyUtil.parseToFen(it) } ?: return null
        if (amountFen <= 0) return null
        val merchant = Regex("记(?:一笔|上|账)?\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})\\s*\\d").find(s)
            ?.groupValues?.get(1)
            ?.takeIf { it !in listOf("一笔", "支出", "收入") }
            ?: ""
        val type = if (s.contains("收入") || s.contains("工资")) Transaction.TYPE_INCOME else Transaction.TYPE_EXPENSE
        val date = DateResolver.resolveFlexible(s) ?: DateUtil.today()
        val valid = categoryRepository.getAll().map { it.name }.toSet()
        val category = classificationService.classifyForImport(merchant, "", "", valid, type)
        val time = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        val id = accountRepository.insert(
            Transaction(
                amount = amountFen,
                type = type,
                category = category,
                date = date,
                time = time,
                merchant = merchant,
                source = Transaction.SOURCE_MANUAL,
            )
        )
        val dir = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "已记账（流水号 **$id**）：$dir **¥${MoneyUtil.fenToYuan(amountFen)}** · $category · ${merchant.ifBlank { "未填商家" }} · $date $time\n\n记错了跟我说「撤回 $id」。"
    }

    private fun formatDay(date: String, txs: List<Transaction>): String {
        val exp = txs.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        val inc = txs.filter { it.type == Transaction.TYPE_INCOME }.sumOf { it.amount }
        if (txs.isEmpty()) return "$date 这一天账本里没有记录。"
        val sb = StringBuilder()
        sb.appendLine("### $date")
        sb.appendLine()
        sb.appendLine("支出 **¥${MoneyUtil.fenToYuan(exp)}** · 收入 **¥${MoneyUtil.fenToYuan(inc)}** · ${txs.size} 笔")
        sb.appendLine()
        sb.appendLine("| 分类 | 商家 | 金额 |")
        sb.appendLine("| --- | --- | --- |")
        txs.sortedByDescending { it.time }.take(12).forEach {
            val sign = if (it.type == Transaction.TYPE_INCOME) "+" else "-"
            sb.appendLine("| ${it.category} | ${it.merchant.ifBlank { it.product.ifBlank { "-" } }} | $sign¥${MoneyUtil.fenToYuan(it.amount)} |")
        }
        return sb.toString().trim()
    }

    private fun formatMonth(month: String, txs: List<Transaction>): String {
        val exp = txs.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        val inc = txs.filter { it.type == Transaction.TYPE_INCOME }.sumOf { it.amount }
        if (txs.isEmpty()) return "$month 还没有记账。"
        val byCat = txs.filter { it.type == Transaction.TYPE_EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(6)
        val sb = StringBuilder()
        sb.appendLine("### $month 收支")
        sb.appendLine()
        sb.appendLine("支出 **¥${MoneyUtil.fenToYuan(exp)}** · 收入 **¥${MoneyUtil.fenToYuan(inc)}** · 结余 **¥${MoneyUtil.fenToYuan(inc - exp)}** · ${txs.size} 笔")
        if (byCat.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("| 分类 | 金额 |")
            sb.appendLine("| --- | --- |")
            byCat.forEach { (c, a) -> sb.appendLine("| $c | ¥${MoneyUtil.fenToYuan(a)} |") }
        }
        return sb.toString().trim()
    }
}

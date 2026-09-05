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
 * 本地会计：常见问题不经过大模型，直接查库用中文答。
 *
 * 为什么「无论接什么模型都显得蠢」——小模型 function calling 不稳，
 * 「昨天花了多少」这种题要 2～3 轮工具才答得上，还经常日期未知。
 * 快路径在设备上 10ms 内给真实数字，弱模型也立刻聪明。
 */
@Singleton
class LocalAccountant @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val classificationService: ClassificationService,
) {

    /**
     * 能本地答就返回 Markdown；答不了返回 null，交给 AgentLoop。
     */
    suspend fun tryAnswer(userMessage: String): String? {
        val s = userMessage.trim()
        if (s.isEmpty() || s.length > 80) return null
        // 明确要模型发挥的，不截胡
        if (s.contains("分析") || s.contains("建议我") || s.contains("规划") || s.contains("对比这")) return null

        if (Regex("体检|月报|花哪了|本月怎么样|这个月怎么样|消费报告|花钱体检").containsMatchIn(s)) {
            val all = accountRepository.getAll()
            return InsightsEngine.toMarkdown(InsightsEngine.compute(all, settingsRepository.monthlyBudget()))
        }
        if (Regex("订阅|固定支出|每月固定|自动扣").containsMatchIn(s)) {
            val subs = InsightsEngine.detectSubscriptions(accountRepository.getAll())
            if (subs.isEmpty()) return "还没发现明显的订阅/固定支出。连续三个月金额接近的商家会出现在这里。"
            val h = InsightsEngine.compute(accountRepository.getAll(), settingsRepository.monthlyBudget())
            return InsightsEngine.toMarkdown(h)
        }

        parseAdd(s)?.let { return it }

        daySpend(s)?.let { return it }
        monthSpend(s)?.let { return it }
        categorySpend(s)?.let { return it }
        topCategory(s)?.let { return it }
        merchantSpend(s)?.let { return it }

        return null
    }

    private suspend fun daySpend(s: String): String? {
        val m = Regex("^(?:请问|帮我看|查一下)?(大前天|前天|昨天|昨日|今天|今日)(?:一共|总共)?(?:花了|支出|用了|消费)多少").find(s)
            ?: Regex("^(大前天|前天|昨天|昨日|今天|今日)(?:的)?(?:账|花销|支出)$").find(s)
            ?: return null
        val date = DateResolver.resolveFlexible(m.groupValues[1]) ?: return null
        val txs = accountRepository.getAll().filter { it.date == date }
        return formatDay(date, txs)
    }

    private suspend fun monthSpend(s: String): String? {
        if (!Regex("(?:这个月|本月|上个月|上月)(?:一共|总共)?(?:花了|支出|用了)多少").containsMatchIn(s)
            && s !in listOf("这个月花了多少", "本月花了多少", "上个月花了多少")
        ) {
            if (!Regex("^(?:这个月|本月|上个月)(?:支出|花销)?$").matches(s)) return null
        }
        val month = DateResolver.resolveMonth(s) ?: DateUtil.thisMonth()
        val txs = accountRepository.getAll().filter { it.date.startsWith(month) }
        return formatMonth(month, txs)
    }

    private suspend fun categorySpend(s: String): String? {
        val cats = categoryRepository.getAll().map { it.name }.filter { it.isNotBlank() }
        val hit = cats.firstOrNull { s.contains(it) } ?: return null
        if (!Regex("花了多少|支出|用了多少|一共").containsMatchIn(s) && !s.endsWith(hit)) {
            if (!s.contains("多少")) return null
        }
        val month = DateResolver.resolveMonth(s)
        val txs = accountRepository.getAll()
            .filter { it.category == hit }
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

    private suspend fun topCategory(s: String): String? {
        if (!Regex("哪类|哪个分类|什么.*最多|花.*最多的分类").containsMatchIn(s)) return null
        val month = DateResolver.resolveMonth(s) ?: DateUtil.thisMonth()
        val h = InsightsEngine.compute(accountRepository.getAll(), 0, month)
        if (h.topCategories.isEmpty()) return "$month 还没有支出。"
        return InsightsEngine.toMarkdown(h)
    }

    private suspend fun merchantSpend(s: String): String? {
        val m = Regex("(?:在|给)?(.+?)(?:花了|用了)多少").find(s) ?: return null
        val raw = m.groupValues[1].trim().trim('的', '了')
        if (raw.length !in 2..16) return null
        val month = DateResolver.resolveMonth(s)
        val txs = accountRepository.getAll()
            .filter { it.merchant.contains(raw) || it.product.contains(raw) }
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

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
    private val autoRecordRuntime: com.simpleaccount.app.auto.AutoRecordRuntime,
) {

    suspend fun tryAnswer(userMessage: String, modelAvailable: Boolean): String? {
        val s = userMessage.trim().trim('？', '?', '。', '！', '!')
        if (s.isEmpty()) return null
        val writeLike = Regex(
            "记(?:一笔|上|账|一下)|帮我记|入账|撤回|撤销|" +
                "改成|改到|归到|归类|归入|改分类|算作|算成|调成|调到|批量改|" +
                "无感|自动记账"
        ).containsMatchIn(s)
        if (s.length > 80 && !writeLike) return null
        if (s.length > 4000) return null
        if (isJudgment(s) && !writeLike) return null

        parseAdd(s)?.let { return it }
        parseWithdraw(s)?.let { return it }
        parseRecategorize(s)?.let { return it }
        parseAutoRecord(s)?.let { return it }

        if (!IntentGate.needsLedger(IntentGate.classify(s))) return null

        val all = accountRepository.getAll()

        daySpend(s, all)?.let { return it }
        monthSpend(s, all)?.let { return it }
        categorySpend(s, all)?.let { return it }
        topCategory(s, all)?.let { return it }
        merchantSpend(s, all)?.let { return it }

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

    private suspend fun parseWithdraw(s: String): String? {
        val idMatch = Regex("^撤回\\s*(\\d+)$").find(s)
        val lastPhrases = setOf(
            "撤回", "撤回一笔", "撤回一笔账", "撤回一笔账单", "帮我撤回一笔", "帮我撤回一笔账单",
            "撤回刚才", "撤回刚才那笔", "撤回刚才那笔账", "撤回刚才那笔账单",
            "撤销", "撤销一笔", "删掉刚才", "删掉刚才那笔",
            "把刚才那笔撤回", "把刚才那笔删掉", "把刚才那笔账撤回", "把刚才那笔账单撤回",
        )
        val lastish = s in lastPhrases || Regex("撤回.*一笔|撤销.*一笔|把刚才.*撤回").containsMatchIn(s)
        if (idMatch == null && !lastish) return null
        val t = if (idMatch != null) {
            val id = idMatch.groupValues[1].toLong()
            accountRepository.getById(id) ?: return "失败 rows_affected=0。流水号 $id 不在账本里（可能已经删了）。"
        } else {
            accountRepository.getAll().maxByOrNull { it.id }
                ?: return "失败 rows_affected=0。账本是空的，没有可撤回的。"
        }
        accountRepository.delete(t.id)
        if (accountRepository.getById(t.id) != null) {
            return "失败 rows_affected=0。流水号 ${t.id} 删除后仍在账本。"
        }
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "【账本已核验】rows_affected=1 已撤回流水号 **${t.id}**：$dir **¥${MoneyUtil.fenToYuan(t.amount)}** · ${t.merchant.ifBlank { t.category }} · ${t.date}。"
    }

    private suspend fun parseAutoRecord(s: String): String? {
        if (!Regex("无感|自动记账").containsMatchIn(s)) return null
        val on = Regex("打开|开启|启用|开始").containsMatchIn(s)
        val off = Regex("关闭|关掉|停止|停用").containsMatchIn(s)
        if (!on && !off) return null
        settingsRepository.setAutoRecordEnabled(on)
        autoRecordRuntime.setEnabled(on)
        return if (on)
            "已打开自动记账。请到「我的 → 无感记账」授权通知使用权，否则支付通知进不来。"
        else "已关闭自动记账。"
    }

    private suspend fun parseAdd(s: String): String? {
        val want = Regex("记(?:一笔|上|账|一下)|帮我记|入账").containsMatchIn(s)
        if (!want) return null
        if (s.contains("多少")) return null
        val amountFen = Regex("(?:¥|￥)\\s*(\\d+(?:\\.\\d{1,2})?)|(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块钱|块)")
            .find(s)?.let { m -> m.groupValues[1].ifBlank { m.groupValues[2] } }
            ?.let { MoneyUtil.parseToFen(it) }
            ?: Regex("(?:记(?:一笔|上|账|一下)|帮我记|入账)[^\\d]{0,16}(\\d+(?:\\.\\d{1,2})?)").find(s)
                ?.groupValues?.get(1)?.let { MoneyUtil.parseToFen(it) }
        if (amountFen == null || amountFen <= 0) return null
        val merchant = Regex("(?:记(?:一笔|上|账|一下)|帮我记|入账)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})\\s*(?:¥|￥|\\d)")
            .find(s)?.groupValues?.get(1)
            ?.takeIf { it !in listOf("一笔", "支出", "收入", "一下") }
            ?: Regex("(?:在|给)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})").find(s)?.groupValues?.get(1)
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
        if (accountRepository.getById(id) == null) {
            return "失败 rows_affected=0。记账后读库失败（流水号 $id）。"
        }
        val dir = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "【账本已核验】rows_affected=1 已记账（流水号 **$id**）：$dir **¥${MoneyUtil.fenToYuan(amountFen)}** · $category · ${merchant.ifBlank { "未填商家" }} · $date $time\n\n记错了跟我说「撤回 $id」。"
    }

    /**
     * 对象级 / 选择集改分类。
     * 支持：流水号、刚才那笔、某商家（最多 800 笔）、金额子集 + 其余。
     */
    private suspend fun parseRecategorize(s: String): String? {
        if (!Regex("改成|改到|归到|改归|归入|算作|算成|调成|调到|改分类|归类为|归为").containsMatchIn(s)) return null
        val valid = categoryRepository.getAll().map { it.name }.sortedByDescending { it.length }
        fun categoryIn(fragment: String): String? = valid.firstOrNull {
            Regex("(?:改成|改到|归到|改归|归入|算作|算成|调成|调到|改分类(?:为|成|到)?|归类为|归为)\\s*${Regex.escape(it)}")
                .containsMatchIn(fragment)
        }

        val idHit = Regex("(?:流水号|账单)\\s*(\\d+)").find(s)
        if (idHit != null) {
            val category = categoryIn(s) ?: return null
            val id = idHit.groupValues[1].toLong()
            val t = accountRepository.getById(id) ?: return "失败 rows_affected=0。流水号 $id 不在账本里。"
            return applyRecategorize(listOf(t), category)
        }
        if (Regex("刚才|最新").containsMatchIn(s)) {
            val category = categoryIn(s) ?: return null
            val t = accountRepository.getAll().maxByOrNull { it.id } ?: return "失败 rows_affected=0。账本是空的。"
            return applyRecategorize(listOf(t), category)
        }

        val merch = Regex(
            "把\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,24}?)(?:这几笔|那几笔|的账|全部|都|的|(?:改成|改到|归到|归类|算作|调成))"
        ).find(s)?.groupValues?.get(1)?.takeIf { it !in listOf("刚才", "这笔", "那笔") }
            ?: return null

        val pool = accountRepository.getAll().filter { it.merchant.contains(merch) || it.product.contains(merch) }
        if (pool.isEmpty()) return "失败 rows_affected=0。没找到商家「$merch」的账单。"
        if (pool.size > 800) {
            return "失败 rows_affected=0。「$merch」有 ${pool.size} 笔，超过 800。请加金额、月份或流水号收窄。"
        }

        val restSplit = Regex("其余|剩下的?|其他的?").split(s, limit = 2)
        val firstFrag = restSplit[0]
        val restFrag = restSplit.getOrNull(1)
        val firstCat = categoryIn(firstFrag) ?: categoryIn(s) ?: return null
        val amountFen = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块)").find(firstFrag)?.groupValues?.get(1)
            ?.let { MoneyUtil.parseToFen(it) }
            ?: Regex("([零一二三四五六七八九十百千万两]+)(?:元|块)").find(firstFrag)?.groupValues?.get(1)
                ?.let { MoneyUtil.parseChineseToFen(it) }

        val jobs = mutableListOf<Pair<List<Transaction>, String>>()
        if (amountFen != null && amountFen > 0) {
            val subset = pool.filter { it.amount == amountFen }
            if (subset.isEmpty()) {
                return "失败 rows_affected=0。「$merch」没有 ¥${MoneyUtil.fenToYuan(amountFen)} 的账单。"
            }
            jobs += subset to firstCat
            if (restFrag != null) {
                val restCat = categoryIn(restFrag)
                    ?: return "失败 rows_affected=0。其余要改到哪一类没看清。"
                val rest = pool.filter { it.amount != amountFen }
                if (rest.isNotEmpty()) jobs += rest to restCat
            }
        } else {
            jobs += pool to firstCat
        }

        val lines = mutableListOf<String>()
        var affected = 0
        for ((txs, cat) in jobs) {
            val r = applyRecategorize(txs, cat)
            lines += r
            affected += Regex("rows_affected=(\\d+)").findAll(r).mapNotNull { it.groupValues[1].toIntOrNull() }.sum()
        }
        if (affected == 0 && lines.any { it.contains("失败") }) return lines.joinToString("\n")
        return lines.joinToString("\n")
    }

    private suspend fun applyRecategorize(targets: List<Transaction>, category: String): String {
        if (targets.isEmpty()) return "失败 rows_affected=0。没有匹配的账单。"
        var n = 0
        targets.forEach {
            if (it.category != category) {
                accountRepository.update(it.copy(category = category, updatedAt = System.currentTimeMillis()))
                n++
            }
        }
        val verified = targets.mapNotNull { accountRepository.getById(it.id) }
        val bad = verified.filter { it.category != category }
        if (bad.isNotEmpty()) {
            return "失败 rows_affected=$n。写库后核验失败：流水号 ${bad.joinToString(",") { it.id.toString() }} 仍不是「$category」。"
        }
        if (n == 0) {
            return "rows_affected=0。选中的 ${verified.size} 笔本来就是「$category」，账本没有新的改动。"
        }
        val sample = verified.take(8).joinToString("\n") {
            "- 流水号 ${it.id} ${it.date} ${it.merchant.ifBlank { it.product }} ¥${MoneyUtil.fenToYuan(it.amount)} 现分类=${it.category}"
        }
        return "【账本已核验】rows_affected=$n matched=${verified.size} 改到「$category」。\n$sample"
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
            sb.appendLine("| --- | --- | --- |")
            byCat.forEach { (c, a) -> sb.appendLine("| $c | ¥${MoneyUtil.fenToYuan(a)} |") }
        }
        return sb.toString().trim()
    }
}

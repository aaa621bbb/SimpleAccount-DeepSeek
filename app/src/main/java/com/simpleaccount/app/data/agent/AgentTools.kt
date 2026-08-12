package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.util.MoneyUtil
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账 Agent 的工具注册表 + 执行器。
 * 对模型暴露几个「能查/算/归类」的函数，模型通过这些工具从账本里取数据，而不是凭空回答。
 */
@Singleton
class AgentTools @Inject constructor(
    private val accountRepository: AccountRepository,
    private val merchantRepository: MerchantRepository,
    private val categoryRepository: CategoryRepository,
) {

    /** 全部工具定义（提供给模型） */
    val specs: List<AgentToolSpec> = listOf(
        AgentToolSpec(
            name = "query_transactions",
            description = "查询账本交易明细。可按月份(yyyy-MM)、分类、商家/商品关键词筛选用；全部留空则返回最近50条。返回每条记录：日期/收支/金额/分类/商家/商品。",
            parameters = mapOf(
                "month" to ("string" to "月份，格式 yyyy-MM，例如 2026-03；空则不限月份"),
                "category" to ("string" to "分类名，例如 餐饮；空则不限"),
                "keyword" to ("string" to "商家或商品关键词，用于搜索；空则不限"),
                "limit" to ("integer" to "最多返回条数，默认50，最大200"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "get_summary",
            description = "获取某段区间的收支汇总。start/end 为 yyyy-MM（含），均留空则统计全部账本。返回该区间支出/收入/结余总额。",
            parameters = mapOf(
                "start" to ("string" to "起始月份 yyyy-MM，空则从最早记账开始"),
                "end" to ("string" to "结束月份 yyyy-MM，空则到最近记账为止"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "get_category_totals",
            description = "按分类统计某段的支出或收入金额。返回每个分类的合计。可通过 type 指定 expense(支出)或 income(收入)，默认支出。",
            parameters = mapOf(
                "type" to ("string" to "'expense' 支出 或 'income' 收入，默认 expense"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "list_merchants",
            description = "列出账本中出现过的所有商家（及当前分类）。用于排查一个商家被归到哪类、或找待归类商家。",
            parameters = mapOf(
                "pending_only" to ("boolean" to "true 只列待归类商家，false 列全部。默认 false"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "classify_merchants",
            description = "给指定商家批量设置分类（覆盖写回账本）。输入 JSON 格式 {\"商家名\":\"分类名\"}。分类名必须是下列之一：餐饮、交通、购物、娱乐、医疗、教育、居住、通讯、转账、其它（支出）；工资、奖金、投资、兼职、退款、其它收入（收入）。返回实际更新的条数。",
            parameters = mapOf(
                "mappings" to ("string" to "JSON 字符串，形如 {\"瑞幸咖啡\":\"餐饮\",\"滴滴出行\":\"交通\"}"),
            ),
            required = listOf("mappings"),
        ),
    )

    /** 依据模型给出的工具调用执行，返回结果文本 */
    suspend fun execute(call: AgentToolCall): AgentToolResult {
        val result = try {
            when (call.name) {
                "query_transactions" -> queryTransactions(call.arguments)
                "get_summary" -> getSummary(call.arguments)
                "get_category_totals" -> getCategoryTotals(call.arguments)
                "list_merchants" -> listMerchants(call.arguments)
                "classify_merchants" -> classifyMerchants(call.arguments)
                else -> "错误：未知工具 ${call.name}"
            }
        } catch (e: Exception) {
            "工具执行出错：${e.message}"
        }
        return AgentToolResult(call.id, call.name, result)
    }

    // ---------------- 各工具实现 ----------------

    private fun parseArgs(args: String): JSONObject =
        try { JSONObject(args) } catch (_: Exception) { JSONObject() }

    private suspend fun queryTransactions(args: String): String {
        val a = parseArgs(args)
        val month = a.optString("month").trim()
        val category = a.optString("category").trim()
        val keyword = a.optString("keyword").trim()
        val limit = a.optInt("limit", 50).coerceIn(1, 200)

        val all = accountRepository.getAll().asSequence()
            .filter { month.isEmpty() || it.date.startsWith(month) }
            .filter { category.isEmpty() || it.category == category }
            .filter {
                keyword.isEmpty() ||
                    it.merchant.contains(keyword, ignoreCase = true) ||
                    it.product.contains(keyword, ignoreCase = true)
            }
            .sortedByDescending { it.date }
            .take(limit)
            .toList()

        if (all.isEmpty()) return "没有找到符合条件的交易记录。"
        val sb = StringBuilder()
        sb.appendLine("符合条件的交易 ${all.size} 条：")
        all.forEach { t ->
            val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
            sb.appendLine("- $dir ${MoneyUtil.fenToYuan(t.amount)}元 分类:${t.category} 商家:${t.merchant.ifEmpty { "-" }} 商品:${t.product.ifEmpty { "-" }} 日期:${t.date}")
        }
        return sb.toString()
    }

    private suspend fun getSummary(args: String): String {
        val a = parseArgs(args)
        val start = a.optString("start").trim()
        val end = a.optString("end").trim()
        val all = accountRepository.getAll()
        val filtered = all.filter {
            (start.isEmpty() || it.date >= start) && (end.isEmpty() || it.date <= "$end-31")
        }
        var exp = 0L; var inc = 0L
        filtered.forEach { if (it.type == Transaction.TYPE_EXPENSE) exp += it.amount else inc += it.amount }
        return "[$start 至 $end] 支出 ${MoneyUtil.fenToYuan(exp)} 元，收入 ${MoneyUtil.fenToYuan(inc)} 元，结余 ${MoneyUtil.fenToYuan(inc - exp)} 元，共 ${filtered.size} 笔。"
    }

    private suspend fun getCategoryTotals(args: String): String {
        val a = parseArgs(args)
        val type = a.optString("type").ifBlank { Transaction.TYPE_EXPENSE }
        val all = accountRepository.getAll().filter { it.type == type }
        val byCat = mutableMapOf<String, Long>()
        all.forEach { byCat[it.category] = byCat.getOrDefault(it.category, 0L) + it.amount }
        if (byCat.isEmpty()) return "没有该类型的记录。"
        val label = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        val sb = StringBuilder()
        byCat.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
            sb.appendLine("- $label【$cat】${MoneyUtil.fenToYuan(amt)} 元")
        }
        return sb.toString()
    }

    private suspend fun listMerchants(args: String): String {
        val a = parseArgs(args)
        val pendingOnly = a.optBoolean("pending_only", false)
        val merchants = if (pendingOnly) merchantRepository.getByStatus(Merchant.STATUS_PENDING)
            else merchantRepository.getAll()
        if (merchants.isEmpty()) return "没有商家记录。"
        val sb = StringBuilder()
        sb.appendLine("商家共 ${merchants.size} 个：")
        merchants.sortedBy { it.merchant }.forEach { m ->
            val status = when (m.status) {
                Merchant.STATUS_USER_SET -> "手动"
                Merchant.STATUS_CLASSIFIED -> "已归类"
                else -> "待归类"
            }
            sb.appendLine("- ${m.merchant} → ${m.category.ifEmpty { "未设置" }}（$status）")
        }
        return sb.toString()
    }

    private suspend fun classifyMerchants(args: String): String {
        val a = parseArgs(args)
        val raw = a.optString("mappings")
        val valid = categoryRepository.getAll().map { it.name }.toSet()
        val map = try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getString(it).trim() }
        } catch (e: Exception) {
            return "参数解析失败：$raw"
        }
        var updated = 0; var skipped = 0
        for ((merchant, category) in map) {
            if (category !in valid) { skipped++; continue }
            val existing = merchantRepository.getByMerchant(merchant.trim())
            val name = merchant.trim()
            if (name.isEmpty()) { skipped++; continue }
            if (existing == null) {
                merchantRepository.insert(
                    Merchant(merchant = name, category = category, status = Merchant.STATUS_USER_SET)
                )
            } else {
                merchantRepository.update(existing.copy(category = category, status = Merchant.STATUS_USER_SET))
            }
            // 同步追改账本里该商家的分类
            accountRepository.getAllImport()
                .filter { it.merchant.trim() == name }
                .forEach { accountRepository.update(it.copy(category = category)) }
            updated++
        }
        return "已归类 $updated 个商家，$skipped 个因分类名无效跳过。"
    }
}

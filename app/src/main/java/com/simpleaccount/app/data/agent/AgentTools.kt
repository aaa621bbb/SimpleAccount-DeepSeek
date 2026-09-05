package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.util.DateUtil
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
    private val appControl: AppControlCenter,
    private val settingsRepository: com.simpleaccount.app.data.repository.SettingsRepository,
) {

    companion object {
        const val NEED_CONFIRM_PREFIX = "[NEED_CONFIRM]"
        val DESTRUCTIVE = setOf(
            "delete_transaction",
            "withdraw_transaction",
            "delete_category",
            "classify_merchants",
        )
    }

    /** 全部工具定义（提供给模型） */
    val specs: List<AgentToolSpec> = listOf(
        AgentToolSpec(
            name = "query_transactions",
            description = "查询账本交易明细。可按月份(yyyy-MM)、收支类型、分类、商家/商品关键词筛选用；全部留空则返回最近50条。返回每条记录：日期/收支/金额/分类/商家/商品。",
            parameters = mapOf(
                "month" to ("string" to "月份，格式 yyyy-MM，例如 2026-03；也接受「本月/上个月」；空则不限月份"),
                "date" to ("string" to "具体某一天 yyyy-MM-dd，例如 2026-09-03；也接受「昨天/前天/今天」。与 month 同时出现时优先用 date"),
                "type" to ("string" to "'expense' 支出 或 'income' 收入；空则不限"),
                "category" to ("string" to "分类名，例如 餐饮；空则不限"),
                "keyword" to ("string" to "商家或商品关键词，用于搜索；空则不限"),
                "limit" to ("integer" to "最多返回条数，默认50，最大200"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "get_summary",
            description = "获取某段区间的收支汇总。start/end 为月份 yyyy-MM（含），均留空则统计全部账本。返回该区间支出/收入/结余总额。",
            parameters = mapOf(
                "start" to ("string" to "起始月份 yyyy-MM（也接受 yyyy-MM-dd），空则从最早记账开始"),
                "end" to ("string" to "结束月份 yyyy-MM（也接受 yyyy-MM-dd），空则到最近记账为止"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "get_category_totals",
            description = "按分类统计某段区间的支出或收入金额，返回每个分类的合计。type 指定 expense(支出)或 income(收入)，默认支出；start/end 月份 yyyy-MM 可选，留空统计全部历史。",
            parameters = mapOf(
                "type" to ("string" to "'expense' 支出 或 'income' 收入，默认 expense"),
                "start" to ("string" to "起始月份 yyyy-MM，空则从最早记账开始"),
                "end" to ("string" to "结束月份 yyyy-MM，空则到最近记账为止"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "list_months",
            description = "列出账本覆盖的所有月份及各月支出/收入/笔数。当不确定用户问的月份有没有数据、或工具查询结果为空时，先调用它核对月份范围，不要直接说没有数据。",
            parameters = mapOf(),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "get_merchant_totals",
            description = "统计消费最多的商家排行榜（按金额降序）。可限定月份、收支类型和返回条数，适合回答「我在哪花钱最多」「某某商家一共花了多少」。",
            parameters = mapOf(
                "month" to ("string" to "月份，格式 yyyy-MM；空则统计全部"),
                "type" to ("string" to "'expense' 支出 或 'income' 收入，默认 expense"),
                "limit" to ("integer" to "最多返回商家数，默认10，最大30"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "get_daily_totals",
            description = "按天汇总某个月的收支（每天一行）。适合回答「我昨天花了多少」「本月哪天花钱最多」这类按日问题。",
            parameters = mapOf(
                "month" to ("string" to "月份，格式 yyyy-MM，例如 2026-08；必填"),
            ),
            required = listOf("month"),
        ),
        AgentToolSpec(
            name = "add_transaction",
            description = "记一笔账。当用户说「我买了什么花了多少钱，帮我记上」时调用。amount 为金额（元，必填）；merchant/product 尽量从用户话里提取；category 可选（不填自动按关键词/商家映射分类）；date 可选 yyyy-MM-dd（默认今天）；type 默认 expense（收入传 income）。返回流水号。",
            parameters = mapOf(
                "amount" to ("number" to "金额，单位元，例如 6.5"),
                "merchant" to ("string" to "商家/交易对象，可空"),
                "product" to ("string" to "商品名，可空"),
                "category" to ("string" to "分类名（餐饮/交通/购物/娱乐/医疗/教育/居住/通讯/其它 或 收入类），可不填自动分类"),
                "date" to ("string" to "日期 yyyy-MM-dd，默认今天"),
                "type" to ("string" to "'expense' 支出（默认）或 'income' 收入"),
            ),
            required = listOf("amount"),
        ),
        AgentToolSpec(
            name = "withdraw_transaction",
            description = "撤回（删除）一笔之前记的账。transaction_id 为之前 add_transaction 返回的流水号。用户说「把刚才那笔删掉/撤回」时调用。",
            parameters = mapOf(
                "transaction_id" to ("integer" to "要撤回的流水号（add_transaction 返回的 id）"),
            ),
            required = listOf("transaction_id"),
        ),
        AgentToolSpec(
            name = "update_transaction_category",
            description = "只修改某一笔交易的分类（不影响该商家其他账单）。transaction_id 为流水号（query_transactions 结果里未展示时，可先按月份/关键词查到该笔，其 id 即流水号）；category 必须是现有分类名。",
            parameters = mapOf(
                "transaction_id" to ("integer" to "流水号"),
                "category" to ("string" to "新分类名，必须是现有分类之一"),
            ),
            required = listOf("transaction_id", "category"),
        ),
        AgentToolSpec(
            name = "navigate",
            description = "跳转到 App 的任意页面。screen 可用中文名或路由：首页/账本/统计/AI管家/设置/记一笔/导入账单/AI设置/分类管理/商家归类管理/数据管理/日志/无感记账。用户说「打开xx」「带我去xx」时调用。",
            parameters = mapOf(
                "screen" to ("string" to "目标页面（中文名或路由）"),
            ),
            required = listOf("screen"),
        ),
        AgentToolSpec(
            name = "edit_transaction",
            description = "编辑一笔已有账单的任意字段（金额/日期/备注/商家/商品）。只改传入的字段。用户说「把那笔改成xx」时调用；只改分类请用 update_transaction_category。",
            parameters = mapOf(
                "transaction_id" to ("integer" to "流水号"),
                "amount" to ("number" to "新金额（元），不改不传"),
                "date" to ("string" to "新日期 yyyy-MM-dd，不改不传"),
                "note" to ("string" to "新备注，不改不传"),
                "merchant" to ("string" to "新商家，不改不传"),
                "product" to ("string" to "新商品，不改不传"),
            ),
            required = listOf("transaction_id"),
        ),
        AgentToolSpec(
            name = "delete_transaction",
            description = "彻底删除一笔账单（区别于 withdraw_transaction 的语义相同）。需要先查到流水号。",
            parameters = mapOf(
                "transaction_id" to ("integer" to "流水号"),
            ),
            required = listOf("transaction_id"),
        ),
        AgentToolSpec(
            name = "create_category",
            description = "新建分类。type 为 expense(支出) 或 income(收入)。用户说「加一个xx分类」时调用。",
            parameters = mapOf(
                "name" to ("string" to "分类名（2-4字为佳）"),
                "type" to ("string" to "'expense' 或 'income'"),
            ),
            required = listOf("name", "type"),
        ),
        AgentToolSpec(
            name = "delete_category",
            description = "删除一个自定义分类（预置分类不可删；仍有账单使用的分类会删除失败）。用户说「删掉xx分类」时调用。",
            parameters = mapOf(
                "name" to ("string" to "分类名"),
            ),
            required = listOf("name"),
        ),
        AgentToolSpec(
            name = "set_merchant_category",
            description = "给单个商家设置固定分类映射（以后该商家的账都归这类）。用户说「以后xx都算xx类」时调用。只影响以后的账，需要改历史账时说明并确认。",
            parameters = mapOf(
                "merchant" to ("string" to "商家名"),
                "category" to ("string" to "分类名"),
            ),
            required = listOf("merchant", "category"),
        ),
        AgentToolSpec(
            name = "set_monthly_budget",
            description = "设置每月预算（元）。amount 传 0 表示清除预算。用户说「帮我设个预算xx」「把预算改成xx」时调用。",
            parameters = mapOf(
                "amount" to ("number" to "每月预算金额（元），0=清除"),
            ),
            required = listOf("amount"),
        ),
        AgentToolSpec(
            name = "set_theme",
            description = "切换 App 外观：system(跟随系统)/light(浅色)/dark(深色)。用户说「换成深色模式」时调用。",
            parameters = mapOf(
                "mode" to ("string" to "'system' | 'light' | 'dark'"),
            ),
            required = listOf("mode"),
        ),
        AgentToolSpec(
            name = "list_merchants",
            description = "列出账本中出现过的所有商家（及当前分类）。用于排查一个商家被归到哪类、或找待归类商家。",
            parameters = mapOf(
                "pending_only" to ("boolean" to "true 只列待归类商家，false 列全部。默认 false"),
                "keyword" to ("string" to "只列商家名含该关键词的；空则不限"),
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
        AgentToolSpec(
            name = "get_insights",
            description = "生成本月（或指定月）花销体检：环比、分类排行、异常日、订阅/固定支出雷达。用户问「体检」「花哪了」「有没有订阅」时优先调用。",
            parameters = mapOf(
                "month" to ("string" to "月份 yyyy-MM，空则本月"),
            ),
            required = emptyList(),
        ),
    )

    /** 依据模型给出的工具调用执行。破坏性操作未确认时返回 [NEED_CONFIRM] 前缀。 */
    suspend fun execute(call: AgentToolCall, confirmed: Boolean = false): AgentToolResult {
        if (call.name in DESTRUCTIVE && !confirmed) {
            return AgentToolResult(call.id, call.name, NEED_CONFIRM_PREFIX + previewDestructive(call))
        }
        val result = try {
            when (call.name) {
                "query_transactions" -> queryTransactions(call.arguments)
                "get_summary" -> getSummary(call.arguments)
                "get_category_totals" -> getCategoryTotals(call.arguments)
                "get_merchant_totals" -> getMerchantTotals(call.arguments)
                "get_daily_totals" -> getDailyTotals(call.arguments)
                "list_months" -> listMonths(call.arguments)
                "add_transaction" -> addTransaction(call.arguments)
                "withdraw_transaction" -> withdrawTransaction(call.arguments)
                "update_transaction_category" -> updateTransactionCategory(call.arguments)
                "navigate" -> navigate(call.arguments)
                "edit_transaction" -> editTransaction(call.arguments)
                "delete_transaction" -> deleteTransaction(call.arguments)
                "create_category" -> createCategory(call.arguments)
                "delete_category" -> deleteCategory(call.arguments)
                "set_merchant_category" -> setMerchantCategory(call.arguments)
                "set_monthly_budget" -> setMonthlyBudget(call.arguments)
                "set_theme" -> setTheme(call.arguments)
                "list_merchants" -> listMerchants(call.arguments)
                "classify_merchants" -> classifyMerchants(call.arguments)
                "get_insights" -> getInsights(call.arguments)
                else -> "错误：未知工具 ${call.name}"
            }
        } catch (e: Exception) {
            "工具执行出错：${e.message}"
        }
        return AgentToolResult(call.id, call.name, result)
    }

    suspend fun previewDestructive(call: AgentToolCall): String {
        val a = parseArgs(call.arguments)
        return when (call.name) {
            "delete_transaction", "withdraw_transaction" -> {
                val id = a.optLong("transaction_id", -1L)
                val t = accountRepository.getById(id)
                if (t == null) "流水号 $id 不存在，无需删除。"
                else {
                    val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
                    "将删除流水号 $id：$dir ¥${MoneyUtil.fenToYuan(t.amount)} · ${t.merchant.ifBlank { t.product.ifBlank { t.category } }} · ${t.date}"
                }
            }
            "delete_category" -> "将删除分类「${a.optString("name")}」（预置分类无法删除；仍有账单的分类会失败）"
            "classify_merchants" -> "将批量改写商家分类并追改历史账单：${a.optString("mappings").take(120)}"
            else -> "将执行 ${call.name}"
        }
    }

    // ---------------- 各工具实现 ----------------

    private fun parseArgs(args: String): JSONObject =
        try { JSONObject(args) } catch (_: Exception) { JSONObject() }

    /**
     * 归一化模型给的月份参数：兼容 "2025-09" / "2025年9月" / "2025/9" / "2025.9" / "202509" 等。
     * 返回标准 "yyyy-MM"；无法识别返回 null。
     * 修复：模型直接把"2025年9月"当参数传时旧实现会解析失败/查空，导致 AI 误答"没有数据"。
     */
    private fun normalizeMonth(v: String): String? {
        val t = v.trim()
        if (t.isEmpty()) return null
        com.simpleaccount.app.util.DateResolver.resolveMonth(t)?.let { return it }
        com.simpleaccount.app.util.DateResolver.resolveFlexible(t)?.let { return it.take(7) }
        // 标准 yyyy-MM / yyyy-MM-dd
        Regex("^(\\d{4})[-/.年](\\d{1,2})").find(t)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (m in 1..12) return "%04d-%02d".format(y, m)
        }
        // 紧凑 yyyyMM / yyyyMMdd
        Regex("^(\\d{4})(\\d{2})").find(t)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (m in 1..12) return "%04d-%02d".format(y, m)
        }
        return null
    }

    /** 工具层再解析一次相对日期，模型传「昨天」也能用 */
    private fun normalizeDate(v: String): String? {
        val t = v.trim()
        if (t.isEmpty()) return null
        return com.simpleaccount.app.util.DateResolver.resolveFlexible(t)
    }

    /**
     * 归一化月份/日期边界：yyyy-MM → [当月首日, 次月首日)；yyyy-MM-dd → [当日, 次日)。
     * 返回 (startInclusive, endExclusive) 的字符串对。
     */
    private fun monthBounds(v: String): Pair<String, String>? {
        val ym = normalizeMonth(v) ?: return null
        val y = ym.substring(0, 4).toInt()
        val m = ym.substring(5, 7).toInt()
        val ymObj = java.time.YearMonth.of(y, m)
        return "$ym-01" to ymObj.plusMonths(1).atDay(1).toString()
    }

    private suspend fun queryTransactions(args: String): String {
        val a = parseArgs(args)
        val date = normalizeDate(a.optString("date").trim()) ?: ""
        val month = normalizeMonth(a.optString("month").trim()) ?: ""
        val type = a.optString("type").trim().lowercase()
        val category = a.optString("category").trim()
        val keyword = a.optString("keyword").trim()
        val limit = a.optInt("limit", 50).coerceIn(1, 200)

        val typeFilter = when (type) {
            "expense", "支出" -> Transaction.TYPE_EXPENSE
            "income", "收入" -> Transaction.TYPE_INCOME
            else -> null
        }

        val matched = accountRepository.getAll().asSequence()
            .filter { date.isEmpty() || it.date == date }
            .filter { date.isNotEmpty() || month.isEmpty() || it.date.startsWith(month) }
            .filter { typeFilter == null || it.type == typeFilter }
            .filter { category.isEmpty() || it.category == category }
            .filter {
                keyword.isEmpty() ||
                    it.merchant.contains(keyword, ignoreCase = true) ||
                    it.product.contains(keyword, ignoreCase = true)
            }
            .sortedByDescending { it.date }
            .toList()
        val total = matched.size
        val all = matched.take(limit)

        if (all.isEmpty()) return "没有找到符合条件的交易记录。"
        val sb = StringBuilder()
        if (total > all.size) {
            sb.appendLine("符合条件的交易共 $total 条，以下为最近 ${all.size} 条（仅为部分数据，不代表全部）：")
        } else {
            sb.appendLine("符合条件的交易 ${all.size} 条：")
        }
        all.forEach { t ->
            val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
            sb.appendLine("- (流水号:${t.id}) $dir ${MoneyUtil.fenToYuan(t.amount)}元 分类:${t.category} 商家:${t.merchant.ifEmpty { "-" }} 商品:${t.product.ifEmpty { "-" }} 日期:${t.date}")
        }
        return sb.toString()
    }

    private suspend fun getSummary(args: String): String {
        val a = parseArgs(args)
        val startBound = monthBounds(a.optString("start"))
        val endBound = monthBounds(a.optString("end"))
        val startIncl = startBound?.first ?: ""
        // end 用"区间边界"（次月首日/次日），配合 date < boundary 天然包含结束日，
        // 修复旧实现 yyyy-MM-dd 会被拼成 "2026-03-15-31" 非法串的问题
        val endExcl = endBound?.second ?: ""
        val all = accountRepository.getAll()
        val filtered = all.filter { t ->
            (startIncl.isEmpty() || t.date >= startIncl) && (endExcl.isEmpty() || t.date < endExcl)
        }
        var exp = 0L; var inc = 0L
        filtered.forEach { if (it.type == Transaction.TYPE_EXPENSE) exp += it.amount else inc += it.amount }
        return "[${a.optString("start").ifEmpty { "最早" }} 至 ${a.optString("end").ifEmpty { "最近" }}] 支出 ${MoneyUtil.fenToYuan(exp)} 元，收入 ${MoneyUtil.fenToYuan(inc)} 元，结余 ${MoneyUtil.fenToYuan(inc - exp)} 元，共 ${filtered.size} 笔。"
    }

    private suspend fun getCategoryTotals(args: String): String {
        val a = parseArgs(args)
        val type = a.optString("type").ifBlank { Transaction.TYPE_EXPENSE }
        val startBound = monthBounds(a.optString("start"))
        val endBound = monthBounds(a.optString("end"))
        val startIncl = startBound?.first ?: ""
        val endExcl = endBound?.second ?: ""
        val all = accountRepository.getAll()
            .filter { it.type == type }
            .filter { startIncl.isEmpty() || it.date >= startIncl }
            .filter { endExcl.isEmpty() || it.date < endExcl }
        val byCat = mutableMapOf<String, Long>()
        all.forEach { byCat[it.category] = byCat.getOrDefault(it.category, 0L) + it.amount }
        if (byCat.isEmpty()) return "没有该类型/区间的记录。"
        val label = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        val sb = StringBuilder()
        byCat.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
            sb.appendLine("- $label【$cat】${MoneyUtil.fenToYuan(amt)} 元")
        }
        return sb.toString()
    }

    private suspend fun getMerchantTotals(args: String): String {
        val a = parseArgs(args)
        val month = normalizeMonth(a.optString("month").trim()) ?: ""
        val type = a.optString("type").ifBlank { Transaction.TYPE_EXPENSE }
        val limit = a.optInt("limit", 10).coerceIn(1, 30)
        val all = accountRepository.getAll()
            .filter { it.type == type }
            .filter { month.isEmpty() || it.date.startsWith(month) }
        if (all.isEmpty()) return "没有符合条件的记录。"
        val byMerchant = mutableMapOf<String, Long>()
        all.forEach {
            val name = it.merchant.ifBlank { "（未记录商家）" }
            byMerchant[name] = byMerchant.getOrDefault(name, 0L) + it.amount
        }
        val label = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        val sb = StringBuilder()
        sb.appendLine("${if (month.isEmpty()) "全部时间" else month} ${label}最多的前 ${minOf(limit, byMerchant.size)} 个商家：")
        byMerchant.entries.sortedByDescending { it.value }.take(limit).forEach { (m, amt) ->
            sb.appendLine("- $m：${MoneyUtil.fenToYuan(amt)} 元")
        }
        return sb.toString()
    }

    /** 记一笔：用户口语记账入口，落 transactions 主表（source=manual），返回流水号 */
    private suspend fun addTransaction(args: String): String {
        val a = parseArgs(args)
        val amountFen = MoneyUtil.parseToFen(
            a.optString("amount").ifBlank {
                val d = a.optDouble("amount")
                if (d.isNaN()) "" else d.toString()
            }
        )
        if (amountFen == null || amountFen <= 0) return "参数错误：amount 必须是大于 0 的金额（元）。"
        val merchant = a.optString("merchant").trim()
        val product = a.optString("product").trim()
        val type = when (a.optString("type").trim().lowercase()) {
            "income", "收入" -> Transaction.TYPE_INCOME
            else -> Transaction.TYPE_EXPENSE
        }
        val date = a.optString("date").trim().ifBlank {
            java.time.LocalDate.now().toString()
        }.let { raw ->
            normalizeDate(raw)
                ?: Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").find(raw)?.let { mm ->
                    "%04d-%02d-%02d".format(mm.groupValues[1].toInt(), mm.groupValues[2].toInt(), mm.groupValues[3].toInt())
                }
                ?: java.time.LocalDate.now().toString()
        }
        // 时间（HH:mm，从参数或"今天 HH:mm"类文本里提取）
        val timeRaw = a.optString("time").trim()
        val nowTime = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        val time = if (timeRaw.isNotBlank()) {
            Regex("(\\d{1,2}):(\\d{2})").find(timeRaw)?.let { tm ->
                "%02d:%02d".format(
                    tm.groupValues[1].toInt().coerceIn(0, 23),
                    tm.groupValues[2].toInt().coerceIn(0, 59)
                )
            } ?: nowTime
        } else nowTime

        // 分类：显式指定且合法 → 直接用；否则 商家映射表 → 关键词规则 → 兜底（与导入同优先级）
        val valid = categoryRepository.getAll().map { it.name }.toSet()
        val requested = a.optString("category").trim()
        val category = if (requested.isNotEmpty() && requested in valid) requested
        else {
            val mapped = if (merchant.isNotBlank()) {
                merchantRepository.getByMerchant(merchant)?.let { m ->
                    when (m.status) {
                        com.simpleaccount.app.data.entity.Merchant.STATUS_USER_SET -> m.category
                        com.simpleaccount.app.data.entity.Merchant.STATUS_CLASSIFIED -> m.category.ifBlank { null }
                        else -> null
                    }
                }
            } else null
            mapped
                ?: com.simpleaccount.app.util.KeywordRules.classify("$merchant $product")
                ?: (if (type == Transaction.TYPE_INCOME) com.simpleaccount.app.util.CategoryPresets.DEFAULT_INCOME_CATEGORY
                    else com.simpleaccount.app.util.CategoryPresets.DEFAULT_EXPENSE_CATEGORY)
        }

        val id = accountRepository.insert(
            Transaction(
                amount = amountFen,
                type = type,
                category = category,
                date = date,
                time = time,
                merchant = merchant,
                product = product,
                source = Transaction.SOURCE_MANUAL,
            )
        )
        val dir = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "已记账（流水号 $id）：$dir ${MoneyUtil.fenToYuan(amountFen)} 元 · " +
            listOf(merchant, product, category, date).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** 撤回一笔账：按 add_transaction 返回的流水号删除 */
    private suspend fun withdrawTransaction(args: String): String {
        val a = parseArgs(args)
        val id = a.optLong("transaction_id", -1L)
        if (id <= 0) return "参数错误：transaction_id 必须是有效的流水号。"
        val t = accountRepository.getById(id) ?: return "没有找到流水号 $id 的记录（可能已删除）。"
        accountRepository.delete(id)
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "已撤回流水号 $id：$dir ${MoneyUtil.fenToYuan(t.amount)} 元 · " +
            listOf(t.merchant, t.product, t.category, t.date).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** 只改某一笔的分类（不动商家映射、不影响同商家其他账单） */
    private suspend fun updateTransactionCategory(args: String): String {
        val a = parseArgs(args)
        val id = a.optLong("transaction_id", -1L)
        val category = a.optString("category").trim()
        if (id <= 0) return "参数错误：transaction_id 必须是有效的流水号。"
        val valid = categoryRepository.getAll().map { it.name }.toSet()
        if (category !in valid) return "参数错误：分类「$category」不存在，可用分类：${valid.joinToString("、")}。"
        val t = accountRepository.getById(id) ?: return "没有找到流水号 $id 的记录。"
        accountRepository.update(t.copy(category = category, updatedAt = System.currentTimeMillis()))
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "已把流水号 $id 的分类从「${t.category}」改为「$category」：" +
            "$dir ${MoneyUtil.fenToYuan(t.amount)} 元 · ${t.merchant.ifBlank { t.product.ifBlank { t.date } }}。该商家其他账单未受影响。"
    }

    /** 操控：跳转页面 */
    private fun navigate(args: String): String {
        val a = parseArgs(args)
        val screen = a.optString("screen").trim()
        if (screen.isEmpty()) return "参数错误：screen 不能为空。可用页面：${appControl.screens.keys.joinToString("、")}。"
        return appControl.navigate(screen)
    }

    /** 操控：编辑一笔账单的任意字段（只改传入的字段） */
    private suspend fun editTransaction(args: String): String {
        val a = parseArgs(args)
        val id = a.optLong("transaction_id", -1L)
        if (id <= 0) return "参数错误：transaction_id 必须是有效的流水号。"
        val t = accountRepository.getById(id) ?: return "没有找到流水号 $id 的记录。"
        val newAmount = if (a.has("amount")) {
            val d = a.optDouble("amount")
            if (d.isNaN() || d <= 0) return "金额不合法（必须是大于 0 的元数）。"
            Math.round(d * 100)
        } else t.amount
        val newDate = a.optString("date").trim().ifBlank { t.date }
        val updated = t.copy(
            amount = newAmount,
            date = newDate,
            note = if (a.has("note")) a.optString("note") else t.note,
            merchant = if (a.has("merchant")) a.optString("merchant").trim() else t.merchant,
            product = if (a.has("product")) a.optString("product").trim() else t.product,
            updatedAt = System.currentTimeMillis()
        )
        accountRepository.update(updated)
        val dir = if (updated.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "已修改流水号 $id：$dir ${MoneyUtil.fenToYuan(updated.amount)} 元 · ${updated.date} · " +
            listOf(updated.merchant, updated.product, updated.category).filter { it.isNotBlank() }.joinToString(" · ") +
            (if (updated.note.isNotBlank()) " · 备注：${updated.note}" else "")
    }

    /** 操控：删除一笔账单 */
    private suspend fun deleteTransaction(args: String): String {
        val a = parseArgs(args)
        val id = a.optLong("transaction_id", -1L)
        if (id <= 0) return "参数错误：transaction_id 必须是有效的流水号。"
        val t = accountRepository.getById(id) ?: return "没有找到流水号 $id 的记录（可能已删除）。"
        accountRepository.delete(id)
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "已删除流水号 $id：$dir ${MoneyUtil.fenToYuan(t.amount)} 元 · " +
            listOf(t.merchant, t.product, t.date).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** 操控：新建分类 */
    private suspend fun createCategory(args: String): String {
        val a = parseArgs(args)
        val name = a.optString("name").trim()
        val type = when (a.optString("type").trim().lowercase()) {
            "income", "收入" -> com.simpleaccount.app.data.entity.Category.TYPE_INCOME
            else -> com.simpleaccount.app.data.entity.Category.TYPE_EXPENSE
        }
        if (name.isEmpty() || name.length > 8) return "参数错误：分类名需 1-8 个字。"
        if (categoryRepository.getByName(name) != null) return "分类「$name」已经存在。"
        categoryRepository.add(
            com.simpleaccount.app.data.entity.Category(
                name = name,
                type = type,
                sortOrder = 99,
                isPreset = false,
                iconName = "more_horiz",
                colorHex = "#7A9AE3",
            )
        )
        return "已创建${if (type == com.simpleaccount.app.data.entity.Category.TYPE_INCOME) "收入" else "支出"}分类「$name」。"
    }

    /** 操控：删除自定义分类（预置分类与在用分类拒绝删除） */
    private suspend fun deleteCategory(args: String): String {
        val a = parseArgs(args)
        val name = a.optString("name").trim()
        val cat = categoryRepository.getByName(name)
            ?: return "分类「$name」不存在。"
        if (cat.isPreset) return "「$name」是预置分类，不能删除。"
        val inUse = accountRepository.getAll().any { it.category == name }
        if (inUse) {
            return "分类「$name」还有账单在使用，先把这些账单改到其他分类（可以让我来改），再删除。"
        }
        categoryRepository.deleteById(cat.id)
        return "已删除分类「$name」。"
    }

    /** 操控：设置商家映射（以后该商家都归此类） */
    private suspend fun setMerchantCategory(args: String): String {
        val a = parseArgs(args)
        val merchant = a.optString("merchant").trim()
        val category = a.optString("category").trim()
        if (merchant.isEmpty()) return "参数错误：merchant 不能为空。"
        val valid = categoryRepository.getAll().map { it.name }.toSet()
        if (category !in valid) return "参数错误：分类「$category」不存在。可用：${valid.joinToString("、")}。"
        val existing = merchantRepository.getByMerchant(merchant)
        if (existing != null) {
            merchantRepository.update(
                existing.copy(category = category, status = com.simpleaccount.app.data.entity.Merchant.STATUS_USER_SET, updatedAt = System.currentTimeMillis())
            )
        } else {
            merchantRepository.insert(
                com.simpleaccount.app.data.entity.Merchant(
                    merchant = merchant,
                    category = category,
                    status = com.simpleaccount.app.data.entity.Merchant.STATUS_USER_SET,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return "已设置映射：以后「$merchant」的账都归「$category」（不影响已有账单；需要改历史账单请告诉我）。"
    }

    /** 操控：设置每月预算 */
    private suspend fun setMonthlyBudget(args: String): String {
        val a = parseArgs(args)
        val yuan = a.optDouble("amount")
        if (yuan.isNaN() || yuan < 0) return "参数错误：amount 必须是 ≥0 的金额（元）。"
        val fen = Math.round(yuan * 100)
        settingsRepository.setMonthlyBudget(fen)
        return if (fen > 0) "已设置每月预算 ${MoneyUtil.fenToYuan(fen)} 元。" else "已清除每月预算。"
    }

    /** 操控：切换外观模式 */
    private suspend fun setTheme(args: String): String {
        val a = parseArgs(args)
        val mode = when (a.optString("mode").trim().lowercase()) {
            "light", "浅色", "亮色" -> com.simpleaccount.app.data.repository.SettingsRepository.THEME_LIGHT
            "dark", "深色", "暗色" -> com.simpleaccount.app.data.repository.SettingsRepository.THEME_DARK
            "system", "跟随系统", "自动" -> com.simpleaccount.app.data.repository.SettingsRepository.THEME_SYSTEM
            else -> return "参数错误：mode 必须是 system/light/dark。"
        }
        settingsRepository.setThemeMode(mode)
        return "已切换外观为「${
            when (mode) {
                com.simpleaccount.app.data.repository.SettingsRepository.THEME_LIGHT -> "浅色"
                com.simpleaccount.app.data.repository.SettingsRepository.THEME_DARK -> "深色"
                else -> "跟随系统"
            }
        }」。"
    }

    /** 列出账本覆盖的所有月份及各月收支，供模型核对"某月有没有数据" */
    private suspend fun listMonths(args: String): String {
        val all = accountRepository.getAll()
        if (all.isEmpty()) return "账本还没有任何数据，请先在「导入」页导入微信/支付宝账单。"
        val byMonth = sortedMapOf<String, LongArray>()
        all.forEach {
            val arr = byMonth.getOrPut(it.date.take(7)) { longArrayOf(0, 0, 0) }  // [支出, 收入, 笔数]
            if (it.type == Transaction.TYPE_EXPENSE) arr[0] += it.amount else arr[1] += it.amount
            arr[2]++
        }
        val sb = StringBuilder()
        sb.appendLine("账本共覆盖 ${byMonth.size} 个月（月份 支出/收入/笔数，单位元）：")
        byMonth.forEach { (m, v) ->
            sb.appendLine("- $m 支出${MoneyUtil.fenToYuan(v[0])} 收入${MoneyUtil.fenToYuan(v[1])} 共${v[2]}笔")
        }
        return sb.toString()
    }

    private suspend fun getDailyTotals(args: String): String {
        val a = parseArgs(args)
        val month = normalizeMonth(a.optString("month").trim())
            ?: normalizeDate(a.optString("month").trim())?.take(7)
            ?: normalizeDate(a.optString("date").trim())?.take(7)
            ?: return "参数错误：month 必须是月份（yyyy-MM，如 2026-08；也接受 本月/上个月/昨天）。"
        val all = accountRepository.getAll().filter { it.date.startsWith(month) }
        if (all.isEmpty()) return "$month 没有记账记录。"
        val byDay = sortedMapOf<String, LongArray>()
        all.forEach {
            val arr = byDay.getOrPut(it.date) { longArrayOf(0, 0) }  // [支出, 收入]
            if (it.type == Transaction.TYPE_EXPENSE) arr[0] += it.amount else arr[1] += it.amount
        }
        val sb = StringBuilder()
        sb.appendLine("$month 按日汇总（日期 支出/收入，单位元）：")
        byDay.forEach { (d, v) ->
            sb.appendLine("- $d 支出${MoneyUtil.fenToYuan(v[0])} 收入${MoneyUtil.fenToYuan(v[1])}")
        }
        return sb.toString()
    }

    private suspend fun listMerchants(args: String): String {
        val a = parseArgs(args)
        val pendingOnly = a.optBoolean("pending_only", false)
        val keyword = a.optString("keyword").trim()
        val merchants = (if (pendingOnly) merchantRepository.getByStatus(Merchant.STATUS_PENDING)
            else merchantRepository.getAll())
            .filter { keyword.isEmpty() || it.merchant.contains(keyword, ignoreCase = true) }
        if (merchants.isEmpty()) return "没有符合条件的商家记录。"
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

    private suspend fun getInsights(args: String): String {
        val a = parseArgs(args)
        val month = normalizeMonth(a.optString("month").trim()) ?: DateUtil.thisMonth()
        val health = InsightsEngine.compute(
            accountRepository.getAll(),
            settingsRepository.monthlyBudget(),
            month,
        )
        return InsightsEngine.toMarkdown(health)
    }
}

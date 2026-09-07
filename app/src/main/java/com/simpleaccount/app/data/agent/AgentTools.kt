package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import org.json.JSONArray
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
    private val subCategoryRepository: com.simpleaccount.app.data.repository.SubCategoryRepository,
    private val appControl: AppControlCenter,
    private val settingsRepository: com.simpleaccount.app.data.repository.SettingsRepository,
    private val memoryStore: com.simpleaccount.app.data.memory.MemoryStore,
    private val autoRecordRuntime: com.simpleaccount.app.auto.AutoRecordRuntime,
) {

    companion object {
        const val NEED_CONFIRM_PREFIX = "[NEED_CONFIRM]"
    }

    /** 全部工具定义（提供给模型） */
    val specs: List<AgentToolSpec> = listOf(
        AgentToolSpec(
            name = "query_transactions",
            description = "查询账本交易明细。可按月份/日期/类型/分类/二级分类/商家筛。默认返回 100 条，最大 800，支持 offset 翻页一次性看 500+。超过一页用 offset 翻页。批量改 500 笔不必把每条 id 抄完，直接 reclassify_transactions(merchant/from_category/amount)。",
            parameters = mapOf(
                "month" to ("string" to "月份，格式 yyyy-MM，例如 2026-03；也接受「本月/上个月」；空则不限月份"),
                "date" to ("string" to "具体某一天 yyyy-MM-dd，例如 2026-09-03；也接受「昨天/前天/今天」。与 month 同时出现时优先用 date"),
                "type" to ("string" to "'expense' 支出 或 'income' 收入；空则不限"),
                "category" to ("string" to "一级分类名，例如 餐饮；空则不限"),
                "sub_category" to ("string" to "二级分类名，例如 早餐/午餐/晚餐/夜宵/奶茶；空则不限"),
                "keyword" to ("string" to "商家或商品关键词，用于搜索；空则不限"),
                "limit" to ("integer" to "最多返回条数，默认100，最大800（满足 500 条批量改写可视窗）"),
                "offset" to ("integer" to "跳过前 N 条，默认0，用于翻页 offset=800 看下一批"),
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
            description = "记一笔账。当用户说「我买了什么花了多少钱，帮我记上」时调用。amount 为金额（元，必填）；merchant/product 尽量从用户话里提取；category 可选（不填自动按关键词/商家映射分类）；sub_category 可选（二级分类如 早餐/午餐，不填自动推断）；date 可选 yyyy-MM-dd（默认今天），time 为独立时刻字段（HH:mm 或 上午/下午/晚上等口语，无依据不传、调用后在回复里追问，禁止默认 12:00）；type 默认 expense（收入传 income）。返回流水号。",
            parameters = mapOf(
                "amount" to ("number" to "金额，单位元，例如 6.5"),
                "merchant" to ("string" to "商家/交易对象，可空"),
                "product" to ("string" to "商品名，可空"),
                "category" to ("string" to "分类名（餐饮/交通/购物/娱乐/医疗/教育/居住/通讯/其它 或 收入类），可不填自动分类"),
                "sub_category" to ("string" to "二级分类名（早餐/午餐/晚餐/夜宵/奶茶/咖啡/打车/地铁…），可不填自动推断"),
                "date" to ("string" to "日期 yyyy-MM-dd，默认今天"),
                "time" to ("string" to "时刻 HH:mm（如 15:30）或口语（下午三点/昨晚）；用户没说几点就别传，框架会留空并提醒你追问"),
                "type" to ("string" to "'expense' 支出（默认）或 'income' 收入"),
            ),
            required = listOf("amount"),
        ),
        AgentToolSpec(
            name = "withdraw_transaction",
            description = "立刻撤回一笔账，不要再问、不要思考。transaction_id 可空：空则删最新一笔。用户说「撤回一笔账单」时马上调用。",
            parameters = mapOf(
                "transaction_id" to ("integer" to "流水号；不传则撤回账本里最新一笔"),
            ),
            required = emptyList(),
        ),
        AgentToolSpec(
            name = "set_auto_record",
            description = "打开或关闭无感记账（支付通知自动入账）。用户说「打开自动记账/无感记账」时调用，然后 navigate 到无感记账页去授权。",
            parameters = mapOf(
                "enabled" to ("boolean" to "true 打开，false 关闭"),
            ),
            required = listOf("enabled"),
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
            name = "reclassify_transactions",
            description = "把选定的若干笔改到新分类，调用后立刻写库并返回 rows_affected。优先 ids；也可 merchant / from_category / amount / month。筛选命中最多 800 笔。禁止口头说已完成。rows_affected=0 就是失败。",
            parameters = mapOf(
                "ids" to ("string" to "流水号，逗号或空格分隔，例如 12,15,18"),
                "category" to ("string" to "目标分类，必须是现有分类之一"),
                "merchant" to ("string" to "可选：只改该商家名下的匹配笔；ids 已给时忽略"),
                "from_category" to ("string" to "可选：只改当前属于该类的笔"),
                "amount" to ("number" to "可选：只改这一金额（元），例如 50 表示五十元那一笔"),
                "month" to ("string" to "可选：yyyy-MM 或「本月」"),
                "date" to ("string" to "可选：某一天 yyyy-MM-dd"),
            ),
            required = listOf("category"),
        ),
        AgentToolSpec(
            name = "navigate",
            description = "跳转到 App 的任意页面。screen 可用中文名或路由：首页/账本/统计/AI管家/设置/记一笔/导入账单/AI设置/分类管理/商家归类管理/数据管理/日志/无感记账/管家记忆/日期与时间选择器。用户说「打开xx」「带我去xx」时调用。",
            parameters = mapOf(
                "screen" to ("string" to "目标页面（中文名或路由）"),
            ),
            required = listOf("screen"),
        ),
        AgentToolSpec(
            name = "edit_transaction",
            description = "编辑一笔已有账单的任意字段（金额/日期/时刻/备注/商家/商品/二级分类）。只改传入的字段。用户说「把那笔改成xx」时调用；只改一级分类请用 update_transaction_category。",
            parameters = mapOf(
                "transaction_id" to ("integer" to "流水号"),
                "amount" to ("number" to "新金额（元），不改不传"),
                "date" to ("string" to "新日期 yyyy-MM-dd，不改不传"),
                "time" to ("string" to "新时刻 HH:mm（如 15:30）或口语（下午三点）；传空字符串=清空时刻，不改不传"),
                "sub_category" to ("string" to "新二级分类名（如 早餐），不改不传"),
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
            name = "memory_get",
            description = "检索长期记忆。keywords 空格分隔，全部词都要命中。scope=daily 只每日日志，all 含 GLOBAL。",
            parameters = mapOf(
                "keywords" to ("string" to "关键词，空格分隔"),
                "scope" to ("string" to "daily 或 all，默认 daily"),
            ),
            required = listOf("keywords"),
        ),
        AgentToolSpec(
            name = "memory_write",
            description = "写入记忆。scope=daily 写今日日志；global 仅当用户明确说「记住这个（全局）」。禁止写密码/Key。",
            parameters = mapOf(
                "title" to ("string" to "条目标题"),
                "body" to ("string" to "内容"),
                "scope" to ("string" to "daily 或 global"),
            ),
            required = listOf("title", "body"),
        ),
        AgentToolSpec(
            name = "get_insights",
            description = "生成本月（或指定月）花销体检：环比、分类排行、异常日。不要谈「固定支出」口径。用户问「体检」「花哪了」时优先调用。",
            parameters = mapOf(
                "month" to ("string" to "月份 yyyy-MM，空则本月"),
            ),
            required = emptyList(),
        ),
    )

    /**
     * 依据模型给出的工具调用执行。
     *
     * 强制确认闸门（[WriteGate]，不可绕过）：一切新增/删除/修改类工具未经用户手动批准
     * （confirmed=true）一律只返回 [NEED_CONFIRM] 预览、不落库；只读查询不受限。
     * 每次执行附带可观测状态（成功/失败、落库行数、权限、耗时），见 [AgentToolResult]。
     */
    suspend fun execute(call: AgentToolCall, confirmed: Boolean = false): AgentToolResult {
        if (WriteGate.isGated(call.name) && !confirmed) {
            return AgentToolResult(
                toolCallId = call.id,
                name = call.name,
                content = NEED_CONFIRM_PREFIX + previewWrite(call),
                ok = true,
                affectedRows = 0,
                permission = permissionOf(call.name),
                permissionNote = permissionNoteOf(call.name),
            )
        }
        val start = System.currentTimeMillis()
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
                "reclassify_transactions" -> reclassifyTransactions(call.arguments)
                "navigate" -> navigate(call.arguments)
                "edit_transaction" -> editTransaction(call.arguments)
                "delete_transaction" -> deleteTransaction(call.arguments)
                "create_category" -> createCategory(call.arguments)
                "delete_category" -> deleteCategory(call.arguments)
                "set_merchant_category" -> setMerchantCategory(call.arguments)
                "set_monthly_budget" -> setMonthlyBudget(call.arguments)
                "set_theme" -> setTheme(call.arguments)
                "set_auto_record" -> setAutoRecord(call.arguments)
                "list_merchants" -> listMerchants(call.arguments)
                "classify_merchants" -> classifyMerchants(call.arguments)
                "get_insights" -> getInsights(call.arguments)
                "memory_get" -> {
                    val a = parseArgs(call.arguments)
                    memoryStore.search(a.optString("keywords"), a.optString("scope").ifBlank { "daily" })
                }
                "memory_write" -> {
                    val a = parseArgs(call.arguments)
                    val title = a.optString("title")
                    val body = a.optString("body")
                    val scope = a.optString("scope").ifBlank { "daily" }
                    if (memoryStore.looksSecret(title + body)) "拒绝：疑似密钥，未写入。"
                    else if (scope == "global") {
                        if (memoryStore.appendGlobal("$title：$body")) "已写入 GLOBAL.md"
                        else "GLOBAL 写入失败"
                    } else {
                        memoryStore.appendDaily(title, body)
                        "已写入今日日志"
                    }
                }
                else -> "错误：未知工具 ${call.name}"
            }
        } catch (e: Exception) {
            "工具执行出错：${e.message}"
        }
        val elapsed = System.currentTimeMillis() - start
        val failed = result.startsWith("错误") || result.startsWith("参数错误") ||
            result.startsWith("工具执行出错") || result.startsWith("参数解析失败") ||
            result.startsWith("失败 rows_affected=0") || result == "拒绝：疑似密钥，未写入。"
        return AgentToolResult(
            toolCallId = call.id,
            name = call.name,
            content = result,
            ok = !failed,
            affectedRows = ToolAudit.affectedRowsOf(result),
            permission = permissionOf(call.name),
            permissionNote = permissionNoteOf(call.name),
            elapsedMs = elapsed,
        )
    }

    /**
     * 工具权限状态（框架如实检测，UI 逐条展示；模型不得编造"拿不到权限"）。
     * - set_auto_record：通知监听授权状态决定后续链路是否真能跑通；
     * - navigate：导航器未就绪时明确标记不可用及原因；
     * - 其余账本/设置工具均为纯本地操作，无需权限。
     */
    private fun permissionOf(tool: String): ToolPermission = when (tool) {
        "set_auto_record" -> {
            val granted = runCatching { autoRecordRuntime.health.value.listenerGranted }.getOrDefault(false)
            if (granted) ToolPermission.GRANTED else ToolPermission.DENIED
        }
        "navigate" -> if (appControl.navigator.value == null) ToolPermission.UNAVAILABLE else ToolPermission.NOT_REQUIRED
        else -> ToolPermission.NOT_REQUIRED
    }

    private fun permissionNoteOf(tool: String): String? = when (tool) {
        "set_auto_record" -> {
            val h = runCatching { autoRecordRuntime.health.value }.getOrNull()
            when {
                h == null -> "运行态未知"
                h.listenerGranted -> if (h.listenerBound) "通知监听已连接" else "已授权，监听尚未连接"
                else -> "通知使用权未授予：打开开关后仍需到「无感记账」页点「去授权」"
            }
        }
        "navigate" -> if (appControl.navigator.value == null) "App 导航尚未就绪（请回首页再试）" else null
        else -> null
    }

    /** 确认闸门预览：所有写工具在用户批准前展示的人话摘要（含权限提示）。 */
    suspend fun previewWrite(call: AgentToolCall): String {
        val a = parseArgs(call.arguments)
        val body = when (call.name) {
            "add_transaction" -> previewAdd(a)
            "delete_transaction" -> {
                val id = a.optLong("transaction_id", -1L)
                val t = accountRepository.getById(id)
                if (t == null) "流水号 $id 不存在，无需删除。"
                else {
                    val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
                    "将删除流水号 $id：$dir ¥${MoneyUtil.fenToYuan(t.amount)} · ${t.merchant.ifBlank { t.product.ifBlank { t.category } }} · ${t.date}"
                }
            }
            "withdraw_transaction" -> {
                val id = a.optLong("transaction_id", -1L)
                val t = if (id > 0) accountRepository.getById(id)
                else accountRepository.getAll().maxByOrNull { it.id }
                if (t == null) "没有可撤回的记录。"
                else {
                    val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
                    "将撤回流水号 ${t.id}：$dir ¥${MoneyUtil.fenToYuan(t.amount)} · ${t.merchant.ifBlank { t.product.ifBlank { t.category } }} · ${t.date}"
                }
            }
            "edit_transaction" -> previewEdit(a)
            "update_transaction_category" -> {
                val id = a.optLong("transaction_id", -1L)
                val category = a.optString("category").trim()
                val t = accountRepository.getById(id)
                if (t == null) "流水号 $id 不存在，无法改分类。"
                else "将把流水号 $id（${t.merchant.ifBlank { t.product.ifBlank { t.category } }} ¥${MoneyUtil.fenToYuan(t.amount)}）从「${t.category}」改到「$category」。"
            }
            "reclassify_transactions" -> previewReclassify(a)
            "create_category" -> {
                val typeLabel = if (a.optString("type").lowercase().contains("income")) "收入" else "支出"
                "将新建${typeLabel}分类「${a.optString("name").trim()}」。"
            }
            "delete_category" -> "将删除分类「${a.optString("name")}」（预置分类无法删除；仍有账单的分类会失败）"
            "set_merchant_category" -> "将设置商家映射：以后「${a.optString("merchant").trim()}」的账都归「${a.optString("category").trim()}」（不影响已有账单）。"
            "classify_merchants" -> "将批量改写商家分类并追改历史账单：${a.optString("mappings").take(120)}"
            "set_monthly_budget" -> {
                val yuan = a.optDouble("amount")
                if (yuan.isNaN() || yuan <= 0) "将清除每月预算。" else "将把每月预算设为 ¥${MoneyUtil.fenToYuan(Math.round(yuan * 100))}。"
            }
            "set_theme" -> "将切换 App 外观为「${a.optString("mode").trim()}」。"
            "set_auto_record" -> {
                val on = if (a.has("enabled")) a.optBoolean("enabled") else true
                if (on) "将打开无感记账（支付通知自动入账），并跳转到无感记账页。"
                else "将关闭无感记账。"
            }
            else -> "将执行 ${call.name}"
        }
        val perm = permissionNoteOf(call.name)
        return if (perm.isNullOrBlank()) body else "$body\n权限：$perm"
    }

    private suspend fun previewAdd(a: JSONObject): String {
        val rawAmount = a.optString("amount").ifBlank {
            val d = a.optDouble("amount")
            if (d.isNaN()) "" else d.toString()
        }
        val fen = MoneyUtil.parseToFen(rawAmount) ?: MoneyUtil.parseChineseToFen(rawAmount)
        val dir = if (a.optString("type").trim().lowercase() in listOf("income", "收入")) "收入" else "支出"
        val dateRaw = a.optString("date").trim()
        val timeRaw = a.optString("time").trim()
        val spoken = com.simpleaccount.app.util.SpokenTimeParser.parse(
            listOf(dateRaw, timeRaw).filter { it.isNotBlank() }.joinToString(" ")
        )
        val date = spoken.date ?: normalizeDate(dateRaw) ?: java.time.LocalDate.now().toString()
        val timeLabel = spoken.time ?: if (timeRaw.isBlank()) "时刻待补" else timeRaw
        val bits = listOf(
            a.optString("merchant").trim(),
            a.optString("product").trim(),
            a.optString("category").trim().ifBlank { "自动分类" },
            a.optString("sub_category").trim(),
            "$date $timeLabel",
        ).filter { it.isNotBlank() }.joinToString(" · ")
        return "将记一笔：$dir ¥${if (fen == null) rawAmount.ifBlank { "?" } else MoneyUtil.fenToYuan(fen)} · $bits"
    }

    private suspend fun previewEdit(a: JSONObject): String {
        val id = a.optLong("transaction_id", -1L)
        val t = accountRepository.getById(id) ?: return "流水号 $id 不存在，无法编辑。"
        val changes = mutableListOf<String>()
        if (a.has("amount")) changes += "金额→¥${a.optDouble("amount")}"
        if (a.has("date")) changes += "日期→${a.optString("date")}"
        if (a.has("time")) changes += "时刻→${a.optString("time").ifBlank { "清空" }}"
        if (a.has("merchant")) changes += "商家→${a.optString("merchant")}"
        if (a.has("product")) changes += "商品→${a.optString("product")}"
        if (a.has("note")) changes += "备注→${a.optString("note").take(20)}"
        if (a.has("sub_category")) changes += "二级分类→${a.optString("sub_category")}"
        if (changes.isEmpty()) return "流水号 $id 没有任何字段变更，无需执行。"
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "将编辑流水号 $id（$dir ¥${MoneyUtil.fenToYuan(t.amount)} · ${t.merchant.ifBlank { t.product.ifBlank { t.category } }}）：${changes.joinToString("；")}。"
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
        val subCategory = a.optString("sub_category").trim()
        val keyword = a.optString("keyword").trim()
        val limit = a.optInt("limit", 100).coerceIn(1, 800)
        val offset = a.optInt("offset", 0).coerceAtLeast(0)

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
            .filter { subCategory.isEmpty() || it.subCategory == subCategory }
            .filter {
                keyword.isEmpty() ||
                    it.merchant.contains(keyword, ignoreCase = true) ||
                    it.product.contains(keyword, ignoreCase = true) ||
                    it.subCategory.contains(keyword, ignoreCase = true)
            }
            .sortedByDescending { it.date }
            .toList()
        val total = matched.size
        val all = matched.drop(offset).take(limit)

        if (all.isEmpty()) return "没有找到符合条件的交易记录。total=$total offset=$offset"
        val sb = StringBuilder()
        sb.appendLine("total=$total offset=$offset returned=${all.size}（批量改分类请直接 reclassify_transactions，不必抄完全部流水号）")
        if (total > offset + all.size) {
            sb.appendLine("还有 ${total - offset - all.size} 条，下一页 offset=${offset + all.size}。")
        }
        all.forEach { t ->
            val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
            val catLabel = if (t.subCategory.isNotBlank()) "${t.category}/${t.subCategory}" else t.category
            val timeLabel = if (t.time.isNotBlank()) " ${t.time}" else ""
            sb.appendLine("- (流水号:${t.id}) $dir ${MoneyUtil.fenToYuan(t.amount)}元 分类:$catLabel 商家:${t.merchant.ifEmpty { "-" }} 商品:${t.product.ifEmpty { "-" }} 日期:${t.date}$timeLabel")
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

    /**
     * 记一笔：用户口语记账入口，落 transactions 主表（source=manual），返回流水号。
     *
     * 时间维度铁律（[com.simpleaccount.app.util.SpokenTimeParser]）：date/time 各自独立解析，
     * 时刻无依据时落空字符串（未知），**禁止默认 12:00**；模型应在回复里显式追问时刻。
     */
    private suspend fun addTransaction(args: String): String {
        val a = parseArgs(args)
        val amountRaw = a.optString("amount").ifBlank {
            val d = a.optDouble("amount")
            if (d.isNaN()) "" else d.toString()
        }
        val amountFen = MoneyUtil.parseToFen(amountRaw) ?: MoneyUtil.parseChineseToFen(amountRaw)
        if (amountFen == null || amountFen <= 0) return "参数错误：amount 必须是大于 0 的金额（元）。"
        val merchant = a.optString("merchant").trim()
        val product = a.optString("product").trim()
        val type = when (a.optString("type").trim().lowercase()) {
            "income", "收入" -> Transaction.TYPE_INCOME
            else -> Transaction.TYPE_EXPENSE
        }
        // 日期：显式/口语解析；整体无依据才默认今天（日期的自然缺省，时刻不适用此规则）
        val dateRaw = a.optString("date").trim()
        val timeRaw = a.optString("time").trim()
        val spoken = com.simpleaccount.app.util.SpokenTimeParser.parse(
            listOf(dateRaw, timeRaw).filter { it.isNotBlank() }.joinToString(" ")
        )
        val date = spoken.date ?: normalizeDate(dateRaw)
            ?: Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").find(dateRaw)?.let { mm ->
                "%04d-%02d-%02d".format(mm.groupValues[1].toInt(), mm.groupValues[2].toInt(), mm.groupValues[3].toInt())
            }
            ?: java.time.LocalDate.now().toString()
        // 时刻：无依据 → 空字符串（未知），绝不默认 12:00
        val time = spoken.time ?: normalizeTimeStrict(timeRaw).orEmpty()
        val timeUnknown = time.isBlank()

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
            val kw = com.simpleaccount.app.util.KeywordRules.classify("$merchant $product")
                ?.takeIf { it in valid }
            mapped?.takeIf { it in valid }
                ?: kw
                ?: (if (type == Transaction.TYPE_INCOME) com.simpleaccount.app.util.CategoryPresets.DEFAULT_INCOME_CATEGORY
                    else com.simpleaccount.app.util.CategoryPresets.DEFAULT_EXPENSE_CATEGORY)
        }
        // 二级分类：显式指定且属于该一级 → 用；否则关键词推断；无把握留空
        val requestedSub = a.optString("sub_category").trim()
        val knownSubs = runCatching { subCategoryRepository.getByParent(category).map { it.name }.toSet() }
            .getOrDefault(emptySet())
        val subCategory = when {
            requestedSub.isNotEmpty() && (knownSubs.isEmpty() || requestedSub in knownSubs) -> requestedSub
            else -> com.simpleaccount.app.util.KeywordRules.classifySub("$merchant $product", category)
                ?.takeIf { knownSubs.isEmpty() || it in knownSubs }.orEmpty()
        }

        val id = accountRepository.insert(
            Transaction(
                amount = amountFen,
                type = type,
                category = category,
                subCategory = subCategory,
                date = date,
                time = time,
                merchant = merchant,
                product = product,
                source = Transaction.SOURCE_MANUAL,
            )
        )
        val stored = accountRepository.getById(id)
        if (stored == null) return "失败 rows_affected=0。记账后读库失败（流水号 $id）。"
        val dir = if (type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        val catLabel = if (subCategory.isNotBlank()) "$category/$subCategory" else category
        val timeLabel = if (timeUnknown) "时刻未知" else time
        return "【账本已核验】rows_affected=1 已记账（流水号 $id）：$dir ${MoneyUtil.fenToYuan(amountFen)} 元 · " +
            listOf(merchant, product, catLabel, "$date $timeLabel").filter { it.isNotBlank() }.joinToString(" · ") +
            (if (timeUnknown) "（时刻无依据未落库：请在回复里追问用户几点，可再用 edit_transaction 补上）" else "")
    }

    /** 严格时刻解析：只认 HH:mm / H:mm；其它一律 null（不猜、不默认）。 */
    private fun normalizeTimeStrict(v: String): String? {
        val t = v.trim()
        if (t.isEmpty()) return null
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(t) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return "%02d:%02d".format(h, min)
    }

    /** 撤回一笔账：有流水号按号删，否则删最新一笔（经确认闸门批准后执行）。 */
    private suspend fun withdrawTransaction(args: String): String {
        val a = parseArgs(args)
        val id = a.optLong("transaction_id", -1L)
        val t = if (id > 0) {
            accountRepository.getById(id)
        } else {
            accountRepository.getAll().maxByOrNull { it.id }
        } ?: return if (id > 0) "没有找到流水号 $id 的记录（可能已删除）。" else "账本是空的，没有可撤回的。"
        accountRepository.delete(t.id)
        if (accountRepository.getById(t.id) != null) {
            return "失败 rows_affected=0。流水号 ${t.id} 删除后仍在账本。"
        }
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "【账本已核验】rows_affected=1 已撤回流水号 ${t.id}：$dir ${MoneyUtil.fenToYuan(t.amount)} 元 · " +
            listOf(t.merchant, t.product, t.category, t.date).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private suspend fun setAutoRecord(args: String): String {
        val a = parseArgs(args)
        val enabled = when {
            a.has("enabled") -> a.optBoolean("enabled")
            else -> true
        }
        settingsRepository.setAutoRecordEnabled(enabled)
        autoRecordRuntime.setEnabled(enabled)
        appControl.navigate("无感记账")
        return if (enabled)
            "已打开自动记账，并带到「无感记账」页。还差通知使用权的话，在该页点「去授权」。"
        else "已关闭自动记账。"
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
        val now = accountRepository.getById(id)
        if (now == null || now.category != category) {
            return "失败 rows_affected=0。流水号 $id 写库后核验不是「$category」。"
        }
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "【账本已核验】rows_affected=1 流水号 $id 从「${t.category}」改为「$category」：" +
            "$dir ${MoneyUtil.fenToYuan(t.amount)} 元 · ${t.merchant.ifBlank { t.product.ifBlank { t.date } }}。"
    }

    private fun parseIds(a: JSONObject): List<Long> {
        val out = mutableListOf<Long>()
        fun eat(raw: Any?) {
            when (raw) {
                null, JSONObject.NULL -> {}
                is JSONArray -> for (i in 0 until raw.length()) eat(raw.opt(i))
                is Number -> out += raw.toLong()
                is String -> raw.split(Regex("[,，\\s]+")).mapNotNull { it.trim().toLongOrNull() }.let { out += it }
                else -> raw.toString().toLongOrNull()?.let { out += it }
            }
        }
        if (a.has("ids")) eat(a.get("ids"))
        if (a.has("transaction_ids")) eat(a.get("transaction_ids"))
        if (a.has("transaction_id")) eat(a.optLong("transaction_id"))
        return out.filter { it > 0 }.distinct()
    }

    private fun parseAmountFen(a: JSONObject): Long? {
        if (!a.has("amount") || a.isNull("amount")) return null
        val raw = a.opt("amount") ?: return null
        val s = when (raw) {
            is Number -> raw.toString()
            else -> raw.toString()
        }
        return MoneyUtil.parseToFen(s) ?: MoneyUtil.parseChineseToFen(s)
    }

    private suspend fun matchReclassify(a: JSONObject): List<Transaction> {
        val ids = parseIds(a)
        val merchant = a.optString("merchant").trim()
        val fromCat = a.optString("from_category").trim()
        val month = normalizeMonth(a.optString("month").trim()) ?: ""
        val date = normalizeDate(a.optString("date").trim()) ?: ""
        val amountFen = parseAmountFen(a)
        val all = accountRepository.getAll()
        return if (ids.isNotEmpty()) {
            val set = ids.toSet()
            all.filter { it.id in set }
        } else {
            all.filter {
                (merchant.isEmpty() || it.merchant.contains(merchant) || it.product.contains(merchant)) &&
                    (fromCat.isEmpty() || it.category == fromCat) &&
                    (month.isEmpty() || it.date.startsWith(month)) &&
                    (date.isEmpty() || it.date == date) &&
                    (amountFen == null || it.amount == amountFen)
            }
        }
    }

    private suspend fun previewReclassify(a: JSONObject): String {
        val category = a.optString("category").trim()
        val ids = parseIds(a)
        val merchant = a.optString("merchant").trim()
        val fromCat = a.optString("from_category").trim()
        if (ids.isEmpty() && merchant.isEmpty() && fromCat.isEmpty()) {
            return "缺少流水号。请先查出要改的几笔，再把 ids 传给 reclassify_transactions。"
        }
        val targets = matchReclassify(a)
        if (targets.isEmpty()) return "没有匹配到要改分类的账单。"
        val sample = targets.take(6).joinToString("；") {
            "流水号${it.id} ${it.merchant.ifBlank { it.product.ifBlank { it.category } }} ¥${MoneyUtil.fenToYuan(it.amount)}"
        }
        val extra = if (targets.size > 6) " 等 ${targets.size} 笔" else ""
        val via = if (ids.isNotEmpty()) "按流水号" else "按商家/分类筛选"
        return "将把 $via 选中的 ${targets.size} 笔改到「$category」：$sample$extra。不会改商家映射，也不会动没选中的账。"
    }

    /** 选择集级改分类：只动传入的流水，不写商家映射。 */
    private suspend fun reclassifyTransactions(args: String): String {
        val a = parseArgs(args)
        val category = a.optString("category").trim()
        val valid = categoryRepository.getAll().map { it.name }.toSet()
        if (category !in valid) return "参数错误：分类「$category」不存在，可用：${valid.joinToString("、")}。"
        val ids = parseIds(a)
        val merchant = a.optString("merchant").trim()
        val fromCat = a.optString("from_category").trim()
        if (ids.isEmpty() && merchant.isEmpty() && fromCat.isEmpty()) {
            return "失败 rows_affected=0。请提供流水号 ids，或商家/原分类。不要整本账一刀切。"
        }
        val targets = matchReclassify(a)
        if (targets.isEmpty()) return "失败 rows_affected=0。没有匹配的账单。"
        if (ids.isEmpty() && targets.size > 800) {
            return "失败 rows_affected=0。匹配 ${targets.size} 笔超过 800。请加 month/from_category/amount 收窄，或分批传 ids。"
        }
        var n = 0
        targets.forEach {
            if (it.category != category) {
                accountRepository.update(it.copy(category = category, updatedAt = System.currentTimeMillis()))
                n++
            }
        }
        val verified = targets.mapNotNull { accountRepository.getById(it.id) }
        val mismatch = verified.filter { it.category != category }
        if (mismatch.isNotEmpty()) {
            return "失败 rows_affected=$n。写库后核验失败：${mismatch.size} 笔仍不是「$category」。"
        }
        if (n == 0) {
            return "rows_affected=0。选中的 ${verified.size} 笔本来就是「$category」，账本没有新的改动。"
        }
        val sample = verified.take(8).joinToString("\n") {
            "- 流水号 ${it.id} ${it.date} ${it.merchant.ifBlank { it.product }} ¥${MoneyUtil.fenToYuan(it.amount)} 现分类=${it.category}"
        }
        return "【账本已核验】rows_affected=$n matched=${verified.size} 改到「$category」。未改商家映射。\n$sample"
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
        val id = a.optLong("transaction_id")
        if (id <= 0) return "参数错误：transaction_id 必须是流水号。"
        val t = accountRepository.getById(id)
            ?: return "失败 rows_affected=0。流水号 $id 不在账本里。"
        val newAmount = if (a.has("amount")) {
            val d = a.optDouble("amount")
            if (d.isNaN() || d <= 0) return "金额不合法（必须是大于 0 的元数）。"
            Math.round(d * 100)
        } else t.amount
        val newDate = a.optString("date").trim().ifBlank { t.date }.let { raw ->
            if (raw == t.date) raw else normalizeDate(raw) ?: raw
        }
        // 时刻：显式传参才动；传空字符串=清空时刻；不传=不动
        val newTime = if (!a.has("time")) t.time
        else {
            val raw = a.optString("time").trim()
            if (raw.isEmpty()) "" else {
                val spoken = com.simpleaccount.app.util.SpokenTimeParser.parse(raw)
                spoken.time ?: normalizeTimeStrict(raw) ?: return "参数错误：time 必须是 HH:mm（如 15:30），或 上午/下午/晚上 等时段词。"
            }
        }
        val newSub = if (a.has("sub_category")) a.optString("sub_category").trim() else t.subCategory
        val updated = t.copy(
            amount = newAmount,
            date = newDate,
            time = newTime,
            note = if (a.has("note")) a.optString("note") else t.note,
            merchant = if (a.has("merchant")) a.optString("merchant").trim() else t.merchant,
            product = if (a.has("product")) a.optString("product").trim() else t.product,
            subCategory = newSub,
            updatedAt = System.currentTimeMillis()
        )
        accountRepository.update(updated)
        val now = accountRepository.getById(id) ?: return "失败 rows_affected=0。流水号 $id 写库后读不到。"
        val dir = if (updated.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        val catLabel = if (now.subCategory.isNotBlank()) "${now.category}/${now.subCategory}" else now.category
        val timeLabel = if (now.time.isNotBlank()) " ${now.time}" else ""
        return "【账本已核验】rows_affected=1 已修改流水号 $id：$dir ${MoneyUtil.fenToYuan(now.amount)} 元 · ${now.date}$timeLabel · " +
            listOf(now.merchant, now.product, catLabel).filter { it.isNotBlank() }.joinToString(" · ") +
            (if (now.note.isNotBlank()) " · 备注：${now.note}" else "")
    }

    /** 操控：删除一笔账单 */
    private suspend fun deleteTransaction(args: String): String {
        val a = parseArgs(args)
        val id = a.optLong("transaction_id", -1L)
        if (id <= 0) return "参数错误：transaction_id 必须是有效的流水号。"
        val t = accountRepository.getById(id) ?: return "没有找到流水号 $id 的记录（可能已删除）。"
        accountRepository.delete(id)
        if (accountRepository.getById(id) != null) return "失败 rows_affected=0。流水号 $id 删除后仍在账本。"
        val dir = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        return "【账本已核验】rows_affected=1 已删除流水号 $id：$dir ${MoneyUtil.fenToYuan(t.amount)} 元 · " +
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
                iconName = com.simpleaccount.app.util.IconMapper.allChoices(type).firstOrNull { it.name != "more_horiz" }?.name ?: "category",
                colorHex = "#7A9AE3",
            )
        )
        return "已创建${if (type == com.simpleaccount.app.data.entity.Category.TYPE_INCOME) "收入" else "支出"}分类「$name」。"
    }

    /** 操控：删除自定义分类（预置分类与在用分类拒绝删除；附带删除其二级分类） */
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
        runCatching { subCategoryRepository.deleteByParent(name) }
        return "已删除分类「$name」（含其二级分类）。"
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

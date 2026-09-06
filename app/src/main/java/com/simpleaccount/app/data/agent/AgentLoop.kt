package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import com.simpleaccount.app.data.service.ToolChatMessage
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账 Agent 循环（ReAct 风格）：
 * 1. 把用户消息 + 历史 + 工具定义发给模型（SSE：终答增量 onDelta，tool_calls 等攒齐再执行）
 * 2. 若模型要求调用工具 → 执行 → 回填 → 回到 1
 * 3. 破坏性工具先停，等用户点确认
 * 4. 直到模型输出最终文本回复
 */
@Singleton
class AgentLoop @Inject constructor(
    private val aiService: AiService,
    private val agentTools: AgentTools,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val memoryStore: com.simpleaccount.app.data.memory.MemoryStore,
    private val ledgerRepository: com.simpleaccount.app.data.repository.LedgerRepository,
) {

    data class PendingConfirm(
        val tool: String,
        val args: String,
        val summary: String,
    )

    data class AgentResult(
        val reply: String,
        val toolRounds: Int,
        val error: String? = null,
        val pending: PendingConfirm? = null,
    )

    companion object {
        /** 工具名 → 过程提示文案（驱动 UI 的"正在查询账单…"等状态） */
        fun toolPhaseLabel(toolName: String): String = when (toolName) {
            "query_transactions" -> "正在查询账单明细…"
            "get_summary" -> "正在统计收支…"
            "get_category_totals" -> "正在按分类统计…"
            "get_merchant_totals" -> "正在统计商家消费…"
            "get_daily_totals" -> "正在按天汇总…"
            "list_months" -> "正在核对账本月份…"
            "list_merchants" -> "正在查看商家归类…"
            "classify_merchants" -> "正在归类商家…"
            "reclassify_transactions", "update_transaction_category" -> "正在改这些账单的分类…"
            "get_insights" -> "正在生成本月体检…"
            "memory_get" -> "正在检索长期记忆…"
            "memory_write" -> "正在写入记忆…"
            "add_transaction" -> "正在记账…"
            "withdraw_transaction" -> "正在撤回…"
            "delete_transaction" -> "正在删除…"
            "set_auto_record" -> "正在开关无感记账…"
            else -> "正在办理…"
        }

        val WRITE = setOf(
            "add_transaction", "withdraw_transaction", "delete_transaction",
            "edit_transaction", "update_transaction_category", "set_monthly_budget",
            "set_auto_record", "set_theme", "set_merchant_category", "create_category",
            "reclassify_transactions", "navigate",
        )

        /** 网络类错误（可重试）；HTTP 4xx（key/参数问题）不重试 */
        private fun isRetryable(error: String): Boolean =
            error.contains("HTTP 5") || error.contains("网络") || error.contains("timeout", true) ||
                error.contains("connect", true) || error.contains("Unable to resolve", true)
    }

    /**
     * 构建系统提示词。注入今天日期、账本覆盖月份、本月/上月快照。
     * 快照让弱模型就算不会调工具，也不会把「本月花了多少」答成胡编。
     */
    private fun buildSystemPrompt(coveredMonths: List<String>, snapshot: String, ledgerName: String = "主账本"): String {
        val today = java.time.LocalDate.now()
        val prevMonth = java.time.YearMonth.now().minusMonths(1)
        val monthsDesc = if (coveredMonths.isEmpty()) "（账本暂无数据）"
        else "${coveredMonths.first()} 至 ${coveredMonths.last()}，共 ${coveredMonths.size} 个月"
        val dates = com.simpleaccount.app.util.DateResolver.anchorBlock()
        return """
你是一个专业、贴心的智能会计管家（Agent），运行在用户的记账 App 里。
当前账本：「$ledgerName」。所有工具只返回这一本的流水，禁止把别的账本当成数据。
$dates
账本数据覆盖：$monthsDesc。金额单位是元。
用户说「记住这个（全局）」时才调用 memory_write(scope=global)；发现偏好/事实/踩坑写今日日志。禁止写入密码/API Key/token。
新消息改变范围、数字、目标时，以最新消息为准，不要沿用旧任务。
用户说「昨天/前天/今天/本月/上个月」时，必须用上面的时间锚点换成 yyyy-MM-dd 或 yyyy-MM 再调工具，绝对不要回答「日期未知」。

$snapshot

你可以调用工具获取/修改真实数据：
- 本月体检 / 花哪了 → get_insights（本地算环比、异常日，优先用；不要谈「固定支出」口径）
- 核对账本覆盖哪些月份 → list_months（查询结果为空、或不确定某月有没有数据时，先调它再下结论）
- 查具体交易明细 / 某类花销 / 某商家消费 → query_transactions（支持 month/type/category/keyword）
- 算某段时间收支总额 → get_summary
- 按分类统计 → get_category_totals（支持月份区间）
- 哪些商家花钱最多 → get_merchant_totals
- 某月每天花多少 / 哪天花得最多 → get_daily_totals
- 用户说「我买了 X 花了 Y，帮我记上」→ 用 add_transaction 记账（从话里提取金额/商家/商品），记完告知流水号
- 用户说「撤回一笔账单/撤回/撤销」→ 立刻调用 withdraw_transaction（可不带流水号，默认删最新一笔），禁止再问、禁止说无法执行
- 用户说打开无感/自动记账 → 立刻 set_auto_record(enabled=true) 再 navigate 到无感记账页
- 查看或排查商家归类 → list_merchants
- 某商家下选定若干笔改分类 → 先 query_transactions 拿流水号，再 reclassify_transactions(ids, category)。禁止用 classify_merchants（那会改该商家全部历史和映射）
- 给商家批量归类（整商户一刀切，需确认）→ classify_merchants
- 跳转到任意页面（"打开统计""带我去导入"）→ navigate
- 编辑账单字段（金额/日期/备注/商家）→ edit_transaction；删除账单 → delete_transaction
- 新建/删除分类 → create_category / delete_category；设置商家固定映射 → set_merchant_category
- 设置每月预算 → set_monthly_budget；切换深浅色模式 → set_theme

工作规则：
1. 凡是涉及数字、金额、明细、统计的问题，必须先调用工具拿到真实结果再回答，绝不凭空编造金额或记录。上面【账本快照】里的数字可以直接引用。
2. 解析相对时间必须换成具体日期再传参：今天=$today，昨天=${today.minusDays(1)}，前天=${today.minusDays(2)}，上个月=$prevMonth。query_transactions 的 date 传 yyyy-MM-dd，month 传 yyyy-MM。工具层也会再解析一次「昨天」这类词，但你自己先换算更稳。禁止输出「日期未知」。
3. 任何工具返回"没有数据/没有找到"时，不要直接告诉用户没数据——先调 list_months 核对账本实际覆盖的月份，确认参数月份是否算错；若该月确实无数据，明确说出账本覆盖范围并给出最近有数据月份的参考数字。
4. 工具结果标注"仅为部分数据"时，回答必须声明这一点；要给占比/排行结论时优先用 get_insights / get_category_totals / get_merchant_totals（它们是全量汇总），不要用明细列表凑。
5. 一次工具结果不够就继续调用其它工具，多步综合分析后再回答；查询类问题通常 1-3 次工具调用足够。
6. 需要多份数据时（如既要看汇总又要看分类），尽量在一条回复里同时发起多个工具调用（并行查询），减少用户等待。
7. 回答用简体中文，语气友好自然；金额用「元」，保留两位小数。关键数字后注明数据依据（如"据9月账单"）。回答时给出有价值的观察或建议（占比、环比、异常消费），但不啰嗦。
8. 排版用 Markdown 结构化输出，重点一目了然：小节用 "### 标题"，关键数字/结论用 **加粗**，并列项用 "- " 列表，多组数据对比用 Markdown 表格（列数不超过 4 列，行数不超过 8 行）。不要用 emoji 堆砌，最多一两个。
8. 用户明确说要记账（"帮我记上""记一笔""买了X花了Y"）时：**先调用 add_transaction 把账记上**，再确认结果；严禁只说"好的我可以记"而不真正调用工具，严禁先说"账本里没有这笔所以记不了"——没查到的该记就记。
9. 用户要求改某一笔的分类 → update_transaction_category；改某商家下选定的若干笔 → reclassify_transactions（必须带 ids 或 merchant+from_category，禁止整商户一刀切）；要改某商家所有账和映射 → 才用 classify_merchants。不要混用。
10. 归类必须用工具完成，不要自己口头分类。
11. 用户闲聊或问与记账无关的问题时，礼貌回应并把话题引导回记账理财。
12. 撤回、记账、开关无感：工具一跑完就用工具结果当最终答复，禁止再说「无法执行」「需要确认」「正在思考」。回答写成连贯段落，禁止一字一行。
""".trimIndent()
    }

    /** 按意图裁工具：无关 query 不挂账本工具，避免误打库。 */
    private fun pickTools(userMessage: String, intent: QueryIntent): List<AgentToolSpec> {
        if (intent == QueryIntent.CHAT) return emptyList()
        val all = agentTools.specs
        val s = userMessage
        if (s.length > 120) return all
        val names = mutableSetOf<String>()
        if (intent == QueryIntent.NAV) {
            names += "navigate"
            return all.filter { it.name in names }
        }
        if (intent == QueryIntent.MEMORY) {
            names += setOf("memory_get", "memory_write")
            return all.filter { it.name in names }
        }
        if (intent == QueryIntent.LEDGER_WRITE) {
            names += setOf(
                "add_transaction", "withdraw_transaction", "delete_transaction",
                "edit_transaction", "update_transaction_category", "reclassify_transactions",
                "query_transactions", "list_merchants", "set_merchant_category",
            )
        }
        if (intent == QueryIntent.LEDGER_READ) {
            if (Regex("明细|哪几笔|流水|账单列表|查一下").containsMatchIn(s)) names += "query_transactions"
            if (Regex("多少|汇总|一共|总共|花了|结余").containsMatchIn(s)) {
                names += setOf("get_summary", "get_category_totals")
            }
            if (Regex("商家|哪家").containsMatchIn(s)) names += "get_merchant_totals"
            if (Regex("哪天|每天|昨天|今天|前天").containsMatchIn(s)) {
                names += setOf("get_daily_totals", "query_transactions")
            }
            if (Regex("体检|花哪|月报|环比|超支|预算").containsMatchIn(s)) names += "get_insights"
            if (names.isEmpty()) names += setOf("get_summary", "get_insights", "get_category_totals")
            names += "list_months"
        }
        if (Regex("记(?:一笔|上|账)|帮我记|入账").containsMatchIn(s)) names += "add_transaction"
        if (Regex("删|撤回|撤销").containsMatchIn(s)) {
            names += "withdraw_transaction"
            names += "delete_transaction"
        }
        if (Regex("改金额|编辑|备注").containsMatchIn(s)) names += "edit_transaction"
        if (Regex("改成|重分类|归类|这几笔|这几条|分类|映射").containsMatchIn(s)) {
            names += setOf(
                "query_transactions", "reclassify_transactions", "update_transaction_category",
                "list_merchants", "set_merchant_category",
            )
            if (Regex("以后|全部|所有|映射").containsMatchIn(s)) {
                names += setOf("classify_merchants", "create_category", "delete_category")
            }
        }
        if (s.contains("预算")) names += "set_monthly_budget"
        if (Regex("主题|深色|浅色|暗色|夜间").containsMatchIn(s)) names += "set_theme"
        if (Regex("打开|跳转|带我去").containsMatchIn(s)) names += "navigate"
        if (Regex("无感|自动记账").containsMatchIn(s)) {
            names += "set_auto_record"
            names += "navigate"
        }
        if (Regex("记住").containsMatchIn(s)) names += "memory_write"
        return all.filter { it.name in names }.ifEmpty {
            all.filter { it.name in setOf("get_summary", "get_insights", "list_months") }
        }
    }

    private fun buildSnapshot(all: List<com.simpleaccount.app.data.entity.Transaction>): String {
        if (all.isEmpty()) return "【账本快照】账本还是空的，用户需要先记一笔或导入账单。"
        val health = InsightsEngine.compute(all, settingsRepository.monthlyBudget(), DateUtil.thisMonth())
        val sb = StringBuilder()
        sb.appendLine("【账本快照，可直接引用这些数字；问本月花了多少不必再调工具】")
        sb.appendLine("本月支出 ¥${MoneyUtil.fenToYuan(health.expense)}，收入 ¥${MoneyUtil.fenToYuan(health.income)}，共覆盖 ${all.size} 笔")
        health.momPct?.let {
            val dir = if (it >= 0) "多花" else "少花"
            sb.appendLine("较上月（¥${MoneyUtil.fenToYuan(health.lastExpense)}）$dir ${"%.1f".format(kotlin.math.abs(it))}%")
        }
        if (health.topCategories.isNotEmpty()) {
            sb.appendLine(
                "本月前三类：" + health.topCategories.take(3)
                    .joinToString("、") { "${it.first} ¥${MoneyUtil.fenToYuan(it.second)}" }
            )
        }
        if (health.subscriptions.isNotEmpty()) {
            sb.appendLine(
                "疑似订阅：" + health.subscriptions.take(3)
                    .joinToString("、") { "${it.merchant} ¥${MoneyUtil.fenToYuan(it.typicalFen)}/月" }
            )
        }
        if (health.projectedFen > 0) {
            sb.appendLine("按当前速度月底预计支出 ¥${MoneyUtil.fenToYuan(health.projectedFen)}；今天已花 ¥${MoneyUtil.fenToYuan(health.todayFen)}")
        }
        if (health.weekdayAvgFen > 0 || health.weekendAvgFen > 0) {
            sb.appendLine("工作日日均 ¥${MoneyUtil.fenToYuan(health.weekdayAvgFen)}，周末日均 ¥${MoneyUtil.fenToYuan(health.weekendAvgFen)}")
        }
        if (health.nightFen > 0) sb.appendLine("夜间（22:00–05:00）已花 ¥${MoneyUtil.fenToYuan(health.nightFen)}")
        if (health.todayDupes.isNotEmpty()) sb.appendLine("今天可能重复记账：" + health.todayDupes.joinToString("、"))
        return sb.toString().trim()
    }

    /**
     * 跑一次完整的 Agent 对话回合。
     * @param history 之前若干轮的 (role, content)；system 由本方法注入第一位置。
     * @param onStatus 过程状态回调（如"正在查询账本…"），供 UI 展示 Agent 进度。
     * @param onDelta 终答文本增量（无 tool_calls 时才会回调）。
     */
    suspend fun run(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        maxRounds: Int = 2,
        onStatus: (String) -> Unit = {},
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
    ): AgentResult {
        val enabled = settingsRepository.isAiEnabled()
        if (!enabled) return AgentResult("", 0, "AI 功能未开启")
        val apiKey = settingsRepository.apiKey()
        if (apiKey.isBlank()) return AgentResult("", 0, "未配置 API Key")
        val baseUrl = settingsRepository.baseUrl()
        val model = settingsRepository.model()
        val intent = IntentGate.classify(userMessage)
        val writeFast = intent == QueryIntent.LEDGER_WRITE ||
            Regex("删|撤回|帮我记|记一笔|记上|撤销|无感|自动记账|改成|改到").containsMatchIn(userMessage)
        val thinkingLevel = if (writeFast) SettingsRepository.THINKING_OFF else settingsRepository.thinkingLevel()
        val tools = pickTools(userMessage, intent)
        val cap = when (intent) {
            QueryIntent.CHAT, QueryIntent.NAV -> 1
            else -> maxRounds
        }

        val messages = mutableListOf<ToolChatMessage>()
        if (IntentGate.needsLedger(intent)) {
            val allTx = accountRepository.getAll()
            val coveredMonths = allTx.map { it.date.take(7) }.distinct().sorted()
            val snapshot = buildSnapshot(allTx)
            val memory = runCatching { memoryStore.injectForNewSession() }.getOrDefault("")
            runCatching { memoryStore.maybeCaptureFromUser(userMessage) }
            val ledgerName = runCatching { ledgerRepository.getCurrent()?.name }.getOrNull() ?: "主账本"
            messages.add(ToolChatMessage("system", buildSystemPrompt(coveredMonths, snapshot, ledgerName) + "\n\n" + memory))
        } else {
            messages.add(
                ToolChatMessage(
                    "system",
                    "你是记账 App 里的管家。用户这句和账本无关，直接简短回答，不要调用工具、不要编造账单数字。回答写成连贯段落，禁止一字一行。",
                )
            )
        }
        history.takeLast(12).forEach { (r, c) -> messages.add(ToolChatMessage(r, c)) }
        messages.add(ToolChatMessage("user", com.simpleaccount.app.util.DateResolver.enrichUserMessage(userMessage)))

        var rounds = 0
        var networkRetried = false
        val loopDetector = ToolLoopDetector()
        onStatus(
            when {
                writeFast -> "正在办理…"
                intent == QueryIntent.CHAT -> "正在回复…"
                else -> "正在查账…"
            }
        )
        while (rounds < cap) {
            rounds++
            val resp = aiService.chatWithTools(
                baseUrl, apiKey, model, messages, tools,
                onDelta = onDelta,
                thinkingLevel = thinkingLevel,
                onReasoning = onReasoning,
            )
            if (resp.error != null) {
                if (resp.error == "已停止") return AgentResult("", rounds, "已停止")
                if (!networkRetried && isRetryable(resp.error)) {
                    networkRetried = true
                    rounds--
                    onStatus("网络波动，正在重试…")
                    kotlinx.coroutines.delay(800)
                    continue
                }
                return AgentResult("", rounds, resp.error)
            }

            if (resp.toolCalls.isEmpty()) {
                return AgentResult(resp.content, rounds)
            }

            onStatus("正在办理（第 $rounds 步）…")

            messages.add(
                ToolChatMessage(role = "assistant", content = resp.content, toolCalls = resp.toolCalls)
            )
            var anyExecuted = false
            val writeReplies = mutableListOf<String>()
            for (tc in resp.toolCalls) {
                onStatus(toolPhaseLabel(tc.name))
                val check = loopDetector.check(tc.name, tc.arguments)
                val result = if (check.level == ToolLoopDetector.Level.CRITICAL) {
                    check.message ?: "拦截：检测到重复调用循环，已阻止本轮执行，请直接基于已有信息回答。"
                } else {
                    val executed = agentTools.execute(tc)
                    if (executed.content.startsWith(AgentTools.NEED_CONFIRM_PREFIX)) {
                        val summary = executed.content.removePrefix(AgentTools.NEED_CONFIRM_PREFIX)
                        return AgentResult(
                            reply = "这项操作会改账本，点「确认」后才执行：\n\n$summary",
                            toolRounds = rounds,
                            pending = PendingConfirm(tc.name, tc.arguments, summary),
                        )
                    }
                    val warn = loopDetector.record(tc.name, tc.arguments, executed.content)
                    listOf(executed.content, check.message, warn.message)
                        .filter { !it.isNullOrBlank() }
                        .joinToString("\n")
                }
                messages.add(
                    ToolChatMessage(role = "tool", content = result, toolCallId = tc.id)
                )
                if (tc.name in WRITE) writeReplies += result
                anyExecuted = true
            }
            if (!anyExecuted) {
                return AgentResult("(工具调用异常，请重试)", rounds)
            }
            if (writeReplies.isNotEmpty()) {
                return AgentResult(writeReplies.joinToString("\n\n"), rounds)
            }
        }
        messages.add(
            ToolChatMessage(
                role = "user",
                content = "请立即基于上面工具已经查到的数据，直接给出最终回答。不要再调用任何工具。"
            )
        )
        val finalResp = aiService.chatWithTools(
            baseUrl, apiKey, model, messages, emptyList(),
            onDelta = onDelta,
            thinkingLevel = thinkingLevel,
            onReasoning = onReasoning,
        )
        return if (finalResp.error != null || finalResp.content.isBlank()) {
            AgentResult("（分析了 ${rounds} 轮仍不完整，请把问题拆小一点再问）", rounds)
        } else {
            AgentResult(finalResp.content, rounds)
        }
    }
}

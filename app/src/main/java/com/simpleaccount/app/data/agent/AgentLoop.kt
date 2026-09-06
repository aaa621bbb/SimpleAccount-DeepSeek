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
 * 记账 Agent 架构级循环（ReAct 风格）—— v2.30 架构重构版。
 *
 * 管线职责：
 * - 撮合层：IntentGate → pickTools 收敛（无关 query 零 DB 往返、零工具挂载）
 * - 调度层：按意图选 cap/思考档位，失败可重试，过思考由管线收敛而非一刀切关闭
 * - 落库层：所有写操作必须落库并回传 rows_affected，可核验；自洽校验拦截 fake-completion
 * - 多模态：时间语义经 DateResolver 统一（上午/下午/今天/昨天/前天）端到端修复
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

        /** 区分真正的写库工具（涉及 transactions 表 rows_affected） */
        val LEDGER_WRITE_TOOLS = setOf(
            "add_transaction", "withdraw_transaction", "delete_transaction",
            "edit_transaction", "update_transaction_category", "reclassify_transactions",
        )

        /**  hallucinated success 关键词：必须以 rows_affected>0 为前提，否则重写为失败 */
        private val HALLUCINATED_SUCCESS = Regex("已完成|已改好|已记账|改好了|记好了|已处理.*\\d+.*笔|成功.*\\d+.*笔|全部改完|已归类")

        /** 网络类错误（可重试）；HTTP 4xx（key/参数问题）不重试 */
        private fun isRetryable(error: String): Boolean =
            error.contains("HTTP 5") || error.contains("网络") || error.contains("timeout", true) ||
                error.contains("connect", true) || error.contains("Unable to resolve", true)
    }

    /**
     * 构建系统提示词。注入今天日期、账本覆盖月份、本月/上月快照。
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
用户说「昨天/前天/今天/本月/上个月/上午/下午」时，必须用上面的时间锚点换成 yyyy-MM-dd 或 yyyy-MM 再调工具，绝对不要回答「日期未知」。上午=09:00 下午=15:00 晚上=20:00 凌晨=02:00，中午=12:00；今天/昨天/前天无钟点填 12:00。

$snapshot

你可以调用工具获取/修改真实数据：
- 本月体检 / 花哪了 → get_insights（本地算环比、异常日，优先用；不要谈「固定支出」口径）
- 核对账本覆盖哪些月份 → list_months（查询结果为空、或不确定某月有没有数据时，先调它再下结论）
- 查具体交易明细 / 某类花销 / 某商家消费 → query_transactions（支持 month/type/category/keyword，默认 100、最大 800，超量用 offset 翻页；批量改 500 笔可一次查出）
- 算某段时间收支总额 → get_summary
- 按分类统计 → get_category_totals（支持月份区间）
- 哪些商家花钱最多 → get_merchant_totals
- 某月每天花多少 / 哪天花得最多 → get_daily_totals
- 用户说「我买了 X 花了 Y，帮我记上」→ 用 add_transaction 记账（从话里提取金额/商家/商品），记完告知流水号与【账本已核验】rows_affected
- 用户说「撤回一笔账单/撤回/撤销」→ 立刻调用 withdraw_transaction（可不带流水号，默认删最新一笔），禁止再问、禁止说无法执行
- 用户说打开无感/自动记账 → 立刻 set_auto_record(enabled=true) 再 navigate 到无感记账页
- 查看或排查商家归类 → list_merchants
- 某商家下选定若干笔改分类 → 直接 reclassify_transactions(merchant/from_category/ids/amount, category)。query 默认 800 上限，500 笔批量可一次改；禁止用 classify_merchants（那会改该商家全部历史和映射）
- 给商家批量归类（整商户一刀切，需确认）→ classify_merchants
- 跳转到任意页面（"打开统计""带我去导入"）→ navigate
- 编辑账单字段（金额/日期/备注/商家）→ edit_transaction；删除账单 → delete_transaction
- 新建/删除分类 → create_category / delete_category；设置商家固定映射 → set_merchant_category
- 设置每月预算 → set_monthly_budget；切换深浅色模式 → set_theme

工作规则：
1. 凡是涉及数字、金额、明细、统计的问题，必须先调用工具拿到真实结果再回答，绝不凭空编造金额或记录。上面【账本快照】里的数字可以直接引用。
2. 解析相对时间必须换成具体日期再传参：今天=$today，昨天=${today.minusDays(1)}，前天=${today.minusDays(2)}，上个月=$prevMonth。query_transactions 的 date 传 yyyy-MM-dd，month 传 yyyy-MM。工具层也会再解析一次「昨天/上午」等，但你自己先换算更稳。禁止输出「日期未知」。
3. 任何工具返回"没有数据/没有找到"时，不要直接告诉用户没数据——先调 list_months 核对账本实际覆盖的月份，确认参数月份是否算错；若该月确实无数据，明确说出账本覆盖范围并给出最近有数据月份的参考数字。
4. 工具结果标注"仅为部分数据"时，回答必须声明这一点；要给占比/排行结论时优先用 get_insights / get_category_totals / get_merchant_totals（它们是全量汇总），不要用明细列表凑。
5. 一次工具结果不够就继续调用其它工具，多步综合分析后再回答；查询类问题通常 1-3 次工具调用足够。
6. 需要多份数据时（如既要看汇总又要看分类），尽量在一条回复里同时发起多个工具调用（并行查询），减少用户等待。
7. 回答用简体中文，语气友好自然；金额用「元」，保留两位小数。关键数字后注明数据依据（如"据9月账单"）。回答时给出有价值的观察或建议（占比、环比、异常消费），但不啰嗦。
8. 排版用 Markdown 结构化输出，重点一目了然：小节用 "### 标题"，关键数字/结论用 **加粗**，并列项用 "- " 列表，多组数据对比用 Markdown 表格（列数不超过 4 列，行数不超过 8 行）。不要用 emoji 堆砌，最多一两个。
8. 用户明确说要记账（"帮我记上""记一笔""买了X花了Y"）时：**先调用 add_transaction 把账记上**，再确认结果；严禁只说"好的我可以记"而不真正调用工具，严禁先说"账本里没有这笔所以记不了"——没查到的该记就记。
9. 用户要求改某一笔的分类 → update_transaction_category；改某商家下选定的若干笔（含「五十元那笔、其余」）→ 先 query_transactions 拿流水号或直接 reclassify_transactions(merchant, amount, category)。禁止整商户一刀切用 classify_merchants，除非用户明确说「全部/以后」。
10. 归类必须用工具完成并等工具返回「账本已核验」且 rows_affected>0。禁止口头说「已完成/已改好」——没调用写工具就是账本没改。rows_affected=0 必须告诉用户失败，禁止编造成功。框架层会对任何「已完成」做自洽校验：无 rows_affected>0 的成功声明会被重写为失败。
11. 用户闲聊或问与记账无关的问题时，礼貌回应并把话题引导回记账理财，绝不调用账本工具。
12. 撤回、记账、改分类、开关无感：工具一跑完就用工具结果当最终答复，禁止再说「无法执行」「需要确认」「正在思考」。回答写成连贯段落，禁止一字一行。
13. 推理内容必须依据工具结果。禁止在思考里承认「刚才的话是编的」还继续对用户撒谎。
14. 批量改 500 笔后必须可核验：reclassify 返回 rows_affected 与抽查明细，框架会抽查回读验证是否真的落库。
""".trimIndent()
    }

    /**
     * 按意图裁工具：无关 query 不挂账本工具，避免误打库。
     * CHAT/NAV/MEMORY 各自收敛；LEDGER_* 按关键词精筛，绝不因 s.length>120 就全量挂载。
     */
    private fun pickTools(userMessage: String, intent: QueryIntent): List<AgentToolSpec> {
        if (intent == QueryIntent.CHAT) return emptyList()
        val all = agentTools.specs
        val s = userMessage
        if (intent == QueryIntent.LEDGER_WRITE) {
            return all.filter {
                it.name in setOf(
                    "add_transaction", "withdraw_transaction", "delete_transaction",
                    "edit_transaction", "update_transaction_category", "reclassify_transactions",
                    "query_transactions", "list_merchants", "set_merchant_category",
                )
            }
        }
        // 长消息不直接回全量，仍按意图收敛以免 token 爆炸与空转 DB
        if (intent == QueryIntent.NAV) {
            return all.filter { it.name == "navigate" }
        }
        if (intent == QueryIntent.MEMORY) {
            return all.filter { it.name in setOf("memory_get", "memory_write") }
        }
        if (intent == QueryIntent.LEDGER_READ) {
            val names = mutableSetOf<String>()
            if (Regex("明细|哪几笔|流水|账单列表|查一下|交易记录").containsMatchIn(s)) names += "query_transactions"
            if (Regex("多少|汇总|一共|总共|花了|结余|支出|收入|多少钱").containsMatchIn(s)) {
                names += setOf("get_summary", "get_category_totals")
            }
            if (Regex("商家|哪家|商户").containsMatchIn(s)) names += "get_merchant_totals"
            if (Regex("哪天|每天|按天|昨天|今天|前天|上个月|本月").containsMatchIn(s)) {
                names += setOf("get_daily_totals", "query_transactions")
            }
            if (Regex("体检|花哪|月报|环比|超支|预算|分析").containsMatchIn(s)) names += "get_insights"
            if (names.isEmpty()) names += setOf("get_summary", "get_insights", "get_category_totals")
            // list_months 仅在可能需要核对月份时携带，不默认全量空转；其余情况按需调用
            if (Regex("月|月份|有没有数据|覆盖").containsMatchIn(s) || names.contains("get_summary")) {
                names += "list_months"
            }
            return all.filter { it.name in names }
        }
        // 回落兜底：LEDGER_READ 的最小可用集，不含写工具
        return all.filter { it.name in setOf("get_summary", "get_insights", "list_months") }
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
            Regex("删|撤回|帮我记|记一笔|记上|撤销|无感|自动记账|改成|改到|归类|归入|改分类|批量改").containsMatchIn(userMessage)
        // 管线策略收敛过度思考：保留用户档位，写操作上限 LOW 而非一刀切 OFF
        val userLevel = settingsRepository.thinkingLevel()
        val thinkingLevel = if (writeFast) {
            when (userLevel) {
                SettingsRepository.THINKING_OFF -> SettingsRepository.THINKING_OFF
                SettingsRepository.THINKING_HIGH -> SettingsRepository.THINKING_LOW
                SettingsRepository.THINKING_MEDIUM -> SettingsRepository.THINKING_LOW
                else -> SettingsRepository.THINKING_LOW
            }
        } else userLevel
        val tools = pickTools(userMessage, intent)
        val cap = when (intent) {
            QueryIntent.CHAT, QueryIntent.NAV -> 1
            QueryIntent.LEDGER_WRITE -> maxOf(maxRounds, 4)
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
        var forcedWrite = false
        val loopDetector = ToolLoopDetector()
        // 聚合本回合所有写工具的客观落库结果，用于自洽校验
        val aggregatedWriteBodies = mutableListOf<String>()
        var aggregatedAffected = 0
        var anyLedgerWriteExecuted = false

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
                if (intent == QueryIntent.LEDGER_WRITE && !forcedWrite && rounds < cap) {
                    // 仅当尚未有任何写工具客观落库时，才强制要求调用写工具
                    if (!anyLedgerWriteExecuted || aggregatedAffected == 0) {
                        forcedWrite = true
                        messages.add(ToolChatMessage(role = "assistant", content = resp.content))
                        messages.add(
                            ToolChatMessage(
                                role = "user",
                                content = "你还没有调用写账工具，账本没有任何改动（rows_affected=0）。立刻调用 reclassify_transactions / update_transaction_category / add_transaction / withdraw_transaction。做不到只回复「无法执行」。禁止说已完成。",
                            )
                        )
                        continue
                    }
                }
                // 自洽校验：模型在未落库时宣称已完成 → 重写为失败
                val finalContent = sanitizeWriteReply(intent, resp.content, aggregatedAffected, anyLedgerWriteExecuted)
                return AgentResult(finalContent, rounds)
            }

            onStatus("正在办理（第 $rounds 步）…")

            messages.add(
                ToolChatMessage(role = "assistant", content = resp.content, toolCalls = resp.toolCalls)
            )
            var anyExecuted = false
            val writeRepliesThisRound = mutableListOf<String>()
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
                if (tc.name in LEDGER_WRITE_TOOLS) {
                    anyLedgerWriteExecuted = true
                    writeRepliesThisRound += result
                    aggregatedWriteBodies += result
                    // 累计 rows_affected 用于最终自洽校验
                    aggregatedAffected += Regex("rows_affected=(\\d+)").findAll(result)
                        .mapNotNull { it.groupValues[1].toIntOrNull() }.sum()
                } else if (tc.name in WRITE) {
                    // 非账本写（set_theme 等）也视为办理，但不计入 rows_affected 校验
                    writeRepliesThisRound += result
                }
                anyExecuted = true
            }
            if (!anyExecuted) {
                return AgentResult("(工具调用异常，请重试)", rounds)
            }
            if (writeRepliesThisRound.isNotEmpty()) {
                val body = writeRepliesThisRound.joinToString("\n\n")
                val affectedThisRound = Regex("rows_affected=(\\d+)").findAll(body)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }.sum()
                // 写工具已执行则直接以工具回执为准，不再让模型二次编造“已完成”
                // 若 rows_affected==0 且意图为写，需明确告知失败，禁止幻觉成功
                if (intent == QueryIntent.LEDGER_WRITE && anyLedgerWriteExecuted && aggregatedAffected == 0 && body.contains("rows_affected=")) {
                    // 保持原样返回，调用方将看到 rows_affected=0 的失败回执
                    return AgentResult(sanitizeWriteReply(intent, body, aggregatedAffected, anyLedgerWriteExecuted), rounds)
                }
                // 成功落库直接返回核验体；避免模型在下一轮把数字改写
                if (anyLedgerWriteExecuted && aggregatedAffected > 0) {
                    return AgentResult(body, rounds)
                }
                // 非账本写（如 navigate）直接返回
                if (writeRepliesThisRound.isNotEmpty() && !anyLedgerWriteExecuted) {
                    return AgentResult(body, rounds)
                }
                // 兜底：仍需自洽校验再返回
                return AgentResult(sanitizeWriteReply(intent, body, aggregatedAffected, anyLedgerWriteExecuted), rounds)
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
            AgentResult(sanitizeWriteReply(intent, finalResp.content, aggregatedAffected, anyLedgerWriteExecuted), rounds)
        }
    }

    /**
     * 自洽校验：任何“已完成/已改好/已记账”类宣称，必须以客观 rows_affected>0 为前提，
     * 否则重写为失败，防止 fake-completion 幻觉落库。
     */
    private fun sanitizeWriteReply(intent: QueryIntent, content: String, affected: Int, wrote: Boolean): String {
        if (intent != QueryIntent.LEDGER_WRITE) return content
        val hasSuccessClaim = HALLUCINATED_SUCCESS.containsMatchIn(content)
        val hasFailedMarker = content.contains("失败 rows_affected=0") || content.contains("rows_affected=0") || content.contains("账本没有改动")
        val hasVerifiedSuccess = content.contains("【账本已核验】") && Regex("rows_affected=[1-9]").containsMatchIn(content)
        // 已有核验成功体则信任
        if (hasVerifiedSuccess) return content
        // 模型宣称成功但客观未落库 → 强制重写
        if (hasSuccessClaim && !wrote) {
            return "账本没有改动。我没有调用写账工具，不能说已经改好（rows_affected=0）。\n\n请直接说流水号（例如「把流水号 12 改成居住」），或说「把【商家】的五十元改成居住，其余改成餐饮」。\n\n—— 框架自洽校验拦截了本次“已完成”幻觉。"
        }
        if (hasSuccessClaim && wrote && affected == 0) {
            return "失败 rows_affected=0。账本没有改动，刚才的“已完成”不可信。\n\n" + content + "\n\n—— 框架自洽校验：客观落库 rows_affected=0，宣称成功已拦截。"
        }
        // 没有写工具调用且内容空或含幻觉，统一兜底
        if (!wrote && content.isBlank()) {
            return "账本没有改动。我没有调用写账工具，不能说已经改好。\n\n请直接说流水号（例如「把流水号 12 改成居住」），或说「把【商家】的五十元改成居住，其余改成餐饮」。"
        }
        if (!wrote && hasSuccessClaim && !hasFailedMarker) {
            return "账本没有改动。我没有调用写账工具，不能说已经改好。\n\n请直接说流水号，或说「把【商家】的五十元改成居住，其余改成餐饮」。"
        }
        return content
    }

    /** 兼容旧调用点的 groundWriteReply，内部走 sanitizeWriteReply */
    private fun groundWriteReply(intent: QueryIntent, content: String, wrote: Boolean): String {
        return sanitizeWriteReply(intent, content, if (wrote) 1 else 0, wrote)
    }
}

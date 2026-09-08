package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.ToolChatMessage
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账 Agent 架构级循环（ReAct 风格）—— v2.31.0。
 *
 * 管线职责：
 * - 撮合层：IntentGate → pickTools 收敛（无关 query 可不挂账本工具；模型仍可自主决策）
 * - 上下文管理层：[AgentContextBuilder] 检索式注入 + 摘要压缩 + 滑动截断
 * - 模型后端层：[ModelBackendProvider] 可插拔（云端大模型 / 端侧小模型），
 *   提示词按后端能力分 full/compact 两档（[AgentPromptBuilder]）
 * - 落库层：写操作按用户「执行授权」设置——每次确认 或 授权后自动执行；
 *   回执以 rows_affected 为唯一依据，自洽校验拦截 fake-completion
 * - 可观测层：每次工具调用生成 [ToolExecutionRecord]，杜绝虚假完成与谎称无权
 * - 时间/金额语义：用户未明示不得臆测填充，必须先追问
 */
@Singleton
class AgentLoop @Inject constructor(
    private val backends: ModelBackendProvider,
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
     * 按意图裁工具：收敛 token，但 **不再对 CHAT 一刀切 emptyList**——
     * 是否真正调用交由模型基于意图自主裁决（v2.31.0 可执行性优先）。
     * CHAT 挂最小工具集（navigate + 只读摘要），模型可选择不用。
     */
    private fun pickTools(userMessage: String, intent: QueryIntent): List<AgentToolSpec> {
        val all = agentTools.specs
        val s = userMessage
        if (intent == QueryIntent.LEDGER_WRITE) {
            return all.filter {
                it.name in setOf(
                    "add_transaction", "withdraw_transaction", "delete_transaction",
                    "edit_transaction", "update_transaction_category", "reclassify_transactions",
                    "query_transactions", "list_merchants", "set_merchant_category",
                    "create_category", "set_auto_record", "navigate",
                )
            }
        }
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
            if (Regex("月|月份|有没有数据|覆盖").containsMatchIn(s) || names.contains("get_summary")) {
                names += "list_months"
            }
            return all.filter { it.name in names }
        }
        // CHAT：挂只读 + 写账工具最小集，避免模型误以为「只能跳手动页」
        // 含 reclassify，支持「商家+金额区间批量改分类」委托真正落库
        return all.filter {
            it.name in setOf(
                "get_summary", "get_insights", "list_months", "navigate", "query_transactions",
                "add_transaction", "reclassify_transactions", "update_transaction_category", "create_category", "create_sub_category",
                "edit_transaction", "delete_transaction", "withdraw_transaction",
            )
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
     *
     * @param onTrace 工具执行审计轨迹（逐条：成功/失败、行数、权限、耗时），UI 实时展示。
     */
    suspend fun run(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        maxRounds: Int = 2,
        onStatus: (String) -> Unit = {},
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {},
        onTrace: (String) -> Unit = {},
    ): AgentResult {
val backend = backends.current()
        val onDevice = backend.id == SettingsRepository.BACKEND_ONDEVICE
        // 端侧：模型就位即可用，不强制「AI 服务商」开关；云端仍需开启 + Key
        if (!onDevice) {
            if (!settingsRepository.isAiEnabled()) return AgentResult("", 0, "AI 功能未开启")
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) return AgentResult("", 0, "未配置 API Key")
        }
val intent = IntentGate.classify(userMessage)
        val writeFast = intent == QueryIntent.LEDGER_WRITE ||
            IntentGate.isHighConfidenceSpend(userMessage) ||
            Regex("删|撤回|帮我记|记一笔|记上|撤销|无感|自动记账|改成|改到|归类|归入|改分类|批量改").containsMatchIn(userMessage)
        // 写操作授权：confirm=每次确认（默认）/ auto=授权后自动执行
        val autoExecute = settingsRepository.isAgentAutoExecute()
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
        // 小模型档：工具集进一步收敛（取前 8 个），提示词用 compact 档
        val tools = pickTools(userMessage, intent).let {
            if (backend.compactPrompt) it.take(8) else it
        }
        val cap = when (intent) {
            QueryIntent.CHAT, QueryIntent.NAV -> 2
            QueryIntent.LEDGER_WRITE -> maxOf(maxRounds, 4)
            else -> maxRounds
        }

        val messages = mutableListOf<ToolChatMessage>()
        val needsLedgerCtx = IntentGate.needsLedger(intent) || intent == QueryIntent.NAV || intent == QueryIntent.MEMORY
        if (needsLedgerCtx || intent == QueryIntent.CHAT) {
            // CHAT 也允许可选注入轻量快照；强意图则完整快照
            val allTx = if (IntentGate.needsLedger(intent)) {
                settingsRepository.filterByLedgerScope(accountRepository.getAll())
            } else emptyList()
            val coveredMonths = allTx.map { it.date.take(7) }.distinct().sorted()
            val snapshot = if (allTx.isNotEmpty()) buildSnapshot(allTx) else ""
            val memory = if (IntentGate.needsLedger(intent)) {
                runCatching { memoryStore.injectForNewSession() }.getOrDefault("")
            } else ""
            if (IntentGate.needsLedger(intent)) {
                runCatching { memoryStore.maybeCaptureFromUser(userMessage) }
            }
            val ledgerName = runCatching { ledgerRepository.getCurrent()?.name }.getOrNull() ?: "主账本"
            val system = when {
                IntentGate.needsLedger(intent) && backend.compactPrompt ->
                    AgentPromptBuilder.systemCompact(coveredMonths, snapshot, ledgerName, autoExecute) + "\n\n" + memory
                IntentGate.needsLedger(intent) ->
                    AgentPromptBuilder.systemFull(coveredMonths, snapshot, ledgerName, autoExecute) + "\n\n" + memory
                else -> AgentPromptBuilder.systemChatLite()
            }
            messages.add(ToolChatMessage("system", system))
        } else {
            messages.add(ToolChatMessage("system", AgentPromptBuilder.systemChatLite()))
        }
        // 上下文管理层：检索式注入 + 摘要压缩 + 滑动截断（负载与会话长度解耦）
        val ctx = AgentContextBuilder.build(history, userMessage)
        if (ctx.summary.isNotBlank()) {
            messages.add(ToolChatMessage("system", ctx.summary))
        }
        if (ctx.dropped > 0) {
            onTrace("上下文已压缩：注入 ${ctx.messages.size} 条相关历史，${ctx.dropped} 条折叠为摘要（长会话不拖慢思考）")
        }
        ctx.messages.forEach { (r, c) -> messages.add(ToolChatMessage(r, c)) }
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
            val resp = backend.chat(
                messages = messages,
                tools = tools,
                thinkingLevel = thinkingLevel,
                onDelta = onDelta,
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
                                content = "你还没有调用写账工具，账本没有任何改动（rows_affected=0）。若能办理：立刻调用 reclassify_transactions / update_transaction_category / add_transaction / withdraw_transaction / create_category（写工具后框架会弹确认）。若缺关键信息或确实办不到：如实回复「无法执行」并说明缺什么/为什么，**严禁**说已完成/已改好。",
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
                    onTrace("✗ ${tc.name} 已拦截：检测到重复调用循环（未执行，未落库）")
                    check.message ?: "拦截：检测到重复调用循环，已阻止本轮执行，请直接基于已有信息回答。"
                } else {
// 自动执行模式：跳过确认闸门直接落库；否则经 WriteGate 弹确认
                    val executed = agentTools.execute(tc, confirmed = autoExecute)
                    if (executed.content.startsWith(AgentTools.NEED_CONFIRM_PREFIX)) {
                        val summary = executed.content.removePrefix(AgentTools.NEED_CONFIRM_PREFIX)
                        onTrace("⏳ ${tc.name} 待用户确认（确认闸门拦截，未落库）")
                        return AgentResult(
                            reply = "这项操作会改账本，点「确认」后才执行：\n\n$summary",
                            toolRounds = rounds,
                            pending = PendingConfirm(tc.name, tc.arguments, summary),
                        )
                    }
                    // 可观测执行状态：成功/失败、行数、权限、耗时逐条展示
                    onTrace(
                        ToolExecutionRecord(
                            tool = tc.name,
                            ok = executed.ok,
                            affectedRows = executed.affectedRows,
                            elapsedMs = executed.elapsedMs,
                            permission = executed.permission,
                            permissionNote = executed.permissionNote,
                        ).verdictLine()
                    )
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
        val finalResp = backend.chat(
            messages = messages,
            tools = emptyList(),
            thinkingLevel = thinkingLevel,
            onDelta = onDelta,
            onReasoning = onReasoning,
        )
        return if (finalResp.error != null || finalResp.content.isBlank()) {
            AgentResult("(分析了 ${rounds} 轮仍不完整，请把问题拆小一点再问)", rounds)
        } else {
            AgentResult(sanitizeWriteReply(intent, finalResp.content, aggregatedAffected, anyLedgerWriteExecuted), rounds)
        }
    }

    /**
     * 自洽校验：任何"已完成/已改好/已记账"类宣称，必须以客观 rows_affected>0 为前提，
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

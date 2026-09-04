package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import com.simpleaccount.app.data.service.ToolChatMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账 Agent 循环（ReAct 风格）：
 * 1. 把用户消息 + 历史 + 工具定义发给模型
 * 2. 若模型要求调用工具（tool_calls）→ 执行对应记账工具 → 把结果回填 → 回到 1
 * 3. 直到模型输出最终文本回复，返回给用户
 *
 * 全程在 App 内部完成（内嵌），不依赖外部服务。
 */
@Singleton
class AgentLoop @Inject constructor(
    private val aiService: AiService,
    private val agentTools: AgentTools,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
) {

    data class AgentResult(val reply: String, val toolRounds: Int, val error: String? = null)

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
            else -> "正在调用工具 $toolName…"
        }

        /** 网络类错误（可重试）；HTTP 4xx（key/参数问题）不重试 */
        private fun isRetryable(error: String): Boolean =
            error.contains("HTTP 5") || error.contains("网络") || error.contains("timeout", true) ||
                error.contains("connect", true) || error.contains("Unable to resolve", true)
    }

    /**
     * 构建系统提示词。注入"今天日期"和账本覆盖的月份范围，
     * 让模型能正确解析"上个月/昨天/最近三个月"这类相对时间。
     */
    private fun buildSystemPrompt(coveredMonths: List<String>): String {
        val today = java.time.LocalDate.now()
        val prevMonth = java.time.YearMonth.now().minusMonths(1)
        val monthsDesc = if (coveredMonths.isEmpty()) "（账本暂无数据）"
        else "${coveredMonths.first()} 至 ${coveredMonths.last()}，共 ${coveredMonths.size} 个月"
        val dates = com.simpleaccount.app.util.DateResolver.anchorBlock()
        return """
你是一个专业、贴心的智能会计管家（Agent），运行在用户的记账 App 里。
$dates
账本数据覆盖：$monthsDesc。金额单位是元。
用户说「昨天/前天/今天/本月/上个月」时，必须用上面的时间锚点换成 yyyy-MM-dd 或 yyyy-MM 再调工具，绝对不要回答「日期未知」。

你可以调用工具获取/修改真实数据：
- 核对账本覆盖哪些月份 → list_months（查询结果为空、或不确定某月有没有数据时，先调它再下结论）
- 查具体交易明细 / 某类花销 / 某商家消费 → query_transactions（支持 month/type/category/keyword）
- 算某段时间收支总额 → get_summary
- 按分类统计 → get_category_totals（支持月份区间）
- 哪些商家花钱最多 → get_merchant_totals
- 某月每天花多少 / 哪天花得最多 → get_daily_totals
- 核对账本覆盖哪些月份 → list_months
- 用户说「我买了 X 花了 Y，帮我记上」→ 用 add_transaction 记账（从话里提取金额/商家/商品），记完告知流水号
- 用户要删除/撤回刚记的账 → 用 withdraw_transaction（带之前返回的流水号）
- 查看或排查商家归类 → list_merchants
- 给商家批量归类 → classify_merchants
- 跳转到任意页面（"打开统计""带我去导入"）→ navigate
- 编辑账单字段（金额/日期/备注/商家）→ edit_transaction；删除账单 → delete_transaction
- 新建/删除分类 → create_category / delete_category；设置商家固定映射 → set_merchant_category
- 设置每月预算 → set_monthly_budget；切换深浅色模式 → set_theme

工作规则：
1. 凡是涉及数字、金额、明细、统计的问题，必须先调用工具拿到真实结果再回答，绝不凭空编造金额或记录。
2. 解析相对时间必须换成具体日期再传参：今天=$today，昨天=${today.minusDays(1)}，前天=${today.minusDays(2)}，上个月=$prevMonth。query_transactions 的 date 传 yyyy-MM-dd，month 传 yyyy-MM。工具层也会再解析一次「昨天」这类词，但你自己先换算更稳。禁止输出「日期未知」。
3. 任何工具返回"没有数据/没有找到"时，不要直接告诉用户没数据——先调 list_months 核对账本实际覆盖的月份，确认参数月份是否算错；若该月确实无数据，明确说出账本覆盖范围并给出最近有数据月份的参考数字。
4. 工具结果标注"仅为部分数据"时，回答必须声明这一点；要给占比/排行结论时优先用 get_category_totals / get_merchant_totals（它们是全量汇总），不要用明细列表凑。
5. 一次工具结果不够就继续调用其它工具，多步综合分析后再回答；查询类问题通常 1-3 次工具调用足够。
6. 需要多份数据时（如既要看汇总又要看分类），尽量在一条回复里同时发起多个工具调用（并行查询），减少用户等待。
7. 回答用简体中文，语气友好自然；金额用「元」，保留两位小数。关键数字后注明数据依据（如"据9月账单"）。回答时给出有价值的观察或建议（占比、环比、异常消费），但不啰嗦。
8. 排版用 Markdown 结构化输出，重点一目了然：小节用 "### 标题"，关键数字/结论用 **加粗**，并列项用 "- " 列表，多组数据对比用 Markdown 表格（列数不超过 4 列，行数不超过 8 行）。不要用 emoji 堆砌，最多一两个。
8. 用户明确说要记账（"帮我记上""记一笔""买了X花了Y"）时：**先调用 add_transaction 把账记上**，再确认结果；严禁只说"好的我可以记"而不真正调用工具，严禁先说"账本里没有这笔所以记不了"——没查到的该记就记。
9. 用户要求改某一笔的分类 → 用 update_transaction_category（只改那一笔）；要改某商家所有账 → 才用 classify_merchants。不要混用。
10. 归类必须用工具完成，不要自己口头分类。
11. 用户闲聊或问与记账无关的问题时，礼貌回应并把话题引导回记账理财。
""".trimIndent()
    }

    /**
     * 跑一次完整的 Agent 对话回合。
     * @param history 之前若干轮的 (role, content)；system 由本方法注入第一位置。
     * @param onStatus 过程状态回调（如"正在查询账本…"），供 UI 展示 Agent 进度。
     * @return 最终文本回复。
     */
    suspend fun run(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        maxRounds: Int = 10,
        onStatus: (String) -> Unit = {},
    ): AgentResult {
        val enabled = settingsRepository.isAiEnabled()
        if (!enabled) return AgentResult("", 0, "AI 功能未开启")
        val apiKey = settingsRepository.apiKey()
        if (apiKey.isBlank()) return AgentResult("", 0, "未配置 API Key")
        val baseUrl = settingsRepository.baseUrl()
        val model = settingsRepository.model()

        // 账本覆盖的月份（供提示词描述时间范围）
        val coveredMonths = accountRepository.getAll()
            .map { it.date.take(7) }
            .distinct()
            .sorted()

        // 构建消息序列：system + 历史 + 用户新消息。
        // 历史条数由 ViewModel 按"30分钟会话窗口/20条兜底"规则截取，这里仅做防御性上限。
        val messages = mutableListOf<ToolChatMessage>()
        messages.add(ToolChatMessage("system", buildSystemPrompt(coveredMonths)))
        history.takeLast(40).forEach { (r, c) -> messages.add(ToolChatMessage(r, c)) }
        messages.add(ToolChatMessage("user", com.simpleaccount.app.util.DateResolver.enrichUserMessage(userMessage)))

        var rounds = 0
        var networkRetried = false
        val loopDetector = ToolLoopDetector()
        onStatus("正在思考…")
        while (rounds < maxRounds) {
            rounds++
            val resp = aiService.chatWithTools(
                baseUrl, apiKey, model, messages, agentTools.specs
            )
            if (resp.error != null) {
                // 网络类失败自动重试一次（重试上限 1 次）；key/参数类错误直接返回
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
                // 模型给出最终文本回答
                return AgentResult(resp.content, rounds)
            }

            onStatus("正在查询账本（第 $rounds 轮）…")

            // 模型要求调用工具：把 assistant 消息(带 tool_calls) + 各工具结果 append 进去
            messages.add(
                ToolChatMessage(role = "assistant", content = resp.content, toolCalls = resp.toolCalls)
            )
            var anyExecuted = false
            for (tc in resp.toolCalls) {
                onStatus(toolPhaseLabel(tc.name))
                // 循环检测：死循环直接拦截，不浪费轮数
                val check = loopDetector.check(tc.name, tc.arguments)
                val result = if (check.level == ToolLoopDetector.Level.CRITICAL) {
                    check.message ?: "[LOOP BLOCKED] 检测到死循环，请直接基于已有信息回答。"
                } else {
                    val executed = agentTools.execute(tc)
                    val warn = loopDetector.record(tc.name, tc.arguments, executed.content)
                    listOf(executed.content, check.message, warn.message)
                        .filter { !it.isNullOrBlank() }
                        .joinToString("\n")
                }
                messages.add(
                    ToolChatMessage(role = "tool", content = result, toolCallId = tc.id)
                )
                anyExecuted = true
            }
            if (!anyExecuted) {
                // 模型要工具但没成功执行：保险起见终止，避免死循环
                return AgentResult("(工具调用异常，请重试)", rounds)
            }
        }
        // 达到最大轮数：把已查到的数据喂回去，强制模型直接给出结论（而不是甩"上限"话术）
        messages.add(
            ToolChatMessage(
                role = "user",
                content = "请立即基于上面工具已经查到的数据，直接给出最终回答。不要再调用任何工具。"
            )
        )
        val finalResp = aiService.chatWithTools(baseUrl, apiKey, model, messages, emptyList())
        return if (finalResp.error != null || finalResp.content.isBlank()) {
            AgentResult("（分析了 ${rounds} 轮仍不完整，请把问题拆小一点再问）", rounds)
        } else {
            AgentResult(finalResp.content, rounds)
        }
    }
}

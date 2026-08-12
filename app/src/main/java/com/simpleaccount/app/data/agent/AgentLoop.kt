package com.simpleaccount.app.data.agent

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
) {

    data class AgentResult(val reply: String, val toolRounds: Int, val error: String? = null)

    private val systemPrompt = """
你是一个记账智能会计助手，运行在用户的记账 App 里。用户向你询问账本相关的事，你可以使用工具获取真实数据：
- 要查具体交易明细 / 某类花销 / 某商家，用 query_transactions
- 要算某段时间收支总额，用 get_summary
- 要知道按分类各花了多少，用 get_category_totals
- 要查看或排查商家归类，用 list_merchants
- 要给商家批量归类，用 classify_merchants

规则：
- 遇到需要数据的问题，先调用工具拿到真实结果，再回答，绝不凭空编造金额或数字。
- 如果一次工具结果不足以回答，可以继续调用其它工具，综合分析后再回答。
- 回答用简体中文，简洁、准确；金额用「元」做单位。如无完全把握，明说依据了什么数据。
- 归类输入必须用工具，不要自己猜分类。
""".trimIndent()

    /**
     * 跑一次完整的 Agent 对话回合。
     * @param history 之前若干轮的 (role, content)；system 由本方法注入第一位置。
     * @return 最终文本回复。
     */
    suspend fun run(
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        maxRounds: Int = 8,
    ): AgentResult {
        val enabled = settingsRepository.isAiEnabled()
        if (!enabled) return AgentResult("", 0, "AI 功能未开启")
        val apiKey = settingsRepository.apiKey()
        if (apiKey.isBlank()) return AgentResult("", 0, "未配置 API Key")
        val baseUrl = settingsRepository.baseUrl()
        val model = settingsRepository.model()

        // 构建消息序列：system + 历史(取最近若干轮) + 用户新消息
        val messages = mutableListOf<ToolChatMessage>()
        messages.add(ToolChatMessage("system", systemPrompt))
        history.takeLast(6).forEach { (r, c) -> messages.add(ToolChatMessage(r, c)) }
        messages.add(ToolChatMessage("user", userMessage))

        var rounds = 0
        while (rounds < maxRounds) {
            rounds++
            val resp = aiService.chatWithTools(
                baseUrl, apiKey, model, messages, agentTools.specs
            )
            if (resp.error != null) {
                return AgentResult("", rounds, resp.error)
            }

            if (resp.toolCalls.isEmpty()) {
                // 模型给出最终文本回答
                return AgentResult(resp.content, rounds)
            }

            // 模型要求调用工具：把 assistant 消息(带 tool_calls) + 各工具结果 append 进去
            messages.add(
                ToolChatMessage(role = "assistant", content = resp.content, toolCalls = resp.toolCalls)
            )
            var anyExecuted = false
            for (tc in resp.toolCalls) {
                val result = agentTools.execute(tc)
                messages.add(
                    ToolChatMessage(role = "tool", content = result.content, toolCallId = result.toolCallId)
                )
                anyExecuted = true
            }
            if (!anyExecuted) {
                // 模型要工具但没成功执行：保险起见终止，避免死循环
                return AgentResult("(工具调用异常，请重试)", rounds)
            }
        }
        // 达到最大轮数仍未给出最终回答
        return AgentResult("（已达分析上限，未得到完整结论，可再问一次）", rounds)
    }
}

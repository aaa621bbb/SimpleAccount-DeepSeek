package com.simpleaccount.app.data.agent

/**
 * Agent 上下文管理器（自研实现）：把"推理负载"与"会话长度"解耦。
 *
 * 旧实现把历史原样灌入每一轮推理，轮数越多、单次思考越久。
 * 新管线三步走：
 * 1. **滑动窗口保底**：永远保留最近 2 条（1 问 1 答），保证指代（"刚才那笔"）可解；
 * 2. **相关性检索注入**：其余历史按"词重叠 + 新鲜度"打分，只取与本轮问题相关的前 N 条；
 * 3. **摘要压缩**：被丢掉的历史压缩成一段 extractive 摘要（每条首句截断），不再参与逐字推理。
 *
 * 输出总量恒定有界（默认 ≤10 条、≤6000 字符），长会话下思考时延不再线性恶化。
 */
object AgentContextBuilder {

    /** 注入推理的历史上限（条）。 */
    const val MAX_MSGS = 10

    /** 注入推理的历史上限（字符）。 */
    const val MAX_CHARS = 6000

    /** 单条历史上限（字符），超长截断并标注。 */
    const val PER_MSG_CHARS = 1200

    /** 滑动窗口：无条件保留的最近消息条数。 */
    const val TAIL_KEEP = 2

    /** 摘要上限（字符）。 */
    const val SUMMARY_CHARS = 800

    data class AgentContext(
        /** 被截掉历史的压缩摘要（为空表示无截断）。 */
        val summary: String,
        /** 实际注入推理的历史（已按时间升序）。 */
        val messages: List<Pair<String, String>>,
        /** 本轮丢弃的历史条数（可观测）。 */
        val dropped: Int,
    )

    /**
     * @param history 按时间升序的 (role, content) 历史。
     * @param userMessage 本轮用户问题（检索 query）。
     */
    fun build(
        history: List<Pair<String, String>>,
        userMessage: String,
        maxMsgs: Int = MAX_MSGS,
        maxChars: Int = MAX_CHARS,
    ): AgentContext {
        if (history.isEmpty()) return AgentContext("", emptyList(), 0)
        if (history.size <= TAIL_KEEP) {
            return AgentContext("", history.map { it.first to clip(it.second) }, 0)
        }

        val tail = history.takeLast(TAIL_KEEP)
        val head = history.dropLast(TAIL_KEEP)

        // 相关性打分：词重叠为主，新鲜度为辅
        val queryTokens = tokenize(userMessage)
        val scored = head.mapIndexed { index, msg ->
            val overlap = if (queryTokens.isEmpty()) 0.0
            else tokenize(msg.second).intersect(queryTokens).size.toDouble()
            val freshness = index.toDouble() / head.size.coerceAtLeast(1)
            val roleBonus = if (msg.first == "user") 1.0 else 0.0
            Triple(msg, overlap * 3.0 + freshness * 2.0 + roleBonus, index)
        }

        val budget = (maxMsgs - tail.size).coerceAtLeast(0)
        // 相关性门槛：零重叠且陈旧的消息不注入（省 token、降噪音）；
        // 但若检索结果为空，仍保留最近 2 条 head 兜底，避免上下文断裂。
        val picked = scored.sortedByDescending { it.second }
            .filter { it.second > 1.0 }
            .take(budget)
            .ifEmpty { scored.sortedByDescending { it.third }.take(minOf(2, budget)) }
            .sortedBy { it.third }
            .map { it.first }

        val pickedIdx = picked.map { head.indexOf(it) }.toSet()
        val droppedMsgs = head.filterIndexed { i, _ -> i !in pickedIdx }

        // 字符预算：按时间倒序塞入，超预算的转摘要
        val kept = mutableListOf<Pair<String, String>>()
        var used = 0
        val overflow = mutableListOf<Pair<String, String>>()
        (picked + tail).asReversed().forEach { (role, content) ->
            val c = clip(content)
            if (used + c.length <= maxChars || kept.isEmpty()) {
                kept.add(role to c)
                used += c.length
            } else {
                overflow.add(role to content)
            }
        }
        val ordered = kept.asReversed()
        val summary = summarize(droppedMsgs + overflow.asReversed())

        return AgentContext(
            summary = summary,
            messages = ordered,
            dropped = droppedMsgs.size + overflow.size,
        )
    }

    private fun clip(content: String): String {
        val t = content.trim()
        if (t.length <= PER_MSG_CHARS) return t
        return t.take(PER_MSG_CHARS) + "\n…（历史过长已截断）"
    }

    /** extractive 摘要：每条取首句/前 60 字，拼成一段。 */
    private fun summarize(dropped: List<Pair<String, String>>): String {
        if (dropped.isEmpty()) return ""
        val sb = StringBuilder("【历史摘要（仅供参考：细节以工具实时查询为准，不要引用其中的金额当结论）】\n")
        dropped.forEach { (role, content) ->
            val who = if (role == "user") "用户曾问" else "管家曾答"
            val first = content.trim().replace(Regex("\\s+"), " ").take(60)
            if (first.isNotBlank()) sb.append("- ").append(who).append("：").append(first).append('\n')
            if (sb.length >= SUMMARY_CHARS) return sb.take(SUMMARY_CHARS).toString() + "…"
        }
        return sb.toString().trim().take(SUMMARY_CHARS)
    }

    /** 分词：CJK 按字、字母数字按词；停用词过滤。 */
    private fun tokenize(text: String): Set<String> {
        val out = mutableSetOf<String>()
        val cjk = Regex("[\\u4e00-\\u9fa5]")
        cjk.findAll(text).forEach { out.add(it.value) }
        Regex("[A-Za-z0-9]+").findAll(text).forEach { out.add(it.value.lowercase()) }
        return out - STOPWORDS
    }

    private val STOPWORDS = setOf(
        "的", "了", "吗", "呢", "啊", "呀", "吧", "么", "我", "你", "他", "她", "它",
        "是", "在", "有", "和", "与", "或", "就", "都", "也", "很", "不", "没",
        "这", "那", "哪", "什么", "怎么", "请", "帮", "个", "下", "上", "中",
        "the", "a", "an", "of", "to", "is", "it",
    )
}

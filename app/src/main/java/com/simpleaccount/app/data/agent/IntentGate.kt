package com.simpleaccount.app.data.agent

/**
 * 管家意图门控：分类用户意图以收敛工具集与上下文。
 *
 * v2.31.0：CHAT 不再意味着「禁止挂工具」——AgentLoop 仍可挂最小工具集，
 * 由模型自主裁决是否调用；needsLedger=false 时跳过全表快照以控制成本。
 */
enum class QueryIntent {
    CHAT,
    LEDGER_READ,
    LEDGER_WRITE,
    NAV,
    MEMORY,
}

object IntentGate {

    // 纯闲聊高频词：含这些且无账本关键词时直接 CHAT，避免“今天天气怎么样”误触发 LEDGER_READ
    private val CHAT_ONLY = Regex("天气|笑话|故事|翻译|写诗|新闻|股票|八卦|怎么做|怎么办|怎么用|你是谁|你叫什么|讲个|唱|画画|游戏")

    // 账本强信号：出现即视为账本意图
    private val LEDGER_STRONG = Regex("花了|花了多少|支出|收入|账单|账本|多少钱|多少元|分类|商家|体检|预算|结余|环比|超支|消费|记账|流水|哪类|花哪|月报|对账|报销")

    fun classify(raw: String): QueryIntent {
        val s = raw.trim()
        if (s.isEmpty()) return QueryIntent.CHAT

        // 闲聊优先：含闲聊词且无账本强信号 → CHAT（防止“今天天气”误判）
        if (CHAT_ONLY.containsMatchIn(s) && !LEDGER_STRONG.containsMatchIn(s) && !Regex("""\d+(?:\.\d+)?\s*元|[¥￥]""").containsMatchIn(s)) {
            return QueryIntent.CHAT
        }

        if (Regex("打开|跳转|带我去").containsMatchIn(s) &&
            Regex("统计|账本|设置|导入|无感|记一笔|管家|分类管理|外观|主题|预算").containsMatchIn(s)
        ) return QueryIntent.NAV

        if (Regex("记住这个|写入记忆|记到记忆").containsMatchIn(s)) return QueryIntent.MEMORY

        if (Regex(
                "记(?:一笔|上|账)|帮我记|入账|撤回|撤销|删掉|删除|" +
                    "改成|改到|改归|改分类|改一下|重分类|归类|归入|归到|" +
                    "映射|记到|算作|算成|调成|调到|批量改|批量归|" +
                    "把.{0,40}(?:改|归|算)",
            ).containsMatchIn(s)
        ) return QueryIntent.LEDGER_WRITE

        if (LEDGER_STRONG.containsMatchIn(s) ||
            Regex("""\d+(?:\.\d+)?\s*元|[¥￥]""").containsMatchIn(s)
        ) return QueryIntent.LEDGER_READ

        // 仅含时间词但无账本名词/金额 → 仍为 CHAT（“今天几号”“前天天气”不查账）
        if (Regex("今天|昨天|前天|上个月|本月|上午|下午|晚上").containsMatchIn(s) && !LEDGER_STRONG.containsMatchIn(s)) {
            return QueryIntent.CHAT
        }

        return QueryIntent.CHAT
    }

    fun needsLedger(intent: QueryIntent): Boolean =
        intent == QueryIntent.LEDGER_READ || intent == QueryIntent.LEDGER_WRITE
}

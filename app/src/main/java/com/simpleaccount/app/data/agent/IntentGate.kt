package com.simpleaccount.app.data.agent

/**
 * 管家意图门控：分类用户意图以收敛工具集与上下文。
 *
 * v2.31.0：CHAT 不再意味着「禁止挂工具」——AgentLoop 仍可挂最小工具集，
 * 由模型自主裁决是否调用；needsLedger=false 时跳过全表快照以控制成本。
 *
 * v2.31.1：高置信消费语句（时间 + 商户/消费动词，金额可缺）→ LEDGER_WRITE，
 * 不再因「无金额」被降级为 CHAT。
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
    private val LEDGER_STRONG = Regex(
        "花了|花了多少|支出|收入|账单|账本|多少钱|多少元|分类|商家|体检|预算|结余|环比|超支|消费|" +
            "记账|流水|哪类|花哪|月报|对账|报销|雪糕|奶茶|咖啡|外卖|地铁|买了|点了|喝了|结账|买单|付款"
    )

    /** 消费叙述动词（完成态为主，避免「点/买」单字误伤） */
    private val CONSUME_VERB = Regex(
        "买了|点了|吃了|喝了|订了|充了|交了|坐了|看了|消费了|结账|买单|付款|给了|转了|买了个|点了杯|吃了顿"
    )

    /** 时间线索：日期口语 / 钟点 / 时段 */
    private val TIME_CUE = Regex(
        "今天|今日|昨天|昨日|前天|大前天|本月|这个月|上个月|上月|" +
            "上午|下午|晚上|早上|中午|凌晨|" +
            """\d{1,2}\s*[:：点]\s*\d{0,2}|""" +
            """\d{1,2}\s*点半?"""
    )

    /** 商户/地点：「在X」「去X」 */
    private val PLACE_CUE = Regex("""在[\u4e00-\u9fa5A-Za-z0-9]{2,16}|去[\u4e00-\u9fa5A-Za-z0-9]{2,16}""")

    /** 金额（明示数字） */
    private val AMOUNT = Regex("""\d+(?:\.\d+)?\s*元|[¥￥]\s*\d+|(\d+(?:\.\d+)?)\s*块""")

    /** 查询「花了多少」类，不是消费叙述 */
    private val SPEND_QUERY = Regex("花了多少|花了几|多少钱|多少元|花哪|哪类|排行|统计|体检|结余|预算还")

    fun classify(raw: String): QueryIntent {
        val s = raw.trim()
        if (s.isEmpty()) return QueryIntent.CHAT

        // 闲聊优先：含闲聊词且无账本强信号/金额/消费结构 → CHAT
        if (CHAT_ONLY.containsMatchIn(s) &&
            !LEDGER_STRONG.containsMatchIn(s) &&
            !AMOUNT.containsMatchIn(s) &&
            !isHighConfidenceSpend(s)
        ) {
            return QueryIntent.CHAT
        }

        if (Regex("打开|跳转|带我去").containsMatchIn(s) &&
            Regex("统计|账本|设置|导入|无感|记一笔|管家|分类管理|外观|主题|预算").containsMatchIn(s)
        ) return QueryIntent.NAV

        if (Regex("记住这个|写入记忆|记到记忆").containsMatchIn(s)) return QueryIntent.MEMORY

        // 显式记账/改账/批量按金额归类
        if (Regex(
                "记(?:一笔|上|账)|帮我记|入账|撤回|撤销|删掉|删除|" +
                    "改成|改到|改归|改分类|改一下|重分类|归类|归入|归到|" +
                    "映射|记到|算作|算成|调成|调到|批量改|批量归|帮我归类|商家归类|待归类|" +
                    "按金额|按商家|小于|大于|不足|不少于|" +
                    "把.{0,40}(?:改|归|算)",
            ).containsMatchIn(s)
        ) return QueryIntent.LEDGER_WRITE

        // 高置信消费：时间 + (商户|消费动词)，金额可缺 → 写意图（由规则/Agent 接管，缺金额再追问）
        if (isHighConfidenceSpend(s)) return QueryIntent.LEDGER_WRITE

        if (LEDGER_STRONG.containsMatchIn(s) || AMOUNT.containsMatchIn(s)) {
            return QueryIntent.LEDGER_READ
        }

        // 仅含时间词但无账本名词/金额/消费结构 → 仍为 CHAT
        if (TIME_CUE.containsMatchIn(s) && !LEDGER_STRONG.containsMatchIn(s) && !isHighConfidenceSpend(s)) {
            return QueryIntent.CHAT
        }

        return QueryIntent.CHAT
    }

    /**
     * 高置信记账语句（叙述消费，非查询）：
     * - 有时间线索 + 消费动词（买了/点了…）
     * - 或 有时间线索 + 地点/商户（在蜜雪冰城…）且含消费动词/商品词
     * - 或 金额 + 商户/消费动词
     *
     * 例：「今天上午9:25在蜜雪冰城买了个雪糕」→ true
     * 排除：「本月花了多少」「昨天花了多少钱」
     */
    fun isHighConfidenceSpend(s: String): Boolean {
        if (SPEND_QUERY.containsMatchIn(s)) return false
        val hasTime = TIME_CUE.containsMatchIn(s)
        val hasVerb = CONSUME_VERB.containsMatchIn(s)
        val hasPlace = PLACE_CUE.containsMatchIn(s)
        val hasAmt = AMOUNT.containsMatchIn(s)
        val hasGoods = Regex("雪糕|奶茶|咖啡|外卖|地铁|饭|餐|票|充值|话费|拿铁|雪王").containsMatchIn(s)
        if (hasAmt && (hasVerb || hasPlace || hasGoods)) return true
        if (hasTime && hasVerb) return true
        if (hasTime && hasPlace && (hasVerb || hasGoods)) return true
        return false
    }

    fun needsLedger(intent: QueryIntent): Boolean =
        intent == QueryIntent.LEDGER_READ || intent == QueryIntent.LEDGER_WRITE
}

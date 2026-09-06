package com.simpleaccount.app.data.agent

/**
 * 管家意图门控：无关 query 不碰账本、不挂工具，避免首答被一次全表扫描拖死。
 */
enum class QueryIntent {
    CHAT,
    LEDGER_READ,
    LEDGER_WRITE,
    NAV,
    MEMORY,
}

object IntentGate {

    fun classify(raw: String): QueryIntent {
        val s = raw.trim()
        if (s.isEmpty()) return QueryIntent.CHAT

        if (Regex("打开|跳转|带我去").containsMatchIn(s) &&
            Regex("统计|账本|设置|导入|无感|记一笔|管家|分类管理").containsMatchIn(s)
        ) return QueryIntent.NAV

        if (Regex("记住这个|写入记忆|记到记忆").containsMatchIn(s)) return QueryIntent.MEMORY

        if (Regex(
                "记(?:一笔|上|账)|帮我记|入账|撤回|撤销|删掉|删除|" +
                    "改成|改到|改归|改分类|改一下|重分类|归类|归入|归到|" +
                    "映射|记到|算作|算成|调成|调到|批量改|批量归|" +
                    "把.{0,40}(?:改|归|算)",
            ).containsMatchIn(s)
        ) return QueryIntent.LEDGER_WRITE

        if (Regex(
                "花了|花了多少|支出|收入|账单|账本|多少钱|多少元|" +
                    "分类|商家|体检|预算|结余|环比|超支|消费|记账|流水|" +
                    "哪类|花哪|月报",
            ).containsMatchIn(s) ||
            Regex("""\d+(?:\.\d+)?\s*元|[¥￥]""").containsMatchIn(s)
        ) return QueryIntent.LEDGER_READ

        return QueryIntent.CHAT
    }

    fun needsLedger(intent: QueryIntent): Boolean =
        intent == QueryIntent.LEDGER_READ || intent == QueryIntent.LEDGER_WRITE
}

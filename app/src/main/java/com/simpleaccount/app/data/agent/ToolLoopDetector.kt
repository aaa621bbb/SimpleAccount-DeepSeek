package com.simpleaccount.app.data.agent

import com.simpleaccount.app.util.AppLog

/**
 * Agent 工具调用护栏（本项目原创实现）：
 * 防止模型在同一问题上用相同参数反复调用同一个工具——原地打转既烧 token，
 * 写库类工具（如 add_transaction）还可能造成重复记账。它监控两种信号：
 * - 连续重复：紧挨着用相同参数调用同一工具多次，中间没有穿插其它调用 —— 一定没有进展；
 * - 累计重复：穿插其它调用之后，又带着相同参数回到同一工具 —— 多数时候也没有进展。
 *
 * 信号弱时在工具结果里附加提醒（WARNING，模型可自行改换策略）；
 * 信号强时在执行前直接拦截（CRITICAL），把拦截说明当作工具结果喂回模型，强制其基于已有信息作答。
 *
 * 判定只比对"工具名 + 参数原文"：OpenAI 兼容接口的 tool_calls 参数由模型端序列化，
 * 同一逻辑请求被重复生成时参数文本通常逐字节一致，无需再做排序规范化或哈希摘要。
 * 每轮 Agent 回合新建一个实例，互不干扰。
 */
class ToolLoopDetector {

    enum class Level { NONE, WARNING, CRITICAL }

    data class LoopCheckResult(val level: Level, val message: String? = null) {
        companion object {
            val NONE = LoopCheckResult(Level.NONE)
        }
    }

    /** 累计第 3 次相同调用 → 提醒模型换策略 */
    private val warnOnSameTotal = 3

    /** 累计第 4 次相同调用 → 拦截（含本次） */
    private val blockOnSameTotal = 4

    /** 连续第 4 次相同调用 → 拦截（含本次；比累计更可疑，但给同样的宽容度） */
    private val blockOnSameRun = 4

    /** 最多记住的调用签名条数，防止超长回合里内存无界增长 */
    private val rememberLimit = 60

    /** 最近成功的调用签名（按发生顺序） */
    private val recent = ArrayDeque<String>()

    /** 上一次成功调用的签名，用于连续重复计数 */
    private var lastSignature: String? = null

    /** 当前连续相同签名的成功调用次数 */
    private var runStreak = 0

    fun reset() {
        recent.clear()
        lastSignature = null
        runStreak = 0
    }

    /**
     * 执行前检查（本次调用尚未计入历史）：
     * - CRITICAL：调用方必须跳过真实执行，把 message 直接作为工具结果返回；
     * - WARNING：调用方照常执行，把 message 追加进工具结果提醒模型。
     */
    fun check(toolName: String, args: String): LoopCheckResult {
        val sig = signature(toolName, args)
        val total = recent.count { it == sig } + 1 // 含即将发生的这次
        val consecutive = if (lastSignature == sig) runStreak + 1 else 1

        if (consecutive >= blockOnSameRun || total >= blockOnSameTotal) {
            AppLog.w("ToolLoop: 拦截 ${toolName} 同参调用 连续=$consecutive 累计=$total")
            return LoopCheckResult(
                Level.CRITICAL,
                "拦截：工具「$toolName」使用相同参数已连续调用 $consecutive 次（累计 $total 次）。" +
                    "查询类工具不会因此返回新数据，写库类工具再执行会重复记账。本轮禁止再次执行该调用，" +
                    "请直接基于已经拿到的信息回答用户；确实需要新数据时，请改用不同的查询参数或其它工具。"
            )
        }
        if (total >= warnOnSameTotal) {
            return LoopCheckResult(
                Level.WARNING,
                "提醒：工具「$toolName」即将用相同参数进行第 $total 次调用，此前的调用没有带来新信息。" +
                    "请停止重复：要么换参数或换工具，要么直接基于已有信息给出回答。"
            )
        }
        return LoopCheckResult.NONE
    }

    /**
     * 执行后记录本次调用，供下一次 check 统计。
     * 失败调用（结果以"错误/参数错误"开头）不累计——临时性失败值得重试一两次，
     * 只有成功返回才能证明"原地打转"。
     */
    fun record(toolName: String, args: String, result: String): LoopCheckResult {
        val failed = result.startsWith("错误") || result.startsWith("参数错误")
        if (!failed) {
            val sig = signature(toolName, args)
            runStreak = if (lastSignature == sig) runStreak + 1 else 1
            lastSignature = sig
            recent.addLast(sig)
            while (recent.size > rememberLimit) recent.removeFirst()
        }
        return LoopCheckResult.NONE
    }

    private fun signature(toolName: String, args: String): String =
        toolName + "\u0000" + args.trim()
}

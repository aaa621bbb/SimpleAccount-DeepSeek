package com.simpleaccount.app.data.agent

/**
 * 写操作确认闸门。
 *
 * v2.31.0：闸门行为可由用户「智能体执行授权」配置：
 * - confirm（默认）：凡 [GATED] 中的工具，未经 confirmed=true 一律不得落库；
 *   未确认调用只返回 [AgentTools.NEED_CONFIRM_PREFIX] 预览，由 UI 弹出确认卡片。
 * - auto：用户授权后自动执行，AgentLoop 以 confirmed=true 调用，闸门放行。
 *
 * 只读查询工具不受限。navigate / memory_write(daily) 不在闸门内。
 */
object WriteGate {

    /** 必须经用户确认（或自动执行授权）才能执行的写工具。 */
    val GATED: Set<String> = setOf(
        // 账本写
        "add_transaction",
        "withdraw_transaction",
        "delete_transaction",
        "edit_transaction",
        "update_transaction_category",
        "reclassify_transactions",
        // 分类/映射写
        "create_category",
        "delete_category",
        "set_merchant_category",
        "classify_merchants",
        // 设置写
        "set_monthly_budget",
        "set_theme",
        "set_auto_record",
    )

    fun isGated(tool: String): Boolean = tool in GATED
}

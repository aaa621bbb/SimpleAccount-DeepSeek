package com.simpleaccount.app.data.agent

/**
 * 写操作确认闸门（强制项，不可绕过）。
 *
 * 规则：凡 [GATED] 中的工具，未经用户手动批准（confirmed=true）一律不得落库；
 * 未确认调用只返回 [AgentTools.NEED_CONFIRM_PREFIX] 开头的预览，由 UI 弹出确认卡片。
 * 只读查询工具不受限。
 *
 * 注意 navigate 不在闸门内（它不改数据）；memory_write 的每日速记是管家工作笔记，
 * 非用户账本数据变更，同样不受限（GLOBAL 写入仍要求用户明确说"记住这个"）。
 */
object WriteGate {

    /** 必须经用户确认才能执行的写工具（新增/删除/修改 App 数据或设置）。 */
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

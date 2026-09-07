package com.simpleaccount.app.data.agent

/**
 * 工具执行审计（自研）：一切工具调用须呈现可观测的执行状态。
 *
 * 根治两类不诚实行为：
 * 1. **虚假完成**：写操作以 [affectedRows]（落库核验行数）为唯一回执依据，
 *    rows=0 时任何"已完成"宣称都会被 [AgentLoop] 的自洽校验拦截；
 * 2. **谎称无权**：权限状态由框架如实检测并暴露（[ToolPermission]），
 *    模型不得编造"拿不到工具权限"——UI 轨迹里会逐条展示权限是否授予、
 *    不可用原因是什么。
 */
enum class ToolPermission {
    /** 无需权限（纯本地账本读写）。 */
    NOT_REQUIRED,

    /** 权限已授予。 */
    GRANTED,

    /** 权限被拒绝/未授予（附原因）。 */
    DENIED,

    /** 能力当前不可用（非权限原因，如导航器未就绪）。 */
    UNAVAILABLE,
}

/** 单次工具调用的执行记录（展示给用户的轨迹 + 写入日志）。 */
data class ToolExecutionRecord(
    val tool: String,
    val ok: Boolean,
    val affectedRows: Int,
    val elapsedMs: Long,
    val permission: ToolPermission,
    val permissionNote: String?,
) {
    /** 一行式轨迹：✓ add_transaction 成功 rows=1 耗时23ms */
    fun verdictLine(): String = buildString {
        append(if (ok) "✓ " else "✗ ")
        append(tool)
        append(if (ok) " 成功" else " 失败")
        if (affectedRows > 0) append(" rows=").append(affectedRows)
        append(" 耗时").append(elapsedMs).append("ms")
        if (permission != ToolPermission.NOT_REQUIRED) {
            append(" 权限:")
            append(
                when (permission) {
                    ToolPermission.GRANTED -> "已授予"
                    ToolPermission.DENIED -> "未授予"
                    ToolPermission.UNAVAILABLE -> "不可用"
                    else -> ""
                }
            )
            if (!permissionNote.isNullOrBlank()) append("（").append(permissionNote).append("）")
        } else if (!permissionNote.isNullOrBlank()) {
            append("（").append(permissionNote).append("）")
        }
    }
}

object ToolAudit {
    private val ROWS_RE = Regex("""rows_affected=(\d+)""")

    /** 从工具回执文本中提取客观落库行数（累加多处）。 */
    fun affectedRowsOf(content: String): Int =
        ROWS_RE.findAll(content).mapNotNull { it.groupValues[1].toIntOrNull() }.sum()

    /** 回执是否携带"账本已核验"成功体。 */
    fun isVerified(content: String): Boolean =
        content.contains("【账本已核验】") && Regex("""rows_affected=[1-9]""").containsMatchIn(content)
}

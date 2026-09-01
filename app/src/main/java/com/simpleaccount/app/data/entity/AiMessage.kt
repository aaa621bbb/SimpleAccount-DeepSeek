package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 对话消息（可溯源）：每条消息有唯一 id + 所属会话 + 工具调用归属 + 状态。
 * status 支撑撤回（withdrawn 后 UI 显示"已撤回"占位）与错误态（error）。
 */
@Entity(
    tableName = "ai_messages",
    indices = [Index("conversationId")]
)
data class AiMessage(
    /** 消息唯一 id（UUID），支撑定位/撤回/删除 */
    @PrimaryKey val id: String,
    /** 所属会话 id */
    val conversationId: String,
    /** "user" | "assistant" | "tool" */
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** tool 消息归属的调用 id（assistant tool_calls 的 id），可空 */
    val toolCallId: String? = null,
    /** 工具名（tool/assistant 工具调用消息可记），可空 */
    val toolName: String? = null,
    /** "pending" | "done" | "error" | "withdrawn" */
    val status: String = STATUS_DONE,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"
        const val STATUS_WITHDRAWN = "withdrawn"
    }
}

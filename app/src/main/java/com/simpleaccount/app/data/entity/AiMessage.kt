package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 对话消息。
 */
@Entity(tableName = "ai_messages")
data class AiMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** "user" | "assistant" */
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

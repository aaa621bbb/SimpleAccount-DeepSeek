package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 会话。每个会话的消息互不串扰，支持新建/切换/删除。
 */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String,
    /** 会话标题（首条用户消息自动命名，可改名） */
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

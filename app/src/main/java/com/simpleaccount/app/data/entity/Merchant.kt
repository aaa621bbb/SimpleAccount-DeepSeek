package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 商家记忆。用于导入账单的自动/辅助分类。
 */
@Entity(tableName = "merchants")
data class Merchant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 商家名（唯一索引） */
    val merchant: String,
    /** 关联分类名 */
    val category: String = "",
    /** "classified" AI/规则自动 | "pending" 待归类 | "user_set" 用户手动（最高优先） */
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_CLASSIFIED = "classified"
        const val STATUS_PENDING = "pending"
        const val STATUS_USER_SET = "user_set"
    }
}

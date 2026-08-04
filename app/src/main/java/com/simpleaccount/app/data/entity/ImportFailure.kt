package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 导入失败明细（R1 需求）。
 * 每次导入被跳过/失败的行，记录内容与原因，供用户查看。
 */
@Entity(tableName = "import_failures")
data class ImportFailure(
    /** 批次 ID（与 import_logs.batchId 对应） */
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: String,
    /** 该失败行的原始内容摘要 */
    val content: String,
    /** 失败原因（中文） */
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

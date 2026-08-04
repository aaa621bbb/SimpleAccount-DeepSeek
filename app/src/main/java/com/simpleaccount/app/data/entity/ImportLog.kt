package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 导入记录。batchId 即主键。
 */
@Entity(tableName = "import_logs")
data class ImportLog(
    /** 批次 ID，导入时生成的 UUID */
    @PrimaryKey val batchId: String,
    /** "wechat" 微信 | "alipay" 支付宝 */
    val sourceType: String,
    val fileName: String,
    val importDate: Long,
    val recordCount: Int,
    val skipCount: Int,
    /** 最后一条记录的日期 yyyy-MM-dd，可空 */
    val lastTransactionDate: String? = null,
)

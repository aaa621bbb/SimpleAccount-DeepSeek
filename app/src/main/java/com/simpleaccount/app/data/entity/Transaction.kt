package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 流水记录。金额一律以"分"(Long) 存储，避免浮点误差。
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 金额，单位为分（1元 = 100分），恒为正数 */
    val amount: Long,
    /** "expense" 支出 | "income" 收入 */
    val type: String,
    /** 分类名（与 Category.name 对应） */
    val category: String,
    /** 日期，格式 yyyy-MM-dd */
    val date: String,
    /** 时间 HH:mm（账单/截图里有精确时间时记录；空=未知） */
    val time: String = "",
    val note: String = "",
    val merchant: String = "",
    val product: String = "",
    /** 收/付款方式（微信"支付方式"，仅导入时记录，不用于展示） */
    val paymentMethod: String = "",
    /** 交易单号（微信"交易单号"，存着不展示） */
    val tradeOrderNo: String = "",
    /** 商家/商户单号（微信"商户单号"，存着不展示） */
    val merchantOrderNo: String = "",
    /** 所属账本。默认主账本，查询/AI 都按当前账本隔离。 */
    val ledgerId: Long = 1L,
    /** "manual" 手动 | "import" 导入 | "auto" 无感记账自动抓取 */
    val source: String,
    /** 导入批次 ID，手动记录为 null */
    val importBatchId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_IMPORT = "import"

        /** 无感记账自动抓取（通知监听） */
        const val SOURCE_AUTO = "auto"
    }
}

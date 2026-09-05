package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账本。流水按 ledgerId 隔离，AI / 统计 / 首页都只看当前账本，避免串味。
 */
@Entity(tableName = "ledgers")
data class Ledger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** 主账本不可删 */
    val isDefault: Boolean = false,
) {
    companion object {
        const val DEFAULT_NAME = "主账本"
        const val DEFAULT_ID = 1L
    }
}

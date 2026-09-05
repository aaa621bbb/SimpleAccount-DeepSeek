package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 分类。只做一级，不做父子。
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 分类名（唯一） */
    val name: String,
    /** "expense" 支出 | "income" 收入 */
    val type: String,
    /** 排序权重 */
    val sortOrder: Int = 0,
    /** 是否为预置分类（预置不可删） */
    val isPreset: Boolean = false,
    /** Material 图标名，运行时用 when 映射 */
    val iconName: String = "more_horiz",
    /** 十六进制颜色，如 "#2D8CF0" */
    val colorHex: String = "#BDC3C7",
) {
    companion object {
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
    }
}

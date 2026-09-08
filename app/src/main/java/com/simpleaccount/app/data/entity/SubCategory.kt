package com.simpleaccount.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 二级分类：挂在一级分类名之下（如"餐饮"→"早餐/午餐/晚餐/夜宵/奶茶/零食"）。
 *
 * 以 [parent]（一级分类名）归属；一级改名时由
 * [com.simpleaccount.app.ui.settings.CategoryManageViewModel] 同步迁移。
 * 排序完全由 [sortOrder] 决定，用户可在分类管理里自由调整，不被内置顺序锁死。
 */
@Entity(
    tableName = "sub_categories",
    indices = [Index(value = ["parent"])]
)
data class SubCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 所属一级分类名（与 Category.name 对应）。 */
    val parent: String,
    /** 二级分类名（同一 parent 下唯一）。 */
    val name: String,
    /** 排序权重（用户自定义顺序）。 */
    val sortOrder: Int = 0,
    /** 是否为预置二级分类（预置可删——二级不锁死，删后由用户自建）。 */
    val isPreset: Boolean = false,
    /** Material 图标名（与 [Category.iconName] 同一套 IconMapper）。 */
    val iconName: String = "more_horiz",
)

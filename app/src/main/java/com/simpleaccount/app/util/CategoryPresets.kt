package com.simpleaccount.app.util

import com.simpleaccount.app.data.entity.Category

/**
 * 预置分类定义（终稿 三/分类）。预置分类不可删。
 */
object CategoryPresets {

    /** (名称, 图标名, 颜色Hex) */
    private val EXPENSE = listOf(
        Triple("餐饮", "restaurant", "#FF6B6B"),
        Triple("交通", "directions_car", "#4ECDC4"),
        Triple("购物", "shopping_cart", "#45B7D1"),
        Triple("娱乐", "movie", "#96CEB4"),
        Triple("医疗", "local_hospital", "#FF9F43"),
        Triple("教育", "school", "#A29BFE"),
        Triple("居住", "home", "#00B894"),
        Triple("通讯", "phone", "#FDCB6E"),
        Triple("转账", "swap_horiz", "#8E44AD"),
        Triple("其它", "more_horiz", "#BDC3C7"),
    )

    /** 收入预置 */
    private val INCOME = listOf(
        Triple("工资", "attach_money", "#2ECC71"),
        Triple("奖金", "card_giftcard", "#E67E22"),
        Triple("投资", "trending_up", "#6C5CE7"),
        Triple("兼职", "work", "#00CEC9"),
        Triple("退款", "assignment_return", "#1ABC9C"),
        Triple("其它收入", "more_horiz", "#636E72"),
    )

    /** 生成预置分类列表（首次启动灌入） */
    fun presetCategories(): List<Category> {
        val result = mutableListOf<Category>()
        EXPENSE.forEachIndexed { i, (name, icon, color) ->
            result.add(
                Category(
                    name = name, type = Category.TYPE_EXPENSE,
                    sortOrder = i, isPreset = true, iconName = icon, colorHex = color
                )
            )
        }
        INCOME.forEachIndexed { i, (name, icon, color) ->
            result.add(
                Category(
                    name = name, type = Category.TYPE_INCOME,
                    sortOrder = i, isPreset = true, iconName = icon, colorHex = color
                )
            )
        }
        return result
    }

    /** 默认"其它"分类名（支出） */
    const val DEFAULT_EXPENSE_CATEGORY = "其它"
    const val DEFAULT_INCOME_CATEGORY = "其它收入"

    /** 转账分类（支出） */
    const val TRANSFER_CATEGORY = "转账"
    /** 退款分类（收入） */
    const val REFUND_CATEGORY = "退款"

    /**
     * 候选集 = 预置 ∪ 用户自建。按名称去重（库内记录优先），支出在前、预置在前。
     * 商家归类 / 记一笔 / 工具写分类都必须走这里，禁止只列内置名录。
     */
    fun union(db: List<Category>): List<Category> {
        val byName = LinkedHashMap<String, Category>()
        db.forEach { byName[it.name] = it }
        presetCategories().forEach { p ->
            if (p.name !in byName) byName[p.name] = p
        }
        return byName.values.sortedWith(
            compareBy<Category> { if (it.type == Category.TYPE_EXPENSE) 0 else 1 }
                .thenBy { if (it.isPreset) 0 else 1 }
                .thenBy { it.sortOrder }
                .thenBy { it.name }
        )
    }
}

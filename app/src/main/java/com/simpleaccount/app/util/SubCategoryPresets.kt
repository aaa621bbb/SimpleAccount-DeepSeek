package com.simpleaccount.app.util

import com.simpleaccount.app.data.entity.SubCategory

/**
 * 二级分类预置体系：每个一级分类各自一套二级子类。
 * 首次启动灌入；用户可增删、自由排序。
 */
object SubCategoryPresets {

    /** 一级分类名 → 二级名列表（顺序即初始展示顺序）。 */
    val PRESETS: Map<String, List<String>> = mapOf(
        "餐饮" to listOf("早餐", "午餐", "晚餐", "夜宵", "奶茶", "咖啡", "零食", "水果", "外卖"),
        "交通" to listOf("公交", "地铁", "打车", "火车", "飞机", "加油", "停车", "骑行", "高速"),
        "购物" to listOf("日用", "服饰", "数码", "生鲜", "家居", "美妆", "母婴", "文具"),
        "娱乐" to listOf("电影", "游戏", "会员", "演出", "运动", "旅游", "聚会"),
        "医疗" to listOf("挂号", "药品", "体检", "牙科", "眼科", "住院"),
        "教育" to listOf("学费", "书籍", "培训", "考试", "网课", "文具"),
        "居住" to listOf("房租", "水电", "物业", "燃气", "酒店", "家政", "维修", "宽带"),
        "通讯" to listOf("话费", "流量", "宽带", "月租"),
        "转账" to listOf("红包", "代付", "还款", "转账"),
        "其它" to listOf("其它"),
        "工资" to listOf("月薪", "绩效", "年终", "补贴"),
        "奖金" to listOf("年终奖", "项目奖", "其它奖金"),
        "投资" to listOf("股票", "基金", "利息", "分红"),
        "兼职" to listOf("劳务", "稿费", "咨询", "其它兼职"),
        "退款" to listOf("购物退款", "其它退款"),
        "其它收入" to listOf("红包", "其它"),
    )

    /** 生成预置二级分类列表（首次启动灌入）。 */
    fun presetSubCategories(): List<SubCategory> {
        val result = mutableListOf<SubCategory>()
        PRESETS.forEach { (parent, subs) ->
            subs.forEachIndexed { i, name ->
                result.add(SubCategory(parent = parent, name = name, sortOrder = i, isPreset = true))
            }
        }
        return result
    }

    /** 某一级分类的预置二级名（二级为空兜底展示用）。 */
    fun subsFor(parent: String): List<String> = PRESETS[parent].orEmpty()
}

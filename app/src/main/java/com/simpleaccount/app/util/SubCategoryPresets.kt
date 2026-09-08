package com.simpleaccount.app.util

import com.simpleaccount.app.data.entity.SubCategory

/**
 * 二级分类预置体系：每个一级分类各自一套二级子类（含图标）。
 * 首次启动灌入；用户可增删、自由排序。
 */
object SubCategoryPresets {

    /** 二级名 → 默认图标（覆盖更细类目）。 */
    val SUB_ICONS: Map<String, String> = mapOf(
        // 餐饮
        "早餐" to "free_breakfast", "午餐" to "lunch_dining", "晚餐" to "dinner_dining",
        "夜宵" to "nightlife", "奶茶" to "local_cafe", "咖啡" to "coffee",
        "零食" to "cookie", "水果" to "nutrition", "外卖" to "delivery_dining",
        "堂食" to "restaurant", "烧烤" to "outdoor_grill", "火锅" to "soup_kitchen",
        // 交通
        "公交" to "directions_bus", "地铁" to "directions_subway", "打车" to "local_taxi",
        "火车" to "train", "飞机" to "flight", "加油" to "local_gas_station",
        "停车" to "local_parking", "骑行" to "directions_bike", "高速" to "add_road",
        "共享单车" to "pedal_bike", "轮渡" to "directions_boat",
        // 购物
        "日用" to "shopping_basket", "服饰" to "checkroom", "数码" to "devices",
        "生鲜" to "set_meal", "家居" to "chair", "美妆" to "face",
        "母婴" to "child_care", "文具" to "edit", "电器" to "kitchen",
        "鞋靴" to "ice_skating", "箱包" to "shopping_bag",
        // 娱乐
        "电影" to "movie", "游戏" to "sports_esports", "会员" to "card_membership",
        "演出" to "theater_comedy", "运动" to "sports_soccer", "旅游" to "luggage",
        "聚会" to "groups", "KTV" to "mic", "展览" to "museum",
        // 医疗
        "挂号" to "local_hospital", "药品" to "medication", "体检" to "monitor_heart",
        "牙科" to "dentistry", "眼科" to "visibility", "住院" to "hotel",
        "中医" to "spa", "疫苗" to "vaccines",
        // 教育
        "学费" to "school", "书籍" to "menu_book", "培训" to "psychology",
        "考试" to "quiz", "网课" to "cast_for_education",
        // 居住
        "房租" to "apartment", "水电" to "electrical_services", "物业" to "home",
        "燃气" to "local_fire_department", "酒店" to "hotel", "家政" to "cleaning_services",
        "维修" to "build", "宽带" to "wifi", "家具" to "weekend",
        // 通讯
        "话费" to "phone", "流量" to "signal_cellular_alt", "月租" to "router",
        // 转账
        "红包" to "card_giftcard", "代付" to "payments", "还款" to "account_balance",
        "转账" to "swap_horiz",
        // 收入
        "月薪" to "payments", "绩效" to "emoji_events", "年终" to "savings",
        "补贴" to "volunteer_activism", "年终奖" to "emoji_events", "项目奖" to "star",
        "其它奖金" to "paid", "股票" to "show_chart", "基金" to "trending_up",
        "利息" to "account_balance", "分红" to "pie_chart",
        "劳务" to "work", "稿费" to "edit_note", "咨询" to "support_agent",
        "其它兼职" to "work", "购物退款" to "assignment_return", "其它退款" to "replay",
        "其它" to "more_horiz", "其它收入" to "attach_money",
    )

    /** 一级分类名 → 二级名列表（顺序即初始展示顺序）。 */
    val PRESETS: Map<String, List<String>> = mapOf(
        "餐饮" to listOf("早餐", "午餐", "晚餐", "夜宵", "奶茶", "咖啡", "零食", "水果", "外卖", "烧烤", "火锅"),
        "交通" to listOf("公交", "地铁", "打车", "火车", "飞机", "加油", "停车", "骑行", "高速", "共享单车"),
        "购物" to listOf("日用", "服饰", "数码", "生鲜", "家居", "美妆", "母婴", "文具", "电器", "鞋靴"),
        "娱乐" to listOf("电影", "游戏", "会员", "演出", "运动", "旅游", "聚会", "KTV", "展览"),
        "医疗" to listOf("挂号", "药品", "体检", "牙科", "眼科", "住院", "中医", "疫苗"),
        "教育" to listOf("学费", "书籍", "培训", "考试", "网课", "文具"),
        "居住" to listOf("房租", "水电", "物业", "燃气", "酒店", "家政", "维修", "宽带", "家具"),
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

    fun iconFor(subName: String): String = SUB_ICONS[subName] ?: "more_horiz"

    /** 生成预置二级分类列表（首次启动灌入）。 */
    fun presetSubCategories(): List<SubCategory> {
        val result = mutableListOf<SubCategory>()
        PRESETS.forEach { (parent, subs) ->
            subs.forEachIndexed { i, name ->
                result.add(
                    SubCategory(
                        parent = parent,
                        name = name,
                        sortOrder = i,
                        isPreset = true,
                        iconName = iconFor(name),
                    )
                )
            }
        }
        return result
    }

    /** 某一级分类的预置二级名（二级为空兜底展示用）。 */
    fun subsFor(parent: String): List<String> = PRESETS[parent].orEmpty()
}

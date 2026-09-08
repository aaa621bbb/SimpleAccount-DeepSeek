package com.simpleaccount.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标库：每个 name 全局唯一映射；带中文 label 供 AI / 用户选图。
 * [nextFreeIcon] 优先返回语义贴合且未被占用的图标。
 */
object IconMapper {

    data class IconChoice(val name: String, val label: String)

    /** 支出可选（≥80，互不重复 name） */
    val expenseIcons: List<IconChoice> = listOf(
        IconChoice("restaurant", "餐饮"),
        IconChoice("free_breakfast", "早餐"),
        IconChoice("lunch_dining", "午餐"),
        IconChoice("dinner_dining", "晚餐"),
        IconChoice("local_cafe", "奶茶咖啡"),
        IconChoice("coffee", "咖啡"),
        IconChoice("local_bar", "酒水"),
        IconChoice("fastfood", "快餐外卖"),
        IconChoice("delivery_dining", "外卖"),
        IconChoice("icecream", "甜品"),
        IconChoice("cake", "蛋糕"),
        IconChoice("cookie", "零食"),
        IconChoice("nutrition", "水果"),
        IconChoice("set_meal", "生鲜套餐"),
        IconChoice("soup_kitchen", "火锅"),
        IconChoice("outdoor_grill", "烧烤"),
        IconChoice("directions_car", "开车"),
        IconChoice("directions_bus", "公交"),
        IconChoice("directions_subway", "地铁"),
        IconChoice("local_taxi", "打车"),
        IconChoice("two_wheeler", "摩托"),
        IconChoice("pedal_bike", "共享单车"),
        IconChoice("directions_bike", "骑行"),
        IconChoice("flight", "飞机"),
        IconChoice("train", "火车"),
        IconChoice("directions_boat", "轮渡"),
        IconChoice("local_gas_station", "加油"),
        IconChoice("local_parking", "停车"),
        IconChoice("add_road", "高速"),
        IconChoice("shopping_cart", "购物"),
        IconChoice("shopping_bag", "箱包"),
        IconChoice("shopping_basket", "日用"),
        IconChoice("storefront", "店铺"),
        IconChoice("checkroom", "服饰"),
        IconChoice("ice_skating", "鞋靴"),
        IconChoice("devices", "数码"),
        IconChoice("diamond", "珠宝"),
        IconChoice("pets", "宠物"),
        IconChoice("watch", "钟表"),
        IconChoice("headphones", "耳机"),
        IconChoice("camera_alt", "摄影"),
        IconChoice("face", "美妆"),
        IconChoice("child_care", "母婴"),
        IconChoice("edit", "文具"),
        IconChoice("kitchen", "电器"),
        IconChoice("movie", "电影"),
        IconChoice("sports_esports", "游戏"),
        IconChoice("music_note", "音乐"),
        IconChoice("mic", "KTV"),
        IconChoice("sports_soccer", "运动"),
        IconChoice("casino", "娱乐"),
        IconChoice("celebration", "聚会"),
        IconChoice("theater_comedy", "演出"),
        IconChoice("nightlife", "夜宵夜生活"),
        IconChoice("card_membership", "会员"),
        IconChoice("luggage", "旅游"),
        IconChoice("museum", "展览"),
        IconChoice("groups", "聚会人群"),
        IconChoice("local_hospital", "医院挂号"),
        IconChoice("medication", "药品"),
        IconChoice("monitor_heart", "体检"),
        IconChoice("dentistry", "牙科"),
        IconChoice("visibility", "眼科"),
        IconChoice("fitness_center", "健身"),
        IconChoice("spa", "中医美容"),
        IconChoice("content_cut", "理发"),
        IconChoice("favorite", "健康"),
        IconChoice("vaccines", "疫苗"),
        IconChoice("school", "学费教育"),
        IconChoice("menu_book", "书籍"),
        IconChoice("psychology", "培训"),
        IconChoice("quiz", "考试"),
        IconChoice("cast_for_education", "网课"),
        IconChoice("brush", "学习"),
        IconChoice("palette", "爱好艺术"),
        IconChoice("home", "居住物业"),
        IconChoice("apartment", "房租"),
        IconChoice("chair", "家居"),
        IconChoice("weekend", "家具"),
        IconChoice("electrical_services", "水电"),
        IconChoice("water_drop", "水费"),
        IconChoice("local_fire_department", "燃气"),
        IconChoice("build", "维修"),
        IconChoice("lightbulb", "电"),
        IconChoice("ac_unit", "空调"),
        IconChoice("hotel", "酒店住院"),
        IconChoice("cleaning_services", "家政"),
        IconChoice("phone", "话费"),
        IconChoice("wifi", "宽带"),
        IconChoice("signal_cellular_alt", "流量"),
        IconChoice("computer", "电脑"),
        IconChoice("router", "月租网络"),
        IconChoice("swap_horiz", "转账"),
        IconChoice("card_giftcard", "红包"),
        IconChoice("payments", "代付月薪"),
        IconChoice("account_balance", "还款利息"),
        IconChoice("volunteer_activism", "捐赠补贴"),
        IconChoice("local_laundry_service", "洗衣"),
        IconChoice("dry_cleaning", "干洗"),
        IconChoice("park", "公园"),
        IconChoice("beach_access", "旅行海滩"),
        IconChoice("local_florist", "花"),
        IconChoice("more_horiz", "其它"),
        IconChoice("category", "类目"),
    )

    val incomeIcons: List<IconChoice> = listOf(
        IconChoice("attach_money", "其它收入"),
        IconChoice("payments", "月薪"),
        IconChoice("account_balance", "利息"),
        IconChoice("account_balance_wallet", "钱包"),
        IconChoice("trending_up", "基金"),
        IconChoice("show_chart", "股票"),
        IconChoice("savings", "年终储蓄"),
        IconChoice("paid", "奖金"),
        IconChoice("currency_yen", "日元"),
        IconChoice("work", "劳务兼职"),
        IconChoice("card_giftcard", "红包礼"),
        IconChoice("redeem", "兑换"),
        IconChoice("assignment_return", "购物退款"),
        IconChoice("replay", "其它退款"),
        IconChoice("pie_chart", "分红"),
        IconChoice("analytics", "收益"),
        IconChoice("groups", "合伙"),
        IconChoice("store", "生意"),
        IconChoice("local_atm", "取现"),
        IconChoice("credit_card", "信用卡"),
        IconChoice("request_quote", "报销"),
        IconChoice("emoji_events", "绩效奖杯"),
        IconChoice("star", "项目奖"),
        IconChoice("inventory", "货款"),
        IconChoice("sell", "出售"),
        IconChoice("point_of_sale", "收银"),
        IconChoice("qr_code", "收款码"),
        IconChoice("receipt_long", "账单入"),
        IconChoice("receipt", "票据"),
        IconChoice("edit_note", "稿费"),
        IconChoice("support_agent", "咨询"),
        IconChoice("weekend", "兼职"),
        IconChoice("apartment", "租金入"),
        IconChoice("category", "其它类"),
        IconChoice("more_horiz", "其它"),
    )

    fun allChoices(type: String): List<IconChoice> =
        if (type == "income") incomeIcons else expenseIcons

    /** 已被占用的图标名集合（一级 + 二级）。 */
    fun usedIconNames(primaryIcons: Collection<String>, subIcons: Collection<String> = emptyList()): Set<String> =
        (primaryIcons + subIcons).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * 为新分类选图标：优先 [preferred] 语义名，否则按 label 模糊匹配，再取第一个未占用。
     */
    fun nextFreeIcon(
        type: String,
        used: Set<String>,
        preferred: String? = null,
        hintName: String? = null,
    ): String {
        val pool = allChoices(type)
        fun free(n: String) = n !in used || n == "more_horiz"
        preferred?.trim()?.takeIf { it.isNotEmpty() }?.let { p ->
            if (pool.any { it.name == p } && free(p)) return p
            // 别名可能在 map 里
            if (free(p)) return p
        }
        val hint = hintName.orEmpty()
        if (hint.isNotEmpty()) {
            pool.firstOrNull { free(it.name) && (it.label.contains(hint) || hint.contains(it.label.take(2))) }
                ?.let { return it.name }
            // 关键词
            val kw = listOf(
                "餐" to "restaurant", "早" to "free_breakfast", "午" to "lunch_dining", "晚" to "dinner_dining",
                "咖啡" to "coffee", "奶茶" to "local_cafe", "水" to "nutrition", "果" to "nutrition",
                "公交" to "directions_bus", "地铁" to "directions_subway", "打车" to "local_taxi",
                "房租" to "apartment", "电" to "electrical_services", "药" to "medication",
                "电影" to "movie", "游戏" to "sports_esports", "工资" to "payments", "奖金" to "emoji_events",
                "股票" to "show_chart", "退" to "assignment_return", "红包" to "card_giftcard",
            )
            for ((k, ic) in kw) {
                if (hint.contains(k) && free(ic)) return ic
            }
        }
        return pool.firstOrNull { free(it.name) && it.name != "more_horiz" }?.name
            ?: pool.firstOrNull { free(it.name) }?.name
            ?: "more_horiz"
    }

    fun map(name: String): ImageVector = when (name) {
        "restaurant" -> Icons.Filled.Restaurant
        "local_cafe" -> Icons.Filled.LocalCafe
        "local_bar" -> Icons.Filled.LocalBar
        "fastfood" -> Icons.Filled.Fastfood
        "icecream" -> Icons.Filled.Icecream
        "cake" -> Icons.Filled.Cake
        "directions_car" -> Icons.Filled.DirectionsCar
        "directions_bus" -> Icons.Filled.DirectionsBus
        "directions_subway" -> Icons.Filled.DirectionsSubway
        "local_taxi" -> Icons.Filled.LocalTaxi
        "two_wheeler" -> Icons.Filled.TwoWheeler
        "flight" -> Icons.Filled.Flight
        "train" -> Icons.Filled.Train
        "directions_boat" -> Icons.Filled.DirectionsBoat
        "local_gas_station" -> Icons.Filled.LocalGasStation
        "directions_bike" -> Icons.Filled.DirectionsBike
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "shopping_bag" -> Icons.Filled.ShoppingBag
        "storefront" -> Icons.Filled.Storefront
        "checkroom" -> Icons.Filled.Checkroom
        "devices" -> Icons.Filled.Devices
        "diamond" -> Icons.Filled.Diamond
        "pets" -> Icons.Filled.Pets
        "watch" -> Icons.Filled.Watch
        "headphones" -> Icons.Filled.Headphones
        "camera_alt" -> Icons.Filled.CameraAlt
        "movie" -> Icons.Filled.Movie
        "sports_esports" -> Icons.Filled.SportsEsports
        "music_note" -> Icons.Filled.MusicNote
        "sports_soccer" -> Icons.Filled.SportsSoccer
        "casino" -> Icons.Filled.Casino
        "celebration" -> Icons.Filled.Celebration
        "theater_comedy" -> Icons.Filled.TheaterComedy
        "nightlife" -> Icons.Filled.Nightlife
        "local_hospital" -> Icons.Filled.LocalHospital
        "medication" -> Icons.Filled.Medication
        "fitness_center" -> Icons.Filled.FitnessCenter
        "spa" -> Icons.Filled.Spa
        "content_cut" -> Icons.Filled.ContentCut
        "favorite" -> Icons.Filled.Favorite
        "vaccines" -> Icons.Filled.Vaccines
        "school" -> Icons.Filled.School
        "menu_book" -> Icons.Filled.MenuBook
        "child_care" -> Icons.Filled.ChildCare
        "brush" -> Icons.Filled.Brush
        "palette" -> Icons.Filled.Palette
        "home" -> Icons.Filled.Home
        "apartment" -> Icons.Filled.Apartment
        "chair" -> Icons.Filled.Chair
        "electrical_services" -> Icons.Filled.ElectricalServices
        "water_drop" -> Icons.Filled.WaterDrop
        "build" -> Icons.Filled.Build
        "kitchen" -> Icons.Filled.Kitchen
        "lightbulb" -> Icons.Filled.Lightbulb
        "ac_unit" -> Icons.Filled.AcUnit
        "hotel" -> Icons.Filled.Hotel
        "phone" -> Icons.Filled.Phone
        "wifi" -> Icons.Filled.Wifi
        "computer" -> Icons.Filled.Computer
        "router" -> Icons.Filled.Router
        "swap_horiz" -> Icons.Filled.SwapHoriz
        "volunteer_activism" -> Icons.Filled.VolunteerActivism
        "local_laundry_service" -> Icons.Filled.LocalLaundryService
        "dry_cleaning" -> Icons.Filled.DryCleaning
        "park" -> Icons.Filled.Park
        "beach_access" -> Icons.Filled.BeachAccess
        "local_florist" -> Icons.Filled.LocalFlorist
        "attach_money" -> Icons.Filled.AttachMoney
        "payments" -> Icons.Filled.Payments
        "account_balance" -> Icons.Filled.AccountBalance
        "account_balance_wallet" -> Icons.Filled.AccountBalanceWallet
        "trending_up" -> Icons.Filled.TrendingUp
        "show_chart" -> Icons.Filled.ShowChart
        "savings" -> Icons.Filled.Savings
        "paid" -> Icons.Filled.Paid
        "currency_yen" -> Icons.Filled.CurrencyYen
        "work" -> Icons.Filled.Work
        "card_giftcard" -> Icons.Filled.CardGiftcard
        "redeem" -> Icons.Filled.Redeem
        "assignment_return" -> Icons.Filled.AssignmentReturn
        "replay" -> Icons.Filled.Replay
        "pie_chart" -> Icons.Filled.PieChart
        "analytics" -> Icons.Filled.Analytics
        "groups" -> Icons.Filled.Groups
        "store" -> Icons.Filled.Store
        "local_atm" -> Icons.Filled.LocalAtm
        "credit_card" -> Icons.Filled.CreditCard
        "request_quote" -> Icons.Filled.RequestQuote
        "emoji_events" -> Icons.Filled.EmojiEvents
        "star" -> Icons.Filled.Star
        "inventory" -> Icons.Filled.Inventory
        "sell" -> Icons.Filled.Sell
        "point_of_sale" -> Icons.Filled.PointOfSale
        "qr_code" -> Icons.Filled.QrCode
        "receipt_long" -> Icons.Filled.ReceiptLong
        "receipt" -> Icons.Filled.Receipt
        "category" -> Icons.Filled.Category
        "weekend" -> Icons.Filled.Weekend
        "edit" -> Icons.Filled.Edit
        // 二级语义别名（各映射唯一 Material 图标，避免水果=冰淇淋）
        "free_breakfast" -> Icons.Filled.LocalCafe
        "coffee" -> Icons.Filled.LocalCafe
        "cookie" -> Icons.Filled.Cake
        "nutrition" -> Icons.Filled.LocalFlorist
        "lunch_dining" -> Icons.Filled.Fastfood
        "dinner_dining" -> Icons.Filled.Restaurant
        "delivery_dining" -> Icons.Filled.Fastfood
        "soup_kitchen" -> Icons.Filled.Restaurant
        "set_meal" -> Icons.Filled.Fastfood
        "outdoor_grill" -> Icons.Filled.Restaurant
        "local_parking" -> Icons.Filled.DirectionsCar
        "add_road" -> Icons.Filled.DirectionsCar
        "pedal_bike" -> Icons.Filled.DirectionsBike
        "shopping_basket" -> Icons.Filled.ShoppingCart
        "face" -> Icons.Filled.Spa
        "ice_skating" -> Icons.Filled.Checkroom
        "card_membership" -> Icons.Filled.Star
        "luggage" -> Icons.Filled.Flight
        "mic" -> Icons.Filled.MusicNote
        "museum" -> Icons.Filled.Palette
        "monitor_heart" -> Icons.Filled.Favorite
        "dentistry" -> Icons.Filled.LocalHospital
        "visibility" -> Icons.Filled.LocalHospital
        "quiz" -> Icons.Filled.School
        "cast_for_education" -> Icons.Filled.School
        "edit_note" -> Icons.Filled.MenuBook
        "psychology" -> Icons.Filled.School
        "cleaning_services" -> Icons.Filled.Home
        "signal_cellular_alt" -> Icons.Filled.Phone
        "support_agent" -> Icons.Filled.Work
        "local_fire_department" -> Icons.Filled.LocalGasStation
        else -> Icons.Filled.MoreHoriz
    }
}

package com.simpleaccount.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyYen
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标名 → ImageVector。支出 / 收入各 30+ 互不相同，自定义分类也能一眼看懂。
 */
object IconMapper {

    data class IconChoice(val name: String, val label: String)

    /** 支出可选图标（≥30，互不重复） */
    val expenseIcons: List<IconChoice> = listOf(
        IconChoice("restaurant", "餐饮"),
        IconChoice("local_cafe", "咖啡"),
        IconChoice("local_bar", "酒水"),
        IconChoice("fastfood", "快餐"),
        IconChoice("icecream", "甜品"),
        IconChoice("cake", "蛋糕"),
        IconChoice("directions_car", "开车"),
        IconChoice("directions_bus", "公交"),
        IconChoice("directions_subway", "地铁"),
        IconChoice("local_taxi", "出租"),
        IconChoice("two_wheeler", "骑行"),
        IconChoice("flight", "飞机"),
        IconChoice("train", "火车"),
        IconChoice("directions_boat", "轮船"),
        IconChoice("local_gas_station", "加油"),
        IconChoice("directions_bike", "单车"),
        IconChoice("shopping_cart", "购物"),
        IconChoice("shopping_bag", "袋装"),
        IconChoice("storefront", "店铺"),
        IconChoice("checkroom", "服装"),
        IconChoice("devices", "数码"),
        IconChoice("diamond", "珠宝"),
        IconChoice("pets", "宠物"),
        IconChoice("watch", "钟表"),
        IconChoice("headphones", "耳机"),
        IconChoice("camera_alt", "摄影"),
        IconChoice("movie", "电影"),
        IconChoice("sports_esports", "游戏"),
        IconChoice("music_note", "音乐"),
        IconChoice("sports_soccer", "运动"),
        IconChoice("casino", "娱乐"),
        IconChoice("celebration", "聚会"),
        IconChoice("theater_comedy", "演出"),
        IconChoice("nightlife", "夜生活"),
        IconChoice("local_hospital", "医院"),
        IconChoice("medication", "药品"),
        IconChoice("fitness_center", "健身"),
        IconChoice("spa", "美容"),
        IconChoice("content_cut", "理发"),
        IconChoice("favorite", "健康"),
        IconChoice("vaccines", "疫苗"),
        IconChoice("school", "教育"),
        IconChoice("menu_book", "书籍"),
        IconChoice("child_care", "孩子"),
        IconChoice("brush", "学习"),
        IconChoice("palette", "爱好"),
        IconChoice("home", "居住"),
        IconChoice("apartment", "房租"),
        IconChoice("chair", "家具"),
        IconChoice("electrical_services", "电费"),
        IconChoice("water_drop", "水费"),
        IconChoice("build", "维修"),
        IconChoice("kitchen", "家电"),
        IconChoice("lightbulb", "电"),
        IconChoice("ac_unit", "空调"),
        IconChoice("hotel", "住宿"),
        IconChoice("phone", "话费"),
        IconChoice("wifi", "网费"),
        IconChoice("computer", "电脑"),
        IconChoice("router", "宽带"),
        IconChoice("swap_horiz", "转账"),
        IconChoice("volunteer_activism", "捐赠"),
        IconChoice("local_laundry_service", "洗衣"),
        IconChoice("dry_cleaning", "干洗"),
        IconChoice("park", "公园"),
        IconChoice("beach_access", "旅行"),
        IconChoice("local_florist", "花"),
        IconChoice("more_horiz", "其它"),
    )

    /** 收入可选图标（≥30，与支出不共用同一套观感） */
    val incomeIcons: List<IconChoice> = listOf(
        IconChoice("attach_money", "工资"),
        IconChoice("payments", "到账"),
        IconChoice("account_balance", "银行"),
        IconChoice("account_balance_wallet", "钱包"),
        IconChoice("trending_up", "投资"),
        IconChoice("show_chart", "理财"),
        IconChoice("savings", "存款"),
        IconChoice("paid", "报酬"),
        IconChoice("currency_yen", "现金"),
        IconChoice("work", "工作"),
        IconChoice("card_giftcard", "奖金"),
        IconChoice("redeem", "礼金"),
        IconChoice("assignment_return", "退款"),
        IconChoice("replay", "返还"),
        IconChoice("pie_chart", "分红"),
        IconChoice("analytics", "收益"),
        IconChoice("groups", "合伙"),
        IconChoice("store", "生意"),
        IconChoice("local_atm", "取现"),
        IconChoice("credit_card", "信用卡"),
        IconChoice("request_quote", "报销"),
        IconChoice("emoji_events", "奖金杯"),
        IconChoice("star", "奖励"),
        IconChoice("inventory", "货款"),
        IconChoice("sell", "出售"),
        IconChoice("point_of_sale", "收银"),
        IconChoice("qr_code", "收款码"),
        IconChoice("receipt_long", "账单入"),
        IconChoice("receipt", "票据"),
        IconChoice("category", "其它收入"),
        IconChoice("weekend", "兼职"),
        IconChoice("apartment", "租金入"),
    )

    fun allChoices(type: String): List<IconChoice> =
        if (type == "income") incomeIcons else expenseIcons

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
        // 二级类目扩容别名 → 已有 Material 图标
        "free_breakfast" -> Icons.Filled.LocalCafe
        "coffee" -> Icons.Filled.LocalCafe
        "cookie" -> Icons.Filled.Icecream
        "nutrition" -> Icons.Filled.Icecream
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

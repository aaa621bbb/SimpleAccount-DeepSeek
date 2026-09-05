package com.simpleaccount.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标名 → Compose ImageVector 的映射表。
 * 写死 when 映射，禁止反射；未知图标兜底 MoreHoriz。
 */
object IconMapper {

    fun map(name: String): ImageVector = when (name) {
        "restaurant" -> Icons.Filled.Restaurant
        "directions_car" -> Icons.Filled.DirectionsCar
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "movie" -> Icons.Filled.Movie
        "local_hospital" -> Icons.Filled.LocalHospital
        "school" -> Icons.Filled.School
        "home" -> Icons.Filled.Home
        "phone" -> Icons.Filled.Phone
        "attach_money" -> Icons.Filled.AttachMoney
        "card_giftcard" -> Icons.Filled.CardGiftcard
        "trending_up" -> Icons.Filled.TrendingUp
        "work" -> Icons.Filled.Work
        "swap_horiz" -> Icons.Filled.SwapHoriz
        "assignment_return" -> Icons.Filled.AssignmentReturn
        else -> Icons.Filled.MoreHoriz
    }
}

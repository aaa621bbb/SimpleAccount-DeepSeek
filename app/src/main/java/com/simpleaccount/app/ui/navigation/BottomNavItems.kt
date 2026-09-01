package com.simpleaccount.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

/** 底部导航项 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

object BottomNavItems {
    val items = listOf(
        BottomNavItem(Routes.HOME, "首页", Icons.Filled.Home),
        BottomNavItem(Routes.LEDGER, "账本", Icons.Filled.ReceiptLong),
        BottomNavItem(Routes.STATS, "统计", Icons.Filled.PieChart),
        BottomNavItem(Routes.AI, "AI管家", Icons.Filled.AutoAwesome),
        BottomNavItem(Routes.SETTINGS, "设置", Icons.Filled.Person),
    )
}

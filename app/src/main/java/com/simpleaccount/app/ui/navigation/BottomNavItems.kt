package com.simpleaccount.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
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
        BottomNavItem(Routes.LEDGER, "账本", Icons.Filled.List),
        BottomNavItem(Routes.STATS, "统计", Icons.Filled.PieChart),
        BottomNavItem(Routes.AI, "AI助手", Icons.Filled.SmartToy),
        BottomNavItem(Routes.SETTINGS, "我的", Icons.Filled.Settings),
    )
}

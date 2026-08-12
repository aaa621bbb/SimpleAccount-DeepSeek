package com.simpleaccount.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simpleaccount.app.ui.logs.LogScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simpleaccount.app.ui.addtransaction.AddTransactionScreen
import com.simpleaccount.app.ui.ai.AiScreen
import com.simpleaccount.app.ui.home.HomeScreen
import com.simpleaccount.app.ui.home.HomeViewModel
import com.simpleaccount.app.ui.importbill.ImportScreen
import com.simpleaccount.app.ui.ledger.LedgerScreen
import com.simpleaccount.app.ui.ledger.LedgerViewModel
import com.simpleaccount.app.ui.settings.AboutScreen
import com.simpleaccount.app.ui.settings.AiSettingsScreen
import com.simpleaccount.app.ui.settings.CategoryManageScreen
import com.simpleaccount.app.ui.settings.DataManageScreen
import com.simpleaccount.app.ui.settings.SettingsScreen
import com.simpleaccount.app.ui.stats.StatsScreen
import com.simpleaccount.app.ui.stats.StatsViewModel

@Composable
fun SimpleAccountNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isBottomBarVisible = currentDestination?.route in BottomNavItems.items.map { it.route }

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                SimpleBottomBar(
                    currentDestination = currentDestination,
                    navController = navController
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.HOME) {
                    val vm: HomeViewModel = hiltViewModel()
                    HomeScreen(vm, navController)
                }
                composable(Routes.LEDGER) {
                    val vm: LedgerViewModel = hiltViewModel()
                    LedgerScreen(vm, navController)
                }
                composable(Routes.STATS) {
                    val vm: StatsViewModel = hiltViewModel()
                    StatsScreen(vm)
                }
                composable(Routes.AI) {
                    AiScreen()
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(navController)
                }
                composable(Routes.ADD) {
                    AddTransactionScreen(navController, editId = null)
                }
                composable(
                    route = Routes.EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    AddTransactionScreen(navController, editId = id)
                }
                composable(Routes.IMPORT) {
                    ImportScreen(navController)
                }
                composable(Routes.AI_SETTINGS) {
                    AiSettingsScreen(navController)
                }
                composable(Routes.CATEGORY_MANAGE) {
                    CategoryManageScreen(navController)
                }
                composable(Routes.MERCHANT_MANAGE) {
                    com.simpleaccount.app.ui.merchant.MerchantManageScreen(navController)
                }
                composable(Routes.DATA_MANAGE) {
                    DataManageScreen(navController)
                }
                composable(Routes.LOGS) {
                    LogScreen(navController)
                }
                composable(Routes.ABOUT) {
                    AboutScreen(navController)
                }
            }
        }
    }
}

@Composable
private fun SimpleBottomBar(
    currentDestination: NavDestination?,
    navController: NavHostController,
) {
    NavigationBar {
        BottomNavItems.items.forEach { item ->
            val selected = currentDestination?.route == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

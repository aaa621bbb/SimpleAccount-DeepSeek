package com.simpleaccount.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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

    // AI 操控中枢：注册导航执行器（AI 的 navigate 工具可跳转任意页面），并上报当前路由
    val appControl: com.simpleaccount.app.data.agent.AppControlCenter =
        androidx.hilt.navigation.compose.hiltViewModel<com.simpleaccount.app.navigation.AppControlViewModel>()
            .appControl
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appControl.registerNavigator { route ->
            runCatching {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(currentDestination?.route) {
        currentDestination?.route?.let { appControl.reportRoute(it) }
    }

    Scaffold(
        // 关键：外层不再补系统栏 inset（各页面自己的 Scaffold/TopAppBar 会补一次）。
        // 之前外层+内层各补一次状态栏高度，顶部标题区域被撑到两倍高（edge-to-edge 后出现的"顶头大标题"根因）
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 键盘弹出时瞬时隐藏导航栏（不加动画）：
            // 若用滑出动画，动画期间"导航栏高度(渐减) + 键盘高度(渐增)"相加会出现一个超过最终位置的尖峰，
            // 表现为输入栏先蹿很高再落下来。瞬时移除后输入栏只跟随键盘单一运动。
            // inset 读取放在本 lambda 内，逐帧变化只重组底部栏，不牵动整棵导航树。
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            if (currentDestination?.route in BottomNavItems.items.map { it.route } && !imeVisible) {
                SimpleBottomBar(
                    currentDestination = currentDestination,
                    navController = navController
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            val reduce = com.simpleaccount.app.ui.motion.LocalReduceMotion.current
            val enterMs = com.simpleaccount.app.ui.motion.Motion.dur(reduce, com.simpleaccount.app.ui.motion.Motion.PAGE_MS)
            val exitMs = com.simpleaccount.app.ui.motion.Motion.dur(reduce, com.simpleaccount.app.ui.motion.Motion.PAGE_EXIT_MS)
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(tween(enterMs, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(enterMs, easing = FastOutSlowInEasing)) { it / 14 }
                },
                exitTransition = {
                    fadeOut(tween(exitMs, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    fadeIn(tween(enterMs, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    fadeOut(tween(exitMs, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(exitMs, easing = FastOutSlowInEasing)) { it / 14 }
                },
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
                    AiScreen(navController = navController, autoPickImage = false)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(navController)
                }
                composable(Routes.APPEARANCE) {
                    com.simpleaccount.app.ui.settings.AppearanceScreen(navController)
                }
                composable(Routes.STATS_LAYOUT) {
                    com.simpleaccount.app.ui.settings.StatsLayoutScreen(navController)
                }
                composable(Routes.PICKER_STYLE) {
                    com.simpleaccount.app.ui.settings.PickerStyleScreen(navController)
                }
                composable(Routes.MEMORY) {
                    com.simpleaccount.app.ui.settings.MemoryScreen(navController)
                }
                composable(Routes.AI_SHOT) {
                    AiScreen(navController = navController, autoPickImage = true)
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
                composable(Routes.ONDEVICE_MODELS) {
                    com.simpleaccount.app.ui.settings.OnDeviceModelScreen(navController)
                }
                composable(Routes.PROJECTION_DETAIL) {
                    com.simpleaccount.app.ui.home.ProjectionDetailScreen(navController)
                }
                composable(Routes.AUTO_RECORD) {
                    com.simpleaccount.app.ui.settings.AutoRecordScreen(navController)
                }
                composable(Routes.LEDGER_SCOPE) {
                    com.simpleaccount.app.ui.settings.LedgerScopeScreen(navController)
                }
                composable(Routes.LEDGER_MANAGE) {
                    com.simpleaccount.app.ui.settings.LedgerManageScreen(navController)
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = com.simpleaccount.app.ui.theme.LocalTokens.current.elevNav,
        tonalElevation = 0.dp,
    ) {
        NavigationBar(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
        ) {
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
}

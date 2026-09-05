package com.simpleaccount.app.data.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 操控中枢：AI 工具与 UI 层之间的桥。
 * - 导航：NavHost 组装时注册导航执行器，AI 的 navigate 工具即可跳转任意页面；
 * - 状态广播：记录当前所在页面、最近一次操作结果，供 AI 感知 App 状态。
 */
@Singleton
class AppControlCenter @Inject constructor() {

    /** 可导航的页面（与底部导航/子页面路由对齐） */
    val screens: Map<String, String> = mapOf(
        "首页" to "home", "账本" to "ledger", "统计" to "stats",
        "AI管家" to "ai", "设置" to "settings", "记一笔" to "add",
        "导入账单" to "import", "AI设置" to "ai_settings",
        "分类管理" to "category_manage", "商家归类管理" to "merchant_manage",
        "数据管理" to "data_manage", "日志" to "logs", "关于" to "about",
        "无感记账" to "auto_record",
        "账本管理" to "ledger_manage",
    )

    /** 导航执行器（由 NavHost 注册；AI 工具调用时执行） */
    private val _navigator = MutableStateFlow<((String) -> Unit)?>(null)
    val navigator: StateFlow<((String) -> Unit)?> = _navigator.asStateFlow()

    /** 当前页面路由 */
    private val _currentRoute = MutableStateFlow("home")
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    fun registerNavigator(navigator: ((String) -> Unit)?) {
        _navigator.value = navigator
    }

    fun reportRoute(route: String) {
        _currentRoute.value = route
    }

    /** AI 跳转页面：中文名或路由名都支持。返回结果描述 */
    fun navigate(nameOrRoute: String): String {
        val route = screens[nameOrRoute.trim()] ?: nameOrRoute.trim()
        val known = screens.values.contains(route) || route == "edit/"
        val nav = _navigator.value ?: return "导航失败：App 还没准备好（请回首页再试）。"
        return try {
            nav(route)
            "已跳转到「${screens.filterValues { it == route }.keys.firstOrNull() ?: route}」。"
        } catch (e: Exception) {
            "导航失败：${e.message}"
        }
    }
}

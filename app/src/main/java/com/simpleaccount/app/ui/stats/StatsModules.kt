package com.simpleaccount.app.ui.stats

/** 统计页可自定义的图表模块 */
object StatsModules {
    const val PIE = "pie"
    const val COMPARE = "compare"
    const val MERCHANTS = "merchants"
    const val CALENDAR = "calendar"
    const val TREND = "trend"
    const val BARS = "bars"
    const val WEEKDAY = "weekday"
    const val HOURS = "hours"

    val DEFAULT_ORDER = listOf(PIE, CALENDAR, BARS, WEEKDAY, HOURS, MERCHANTS, TREND, COMPARE)

    fun title(id: String): String = when (id) {
        PIE -> "分类构成（饼图）"
        COMPARE -> "对照数字（日均 / 环比）"
        MERCHANTS -> "商家排行"
        CALENDAR -> "每日花销日历"
        TREND -> "近 12 个月曲线"
        BARS -> "近 12 个月柱（收支对照）"
        WEEKDAY -> "星期分布"
        HOURS -> "时段分布"
        else -> id
    }

    fun parseOrder(raw: String?): List<String> {
        val parsed = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val known = parsed.filter { it in DEFAULT_ORDER }.distinct()
        return known + DEFAULT_ORDER.filter { it !in known }
    }

    fun parseHidden(raw: String?): Set<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it in DEFAULT_ORDER }.toSet()
}

data class StatsLayoutUi(
    val order: List<String> = StatsModules.DEFAULT_ORDER,
    val hidden: Set<String> = emptySet(),
    val pieLegendCount: Int = 3,
)

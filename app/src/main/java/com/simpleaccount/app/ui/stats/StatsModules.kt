package com.simpleaccount.app.ui.stats

/** 统计页可自定义的图表模块。新旧口径互斥，禁止再堆同质条形。 */
object StatsModules {
    const val PIE = "pie"
    const val COMPARE = "compare"
    const val MERCHANTS = "merchants"
    const val CALENDAR = "calendar"
    const val TREND = "trend"
    const val BARS = "bars"
    const val WEEKDAY = "weekday"
    const val HOURS = "hours"

    const val FREQ = "freq"
    const val SHARE = "share"
    const val MOM_DELTA = "mom_delta"
    const val CONC = "conc"
    const val ELASTIC = "elastic"
    const val PARETO = "pareto"
    const val HEAT = "heat"
    const val SPARK = "spark"
    const val FLOW = "flow"
    const val RADAR = "radar"

    val DEFAULT_ORDER = listOf(
        PIE, CALENDAR, BARS, WEEKDAY, HOURS, MERCHANTS, TREND, COMPARE,
        FREQ, SHARE, MOM_DELTA, CONC, ELASTIC, PARETO, HEAT, SPARK, FLOW, RADAR,
    )

    fun title(id: String): String = when (id) {
        PIE -> "分类构成（饼图）"
        COMPARE -> "对照数字（日均 / 环比）"
        MERCHANTS -> "商家排行"
        CALENDAR -> "每日花销日历"
        TREND -> "近 12 个月曲线"
        BARS -> "近 12 个月柱（收支对照）"
        WEEKDAY -> "星期分布"
        HOURS -> "时段分布"
        FREQ -> "频次曲线"
        SHARE -> "结构演进"
        MOM_DELTA -> "环比水位"
        CONC -> "商户集中度"
        ELASTIC -> "类目弹性"
        PARETO -> "帕累托累计"
        HEAT -> "星期×时段热力"
        SPARK -> "本月日火花"
        FLOW -> "收支流向"
        RADAR -> "结构雷达"
        else -> id
    }

    fun caption(id: String): String = when (id) {
        FREQ -> "每天记了几笔，不是花了多少。和金额曲线不是同一件事。"
        SHARE -> "近 6 个月前几类占比怎么挪，不是单月饼图。"
        MOM_DELTA -> "每个月相对上个月多花或少花多少。正负从零轴分开。"
        CONC -> "CR1/CR3 和洛伦兹曲线。看钱是不是集中在少数商家。"
        ELASTIC -> "各类目月环比对照总额环比。跟总盘走的靠近斜线。"
        PARETO -> "商家从大到小累计占比。看 80% 花在几家里。"
        HEAT -> "星期几 × 五个时段的金额格子。一维条形看不出交叉。"
        SPARK -> "本月每一天一条火花，日历是格子，这里是走势。"
        FLOW -> "左边收入来源，右边支出去向。不是收支对照柱。"
        RADAR -> "本月与上月前几类的形状对照，不是饼图切块。"
        else -> ""
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

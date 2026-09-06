package com.simpleaccount.app.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * 相对日期解析（今天/昨天/前天/本月…）。
 * 给 Agent 工具层、识图记账、系统提示词共用：
 * 模型即使原样传「昨天」，工具也能换成 yyyy-MM-dd，避免「日期未知」。
 */
object DateResolver {

    val PERIOD_WORDS = listOf("上午", "下午", "晚上", "早上", "早晨", "中午", "傍晚", "凌晨", "夜里", "夜间", "今晚")

    fun today(): LocalDate = LocalDate.now()

    /**
     * 「上午/下午/晚上/8点」→ HH:mm。没有钟点时给该时段的默认时刻，避免「时间未知」。
     */
    fun parseTimeText(raw: String?): String? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty() || s == "未知" || s == "时间未知" || s.equals("null", true)) return null

        Regex("""(\d{1,2})[:：点时](\d{1,2})?""").find(s)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h > 23 || min > 59) return@let
            val ctx = s.substring(0, m.range.first)
            if (h < 12 && (ctx.contains("下午") || ctx.contains("晚上") || ctx.contains("傍晚") ||
                    ctx.contains("夜里") || ctx.contains("夜间") || ctx.contains("今晚"))) {
                h += 12
            }
            if (h == 12 && (ctx.contains("凌晨") || ctx.contains("午夜"))) h = 0
            return "%02d:%02d".format(h.coerceIn(0, 23), min)
        }

        parseChineseHour(s)?.let { return it }
        return periodDefault(s)
    }

    fun periodDefault(s: String): String? = when {
        s.contains("凌晨") -> "02:00"
        s.contains("早上") || s.contains("早晨") -> "08:00"
        s.contains("上午") -> "09:00"
        s.contains("中午") -> "12:00"
        s.contains("下午") -> "15:00"
        s.contains("傍晚") -> "18:00"
        s.contains("晚上") || s.contains("夜里") || s.contains("夜间") || s.contains("今晚") -> "20:00"
        else -> null
    }

    private fun parseChineseHour(s: String): String? {
        val map = mapOf(
            "零" to 0, "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9,
            "十" to 10, "十一" to 11, "十二" to 12,
        )
        val m = Regex("""(十[一二]?|[零一二两三四五六七八九])点(半|(\d{1,2})分?)?""").find(s) ?: return null
        var h = map[m.groupValues[1]] ?: return null
        val min = when {
            m.groupValues[2] == "半" -> 30
            m.groupValues[3].isNotBlank() -> m.groupValues[3].toIntOrNull() ?: 0
            else -> 0
        }
        if (h < 12 && (s.contains("下午") || s.contains("晚上") || s.contains("傍晚") || s.contains("夜里"))) h += 12
        if (h == 12 && (s.contains("凌晨") || s.contains("午夜"))) h = 0
        return "%02d:%02d".format(h.coerceIn(0, 23), min.coerceIn(0, 59))
    }

    /** 注入到用户消息前的时间锚点，禁止模型回答「日期未知」 */
    fun anchorBlock(): String {
        val t = today()
        val yest = t.minusDays(1)
        val db = t.minusDays(2)
        val thisMonth = YearMonth.from(t)
        val lastMonth = thisMonth.minusMonths(1)
        val weekStart = t.minusDays((t.dayOfWeek.value - 1).toLong())
        val lastWeekStart = weekStart.minusDays(7)
        return """【时间锚点（必须使用这些具体日期，禁止回答「日期未知」）】
今天=$t（${t.year}年${t.monthValue}月${t.dayOfMonth}日）
昨天=$yest
前天=$db
本月=$thisMonth
上个月=$lastMonth
本周=$weekStart 至 ${weekStart.plusDays(6)}
上周=$lastWeekStart 至 ${lastWeekStart.plusDays(6)}"""
    }

    fun enrichUserMessage(msg: String): String {
        if (msg.contains("【时间锚点")) return msg
        return anchorBlock() + "\n用户说：" + msg
    }

    /**
     * 把任意日期说法换成 yyyy-MM-dd。
     * 无法识别返回 null（调用方再决定要不要回落到今天）。
     */
    fun resolveFlexible(raw: String?, now: LocalDate = today()): String? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        if (s == "未知" || s == "日期未知" || s == "时间未知" || s.equals("null", true) || s == "-" || s == "无" || s == "none") {
            return null
        }

        // 相对词：大前天必须在前天之前判断
        when {
            s.contains("大前天") -> return now.minusDays(3).toString()
            s.contains("前天") -> return now.minusDays(2).toString()
            s.contains("昨天") || s.contains("昨日") -> return now.minusDays(1).toString()
            s.contains("今天") || s.contains("今日") -> return now.toString()
            s.contains("明天") -> return now.plusDays(1).toString()
        }

        // 截图常把「上午/下午/晚上」写在日期列：没有年月日时按今天
        if (PERIOD_WORDS.any { s.contains(it) } && !s.contains("月") && Regex("\\d{4}").find(s) == null) {
            return now.toString()
        }

        // 2026-08-30 / 2026/8/30 / 2026年8月30日
        Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""").find(s)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            if (m in 1..12 && d in 1..31) {
                return runCatching { LocalDate.of(y, m, d).toString() }.getOrNull()
            }
        }
        // 紧凑 yyyyMMdd
        Regex("""^(\d{4})(\d{2})(\d{2})$""").find(s)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            if (m in 1..12 && d in 1..31) {
                return runCatching { LocalDate.of(y, m, d).toString() }.getOrNull()
            }
        }
        // 8月30日 / 08-30 / 8/30 → 今年
        Regex("""(\d{1,2})[月/\-.](\d{1,2})日?""").find(s)?.let {
            val m = it.groupValues[1].toInt()
            val d = it.groupValues[2].toInt()
            if (m in 1..12 && d in 1..31) {
                return runCatching { LocalDate.of(now.year, m, d).toString() }.getOrNull()
            }
        }
        return null
    }

    /** 月份参数：本月/上个月/yyyy-MM/yyyy年M月 → yyyy-MM */
    fun resolveMonth(raw: String?, now: LocalDate = today()): String? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        when {
            s.contains("上个月") || s == "上月" || s.contains("上月") ->
                return YearMonth.from(now).minusMonths(1).toString()
            s.contains("下个月") || s == "下月" || s.contains("下月") ->
                return YearMonth.from(now).plusMonths(1).toString()
            s.contains("本月") || s.contains("这个月") || s.contains("当月") ->
                return YearMonth.from(now).toString()
        }
        Regex("""(\d{4})[-/.年](\d{1,2})""").find(s)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (m in 1..12) return "%04d-%02d".format(y, m)
        }
        Regex("""^(\d{4})(\d{2})$""").find(s)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (m in 1..12) return "%04d-%02d".format(y, m)
        }
        return null
    }
}

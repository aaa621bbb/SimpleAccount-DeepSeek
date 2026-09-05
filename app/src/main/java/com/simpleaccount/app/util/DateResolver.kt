package com.simpleaccount.app.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * 相对日期解析（今天/昨天/前天/本月…）。
 * 给 Agent 工具层、识图记账、系统提示词共用：
 * 模型即使原样传「昨天」，工具也能换成 yyyy-MM-dd，避免「日期未知」。
 */
object DateResolver {

    fun today(): LocalDate = LocalDate.now()

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
        if (s == "未知" || s == "日期未知" || s.equals("null", true) || s == "-" || s == "无" || s == "none") {
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

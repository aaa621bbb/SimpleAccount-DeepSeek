package com.simpleaccount.app.util

import java.time.LocalDate

/**
 * 口语时间解析器（Agent 写路径专用，自研实现）。
 *
 * 与 [DateResolver] 的区别：本解析器**禁止凭空默认**——文本里没有时刻依据时，
 * [SpokenTime.time] 直接返回 null（调用方必须显式询问用户或留空落库），
 * 绝不回落到 "12:00"。
 *
 * 能解析的表达（均为原创规则实现，不依赖任何第三方 NLP）：
 * - 日期：今天/今日/今晚/今早、昨天/昨日/昨晚、 Strip、前天、大前天、明天，
 *   以及 yyyy-MM-dd、yyyy年M月d日、M月d日、MM-dd/MM/dd 等显式写法。
 * - 时刻：数字钟点（3点/15点/下午3点/8:15/晚上8点15/三点半/下午三点半）、
 *   时段词（凌晨/早上/上午/中午/下午/傍晚/晚上/夜里/夜间）。
 * - 组合：昨晚、前天中午、今天下午三点、昨天晚上8点 等。
 *
 * 约定：
 * - 只有钟点、没有日期的表达（如"下午三点"），日期按今天理解，但 [SpokenTime.dateExplicit]
 *   为 false，调用方可决定是否向用户确认。
 * - 只有日期、没有时刻的表达，time 为 null（未知），禁止默认。
 * - 光秃钟点（如"三点"，无上下午）视为歧义：[SpokenTime.timeAmbiguous] 为 true 且 time 为 null，
 *   调用方必须追问"是凌晨三点还是下午三点"，禁止猜一个落库。
 */
object SpokenTimeParser {

    data class SpokenTime(
        /** 日期 yyyy-MM-dd；无依据时为 null（必须询问，不得默认今天）。 */
        val date: String?,
        /** 时刻 HH:mm；无依据/歧义时为 null（必须询问或留空，不得默认 12:00）。 */
        val time: String?,
        /** 日期是否有明确文本依据（今天/昨天/前天/显式日期等）。 */
        val dateExplicit: Boolean,
        /** 时刻是否有明确文本依据（钟点或时段词）。 */
        val timeExplicit: Boolean,
        /** 时刻是否来自时段词的默认映射（如"上午"→09:00），而非精确钟点。 */
        val timeIsPeriodDefault: Boolean,
        /** 光秃钟点歧义（"三点"不知上下午）。 */
        val timeAmbiguous: Boolean,
    ) {
        /** 日期是否需要追问。 */
        fun needsDateAsk(): Boolean = date == null

        /** 时刻是否需要追问（未知或歧义）。 */
        fun needsTimeAsk(): Boolean = time == null
    }

    /** 中文数字 → 小时。 */
    private val CN_HOUR = mapOf(
        "零" to 0, "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9,
        "十" to 10, "十一" to 11, "十二" to 12,
    )

    /** 时段词 → 默认时刻。注意：这是"有依据的映射"而非凭空默认；无任何时段词时不触发。 */
    private val PERIOD_DEFAULT = listOf(
        "凌晨" to "02:00",
        "午夜" to "00:00",
        "早上" to "08:00",
        "早晨" to "08:00",
        "今早" to "08:00",
        "上午" to "09:00",
        "中午" to "12:00",
        "下午" to "15:00",
        "傍晚" to "18:00",
        "晚上" to "20:00",
        "晚间" to "20:00",
        "夜里" to "20:00",
        "夜间" to "20:00",
        "今晚" to "20:00",
        "昨晚" to "20:00",
    )

    private val PM_HINTS = listOf("下午", "晚上", "晚间", "傍晚", "夜里", "夜间", "今晚", "昨晚")
    private val AM_HINTS = listOf("上午", "早上", "早晨", "今早", "凌晨")

    fun parse(text: String?, now: LocalDate = LocalDate.now()): SpokenTime {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) {
            return SpokenTime(null, null, false, false, false, false)
        }

        // ---------- 日期 ----------
        var date: String? = null
        var dateExplicit = false
        when {
            s.contains("大前天") -> { date = now.minusDays(3).toString(); dateExplicit = true }
            s.contains("前天") -> { date = now.minusDays(2).toString(); dateExplicit = true }
            s.contains("昨天") || s.contains("昨日") || s.contains("昨晚") -> {
                date = now.minusDays(1).toString(); dateExplicit = true
            }
            s.contains("今天") || s.contains("今日") || s.contains("今晚") || s.contains("今早") -> {
                date = now.toString(); dateExplicit = true
            }
            s.contains("明天") -> { date = now.plusDays(1).toString(); dateExplicit = true }
            s.contains("后天") -> { date = now.plusDays(2).toString(); dateExplicit = true }
        }
        if (date == null) {
            // 显式日期：2026-09-07 / 2026/9/7 / 2026年9月7日
            Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""").find(s)?.let {
                val y = it.groupValues[1].toInt()
                val m = it.groupValues[2].toInt()
                val d = it.groupValues[3].toInt()
                if (m in 1..12 && d in 1..31) {
                    date = runCatching { LocalDate.of(y, m, d).toString() }.getOrNull()
                    if (date != null) dateExplicit = true
                }
            }
        }
        if (date == null) {
            // 裸日期：9月7日 / 09-07 / 9/7 → 今年
            Regex("""(\d{1,2})[月/\-.](\d{1,2})日?""").find(s)?.let {
                val m = it.groupValues[1].toInt()
                val d = it.groupValues[2].toInt()
                if (m in 1..12 && d in 1..31) {
                    date = runCatching { LocalDate.of(now.year, m, d).toString() }.getOrNull()
                    if (date != null) dateExplicit = true
                }
            }
        }

        // ---------- 时刻：先找精确钟点 ----------
        var time: String? = null
        var timeExplicit = false
        var periodDefault = false
        var ambiguous = false

        val hasPm = PM_HINTS.any { s.contains(it) }
        val hasAm = AM_HINTS.any { s.contains(it) }

        // 阿拉伯数字钟点：8:15 / 8点15 / 8点半 / 15点 / 下午3点
        val digitClock = Regex("""(\d{1,2})\s*[:：点时]\s*(\d{1,2})?\s*(分)?\s*(半)?""").find(s)
        if (digitClock != null) {
            var h = digitClock.groupValues[1].toIntOrNull()
            var min = digitClock.groupValues[2].toIntOrNull() ?: 0
            if (digitClock.groupValues[4] == "半") min = 30
            if (h != null && h in 0..23 && min in 0..59) {
                if (h < 12 && hasPm) h += 12
                if (h == 12 && s.contains("凌晨")) h = 0
                if (h in 1..12 && !hasPm && !hasAm && h <= 12 && digitClock.groupValues[1].length <= 2) {
                    // 光秃小钟点（如"三点"/"3点"无上下午）：歧义，不猜
                    // 但 24 小时制写法（13~23 点）是明确的
                    if (h <= 12) {
                        ambiguous = true
                    } else {
                        time = "%02d:%02d".format(h, min)
                        timeExplicit = true
                    }
                } else {
                    // 有上下午语境，或 0/13~23 的明确写法
                    if (h in 1..12 && !hasPm && !hasAm) {
                        ambiguous = true
                    } else {
                        time = "%02d:%02d".format(h.coerceIn(0, 23), min)
                        timeExplicit = true
                    }
                }
            }
        } else {
            // 中文数字钟点：三点 / 三点半 / 下午三点十分
            val cnClock = Regex("""(十[一二]?|[零一二两三四五六七八九])\s*点\s*(半|([零一二三四五六七八九\d]{1,3})\s*分?)?""").find(s)
            if (cnClock != null) {
                var h = CN_HOUR[cnClock.groupValues[1]]
                if (h != null) {
                    var min = 0
                    val tail = cnClock.groupValues[2]
                    when {
                        tail == "半" -> min = 30
                        tail.isNotBlank() -> {
                            min = cnClock.groupValues[3].toIntOrNull()
                                ?: chineseSmallNumber(cnClock.groupValues[3]) ?: 0
                        }
                    }
                    if (h < 12 && hasPm) h += 12
                    if (h == 12 && s.contains("凌晨")) h = 0
                    if (h in 1..12 && !hasPm && !hasAm) {
                        ambiguous = true
                    } else {
                        time = "%02d:%02d".format(h.coerceIn(0, 23), min.coerceIn(0, 59))
                        timeExplicit = true
                    }
                }
            }
        }

        // 无精确钟点 → 时段词默认映射（有依据才映射，无词不映射）
        if (time == null && !ambiguous) {
            for ((word, mapped) in PERIOD_DEFAULT) {
                if (s.contains(word)) {
                    time = mapped
                    timeExplicit = true
                    periodDefault = true
                    break
                }
            }
        }

        // 只有时刻、没有日期 → 日期按今天理解（非显式，调用方可确认）
        if (date == null && time != null) {
            date = now.toString()
            dateExplicit = false
        }

        return SpokenTime(
            date = date,
            time = time,
            dateExplicit = dateExplicit,
            timeExplicit = timeExplicit,
            timeIsPeriodDefault = periodDefault,
            timeAmbiguous = ambiguous,
        )
    }

    /** 追问话术：时刻未知/歧义时由 Agent 逐字或改写使用。 */
    fun timeAskHint(spoken: SpokenTime): String = when {
        spoken.timeAmbiguous -> "是凌晨三点还是下午三点？"
        spoken.time == null -> "具体是几点？"
        else -> ""
    }

    private fun chineseSmallNumber(s: String): Int? {
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        // 十分/廿等口语分钟
        if (s == "半") return 30
        val d = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (s == "十") return 10
        if (s.length == 1) return d[s[0]]
        if (s.startsWith("十") && s.length == 2) return 10 + (d[s[1]] ?: return null)
        if (s.length == 2 && s[1] == '十') return (d[s[0]] ?: return null) * 10
        if (s.length == 3 && s[1] == '十') {
            return (d[s[0]] ?: return null) * 10 + (d[s[2]] ?: return null)
        }
        return null
    }
}

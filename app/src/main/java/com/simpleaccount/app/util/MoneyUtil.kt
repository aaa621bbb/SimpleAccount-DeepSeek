package com.simpleaccount.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * 金额工具。所有金额以"分"(Long) 存储/计算，展示时转元.分。
 */
object MoneyUtil {

    private val df = DecimalFormat("0.00")
    /** 单笔上限 1 千万元，挡住 OCR/粘贴把科学计数法或超长数字写进账本 */
    private val MAX_YUAN = BigDecimal("10000000")

    /** 用户输入金额字符串 → 分。去除 ¥/,/空格/元/引号后转。非法返回 null。 */
    fun parseToFen(input: String): Long? {
        val cleaned = input.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
            .replace("元", "")
            .replace("\"", "")
            .replace("'", "")
        if (cleaned.isEmpty()) return null
        val bd = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (bd.signum() < 0) return null
        if (bd > MAX_YUAN) return null
        return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * 中文数字金额 → 分。认「五十元 / 十五块 / 二十」。
     * 超范围或无法解析返回 null。
     */
    fun parseChineseToFen(input: String): Long? {
        val t = input.trim()
            .replace("块钱", "")
            .replace("块", "")
            .replace("元", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace(" ", "")
        if (t.isEmpty()) return null
        parseToFen(t)?.let { return it }
        val n = chineseToInt(t) ?: return null
        if (n <= 0 || n > 10_000_000) return null
        return n * 100L
    }

    private fun chineseToInt(s: String): Int? {
        val d = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (s == "十") return 10
        if (s.length == 1) return d[s[0]]
        if (s.endsWith("十") && s.length == 2) {
            val a = d[s[0]] ?: return null
            return a * 10
        }
        if (s.startsWith("十") && s.length == 2) {
            val a = d[s[1]] ?: return null
            return 10 + a
        }
        if (s.length == 3 && s[1] == '十') {
            val a = d[s[0]] ?: return null
            val b = d[s[2]] ?: return null
            return a * 10 + b
        }
        if (s == "一百" || s == "百") return 100
        return null
    }

    /** 分 → "12.34"（两位小数，不四舍五入成整数） */
    fun fenToYuan(amount: Long): String {
        return df.format(amount / 100.0)
    }

    /** 分 → "12.34" 元（含符号由调用方决定） */
    fun fenToYuanWithSymbol(amount: Long): String = fenToYuan(amount)
}

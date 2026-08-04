package com.simpleaccount.app.util

import java.text.DecimalFormat

/**
 * 金额工具。所有金额以"分"(Long) 存储/计算，展示时转元.分。
 */
object MoneyUtil {

    private val df = DecimalFormat("0.00")

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
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0) return null
        return Math.round(value * 100)
    }

    /** 分 → "12.34"（两位小数，不四舍五入成整数） */
    fun fenToYuan(amount: Long): String {
        return df.format(amount / 100.0)
    }

    /** 分 → "12.34" 元（含符号由调用方决定） */
    fun fenToYuanWithSymbol(amount: Long): String = fenToYuan(amount)
}

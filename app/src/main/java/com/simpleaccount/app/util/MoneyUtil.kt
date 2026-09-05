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

    /** 分 → "12.34"（两位小数，不四舍五入成整数） */
    fun fenToYuan(amount: Long): String {
        return df.format(amount / 100.0)
    }

    /** 分 → "12.34" 元（含符号由调用方决定） */
    fun fenToYuanWithSymbol(amount: Long): String = fenToYuan(amount)
}

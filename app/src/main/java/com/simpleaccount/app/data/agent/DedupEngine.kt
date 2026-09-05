package com.simpleaccount.app.data.agent

import com.simpleaccount.app.util.MerchantMatcher

/**
 * 智能去重。识图/无感记账「记重」的根因多半是商家写法不一致，
 * 所以判重比商家归类稍宽一档，但仍拒绝「中国」这种短前缀。
 *
 * 判重：
 * 1. 金额必须一致；
 * 2. 日期同一天（不再放宽到 ±2 天，避免把隔天的两杯咖啡并成一笔）；
 * 3. 商家：同一家，或一方为空；
 * 4. 两边都有时间且相差 >2 分钟 → 不是同一笔。
 */
object DedupEngine {

    fun normalizeMerchant(name: String): String = MerchantMatcher.normalize(name)

    fun merchantMatches(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return true
        if (MerchantMatcher.isSameMerchant(a, b)) return true
        val na = MerchantMatcher.normalize(a)
        val nb = MerchantMatcher.normalize(b)
        if (na.length < 4 || nb.length < 4) return false
        val short = if (na.length <= nb.length) na else nb
        val long = if (na.length <= nb.length) nb else na
        // 瑞幸 / 瑞幸咖啡：短名 ≥4 且是长名前缀，且长名只多了「咖啡/店/超市」这类后缀
        if (long.startsWith(short) && (long.length - short.length) <= 4) return true
        return false
    }

    fun dateMatches(a: String, b: String): Boolean = a == b

    fun isSameTx(
        dateA: String, amountA: Long, merchantA: String, timeA: String,
        dateB: String, amountB: Long, merchantB: String, timeB: String,
    ): Boolean {
        if (amountA != amountB || amountA <= 0) return false
        if (!dateMatches(dateA, dateB)) return false
        if (!merchantMatches(merchantA, merchantB)) return false
        if (timeA.isNotBlank() && timeB.isNotBlank()) {
            val da = minutes(timeA) ?: return true
            val db = minutes(timeB) ?: return true
            if (kotlin.math.abs(da - db) > 2) return false
        }
        return true
    }

    private fun minutes(hhmm: String): Int? {
        val p = hhmm.split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
}

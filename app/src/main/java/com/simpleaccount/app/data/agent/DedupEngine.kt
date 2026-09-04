package com.simpleaccount.app.data.agent

import com.simpleaccount.app.util.MerchantMatcher

/**
 * 智能去重引擎。
 *
 * 判重规则：
 * 1. 金额必须一致（分）；
 * 2. 日期：相等，或相差 ≤2 天；
 * 3. 商家：走 MerchantMatcher（截断名 vs 全称算同一家，短前缀不算）；
 * 4. 时间：两边都有且不一致 → 不同笔。
 */
object DedupEngine {

    fun normalizeMerchant(name: String): String = MerchantMatcher.normalize(name)

    fun merchantMatches(a: String, b: String): Boolean = MerchantMatcher.isSameMerchant(a, b)

    fun dateMatches(a: String, b: String): Boolean {
        if (a == b) return true
        val da = parseDate(a) ?: return false
        val db = parseDate(b) ?: return false
        return kotlin.math.abs(da - db) <= 2L
    }

    private fun parseDate(s: String): Long? {
        return try {
            java.time.LocalDate.parse(s).toEpochDay()
        } catch (_: Exception) { null }
    }

    fun isSameTx(
        dateA: String, amountA: Long, merchantA: String, timeA: String,
        dateB: String, amountB: Long, merchantB: String, timeB: String,
    ): Boolean {
        if (amountA != amountB || amountA <= 0) return false
        if (!dateMatches(dateA, dateB)) return false
        if (!merchantMatches(merchantA, merchantB)) return false
        if (timeA.isNotBlank() && timeB.isNotBlank() && timeA != timeB) return false
        return true
    }
}

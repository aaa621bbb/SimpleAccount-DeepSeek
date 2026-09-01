package com.simpleaccount.app.data.agent

/**
 * 智能去重引擎（解决"全等才判重"导致的重复记/漏记）：
 *
 * 判重规则（防重优先 + 防漏平衡）：
 * 1. 金额必须一致（分）——最可靠的主键；
 * 2. 日期：相等，或相差 ≤2 天（截图/手动/AI 记录与账单文件日期常对不上；
 *    同渠道截图与账单日期应一致，跨渠道允许 2 天容差）；
 * 3. 商家：归一化后相等，或一方包含另一方（≥2字），或剔除尾缀后相等；
 * 4. 时间（HH:mm）：两边都有时间时，不一致 → 判为不同笔（同一天同商家同金额时，
 *    分钟是唯一区分依据，必须用它区分）；至少一边没时间 → 不因缺时间误判。
 */
object DedupEngine {

    /** 商家名归一化：去空白/全角空格/小写/剔除括号内容与常见尾缀 */
    fun normalizeMerchant(name: String): String {
        var n = name.trim()
            .replace(" ", "")
            .replace("\u3000", "")
            .lowercase()
        n = n.replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("（[^）]*）"), "")
        n = n.replace(Regex("(门店|旗舰店|分店|广场店|超级市场|便利店|食品店|小卖部|档口|摊位|铺)$"), "")
        return n.trim()
    }

    /** 商家模糊匹配：归一化相等 或 一方包含另一方（短名≥2字） */
    fun merchantMatches(a: String, b: String): Boolean {
        val na = normalizeMerchant(a)
        val nb = normalizeMerchant(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        if (na.length < 2 || nb.length < 2) return false
        return na.contains(nb) || nb.contains(na)
    }

    /** 日期容错：相等或相差 ≤2 天（跨渠道记录日期对不齐） */
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

    /**
     * 核心判重：
     * 1. 金额必须一致（分）；
     * 2. 日期：相等，或相差 ≤2 天（跨渠道记录日期对不齐）；
     * 3. 商家：归一化后相等，或一方包含另一方（≥2字）；
     * 4. 时间（HH:mm）：
     *    - 两边都有时间：不一致 → 判为不同笔（同一天同商家同金额时，分钟是唯一区分依据）；
     *    - 至少一边没时间：不因缺时间误判，走 1-3 的宽松匹配。
     */
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

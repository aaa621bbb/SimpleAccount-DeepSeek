package com.simpleaccount.app.util

/**
 * 商家名智能匹配。
 *
 * 目标：截图截断（「藤县城乡公交汽车有限…」）和全称视为同一商户；
 * 但又不能太糊——「瑞幸」≠「瑞幸咖啡旁边的便利店」，「中国」≠「中国移动」。
 *
 * 规则（从严到宽）：
 * 1. 去空白/括号/省略号后全等；
 * 2. 一方带省略号，且是另一方前缀（短名 ≥4 字）；
 * 3. 短名以未写完的公司尾缀结尾（有限/股份有/…）且是长名前缀（≥6 字）；
 * 4. 去掉「有限公司/股份有限公司」等公司尾缀后全等（剩余 ≥4 字）；
 * 5. 不做短前缀包含（避免 中国 vs 中国移动）。
 */
object MerchantMatcher {

    private val ELLIPSIS = listOf("......", "……", "...", "…", "。。。")

    /** 未写完的公司尾缀：截图经常把「有限公司」截成「有限」 */
    private val INCOMPLETE_SUFFIX = listOf(
        "有限责任", "有限公", "有限", "股份有限", "股份有", "股份", "集团有", "集团"
    )

    private val COMPANY_SUFFIX =
        Regex("(股份有限责任公司|股份有限公司|有限责任公司|集团有限公司|集团公司|有限公司|集团|公司)$")

    fun hasEllipsis(name: String): Boolean =
        name.contains("...") || name.contains("…") || name.contains("......") || name.contains("……")

    fun stripEllipsis(name: String): String {
        var n = name.trim()
        var changed = true
        while (changed) {
            changed = false
            for (e in ELLIPSIS) {
                if (n.endsWith(e)) {
                    n = n.removeSuffix(e).trim()
                    changed = true
                }
            }
        }
        return n
    }

    fun normalize(name: String): String {
        var n = stripEllipsis(name)
            .replace(" ", "")
            .replace("\u3000", "")
            .replace("·", "")
            .replace("•", "")
            .lowercase()
        n = n.replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""（[^）]*）"""), "")
        return n.trim()
    }

    fun isSameMerchant(a: String, b: String): Boolean {
        val aEll = hasEllipsis(a)
        val bEll = hasEllipsis(b)
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true

        val short = if (na.length <= nb.length) na else nb
        val long = if (na.length <= nb.length) nb else na
        val shortHadEllipsis = if (na.length <= nb.length) aEll else bEll

        if (long.startsWith(short)) {
            // 截图截断：带省略号的短名是长名前缀
            if (shortHadEllipsis && short.length >= 4) return true
            // 「…有限」这种没写完的公司名
            if (INCOMPLETE_SUFFIX.any { short.endsWith(it) } && short.length >= 6) return true
            // 很长的前缀（≥10 字）几乎不可能是两家不同的店
            if (short.length >= 10) return true
        }

        val sa = COMPANY_SUFFIX.replace(na, "")
        val sb = COMPANY_SUFFIX.replace(nb, "")
        if (sa.length >= 4 && sa == sb) return true

        return false
    }

    /** 选更完整的名字：不要省略号、更长的优先 */
    fun preferCanonical(a: String, b: String): String {
        val ac = stripEllipsis(a).trim()
        val bc = stripEllipsis(b).trim()
        if (hasEllipsis(a) && !hasEllipsis(b)) return bc.ifEmpty { ac }
        if (hasEllipsis(b) && !hasEllipsis(a)) return ac.ifEmpty { bc }
        return if (ac.length >= bc.length) ac else bc
    }

    /**
     * 在已有商家列表里找规范名。
     * 命中则返回更完整的那个；否则返回去掉省略号后的原名。
     */
    fun canonicalize(name: String, existing: List<String>): String {
        val cleaned = stripEllipsis(name).trim()
        if (cleaned.isEmpty()) return cleaned
        val match = existing.firstOrNull { it.isNotBlank() && isSameMerchant(it, cleaned) }
        return if (match == null) cleaned else preferCanonical(match, cleaned)
    }
}

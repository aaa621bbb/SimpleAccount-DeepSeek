package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.KeywordRules
import com.simpleaccount.app.util.MoneyUtil
import com.simpleaccount.app.util.SpokenTimeParser
import java.time.LocalDate

/**
 * 口语记账语义解析器（自研实现，零第三方依赖）。
 *
 * 替代旧"模板匹配"式规则记账：旧实现会把
 * "今天在食堂吃了15块钱晚饭，帮我记一下" 整句误判为商家名。
 * 本解析器做有序多遍抽取：
 *
 * 1. 金额（含中文数字、"三块五"、"¥15"）：定位金额跨度，先摘走；
 * 2. 时间（[SpokenTimeParser]）：日期/时刻独立抽取，无依据不默认；
 * 3. 意图动词剥离（记/帮我记/记一下/入账…）；
 * 4. 商家：在/去/到/给/从 + 名词；已知商家最长匹配；做"店/馆/厅…"后缀判定；
 * 5. 商品/备注：吃了/买了/点了 + 名词、餐点词（早餐/午饭/晚饭/夜宵/奶茶…）、金额后名词；
 * 6. 分类/二级分类：关键词规则 + 餐点映射，二级分类走 [KeywordRules.classifySub]。
 *
 * 商家与商品互斥判定：动宾结构的宾语先判商家后缀（如"食堂/饭店/超市"），
 * 否则归商品，杜绝整句当商家。
 */
object UtteranceParser {

    data class ParsedUtterance(
        val amountFen: Long?,
        val merchant: String,
        val product: String,
        val note: String,
        val type: String,
        /** 日期 yyyy-MM-dd；null = 无依据（调用方必须询问）。 */
        val date: String?,
        /** 时刻 HH:mm；null = 无依据/歧义（调用方必须询问或留空）。 */
        val time: String?,
        val dateExplicit: Boolean,
        val timeExplicit: Boolean,
        val timeIsPeriodDefault: Boolean,
        val timeAmbiguous: Boolean,
        /** 建议一级分类（已在 validCategories 内校验，无把握为 null）。 */
        val category: String?,
        /** 建议二级分类（无把握为 null）。 */
        val subCategory: String?,
        /** 0..1：金额/商家/商品/时间的完整度。 */
        val confidence: Float,
        /** 需要追问的槽位：amount/date/time。 */
        val needAsk: List<String>,
    )

    /** 意图动词：记账请求里的功能词，抽取实体前剥离。 */
    private val INTENT_VERBS = listOf(
        "帮我记一下", "帮我记一笔", "帮我记上", "帮我记", "帮我入账",
        "记一笔", "记一下", "记上", "记账", "入账", "记",
    )

    /** 介词引导的商家："在食堂吃了…"。 */
    private val MERCHANT_PREP = Regex(
        """(?:在|去|到|给|从)\s*([\u4e00-\u9fa5A-Za-z0-9·・\-]{1,16}?)""" +
            """(?=吃了|买了|点了|喝了|订了|充了|交了|花了|坐了|看了|消费|结账|买单|付款|给了|[\s，。,．！？]|帮我记|记一笔|记一下|记上|入账|$)"""
    )

    /** 动宾结构：吃了晚饭/买了咖啡/点了外卖。 */
    private val VERB_OBJECT = Regex(
        """(?:吃了|买了|点了|喝了|订了|充了|交了|花了|坐了|看了|买|吃|点|喝)\s*""" +
            """([\u4e00-\u9fa5A-Za-z0-9·・]{1,12})"""
    )

    /** 金额后紧跟的名词：15块钱晚饭/20元打车。 */
    private val AFTER_AMOUNT_NOUN = Regex(
        """(?:元|块钱|块|¥|￥)\s*([\u4e00-\u9fa5]{1,8}?)(?=[，。,．！？]|帮我|记|入账|$)"""
    )

    /** 商家后缀：命中则优先判商家。 */
    private val MERCHANT_SUFFIX = listOf(
        "食堂", "饭店", "餐厅", "饭馆", "酒店", "宾馆", "超市", "商场", "商店", "便利店",
        "咖啡馆", "咖啡店", "奶茶店", "饮品店", "面包店", "蛋糕店", "水果店", "药店",
        "医院", "诊所", "银行", "加油站", "停车场", "地铁站", "火车站", "机场",
        "公司", "店", "馆", "厅", "城", "站", "行", "院", "校", "吧", "所", "场", "中心",
    )

    /** 餐点词 → 商品；同时映射二级分类。 */
    private val MEAL_WORDS = listOf(
        "早餐", "早饭", "午餐", "午饭", "晚餐", "晚饭", "夜宵",
        "下午茶", "奶茶", "咖啡", "零食", "水果", "外卖", "便当", "盒饭",
    )

    /** 不可能是商家的垃圾词（功能词残留）。 */
    private val MERCHANT_STOP = setOf(
        "一笔", "一下", "支出", "收入", "账", "账单", "这里", "那里", "这个", "那个",
        "东西", "钱", "块钱", "", "我", "你",
    )

    private val INCOME_HINTS = listOf("收入", "工资", "发工资", "到账", "入账", "收了", "收到", "赚了", "奖金", "报销到", "退款到")
    private val REFUND_HINTS = listOf("退款", "退钱", "退回")

    fun parse(
        raw: String,
        validCategories: Set<String>,
        knownMerchants: List<String> = emptyList(),
        now: LocalDate = LocalDate.now(),
    ): ParsedUtterance {
        val s = raw.trim()

        // ---- 1. 时间 ----
        val spoken = SpokenTimeParser.parse(s, now)

        // ---- 2. 金额（记录跨度，后续摘除） ----
        val (amountFen, amountSpan) = extractAmount(s)

        // ---- 3. 收支类型 ----
        val type = when {
            INCOME_HINTS.any { s.contains(it) } -> Transaction.TYPE_INCOME
            REFUND_HINTS.any { s.contains(it) } -> Transaction.TYPE_INCOME
            else -> Transaction.TYPE_EXPENSE
        }

        // ---- 4. 工作串：摘除金额/意图动词/日期时间词 ----
        var work = s
        if (amountSpan != null) {
            work = (work.substring(0, amountSpan.first) + " " + work.substring(amountSpan.second)).trim()
        }
        INTENT_VERBS.sortedByDescending { it.length }.forEach { v ->
            work = work.replace(v, " ")
        }
        // 摘除日期时间词，避免污染商家/商品
        listOf(
            "大前天", "前天", "昨天", "昨日", "今天", "今日", "明天", "后天",
            "昨晚", "今晚", "今早", "凌晨", "早上", "早晨", "上午", "中午",
            "下午", "傍晚", "晚上", "晚间", "夜里", "夜间",
        ).forEach { w -> work = work.replace(w, " ") }
        work = work.replace(Regex("""\d{1,2}\s*[:：点时]\s*\d{0,2}\s*(分|半)?"""), " ")
        work = work.replace(Regex("""(十[一二]?|[零一二两三四五六七八九])\s*点\s*(半)?"""), " ")
        work = work.replace(Regex("""[，。,．！？!?\s]+"""), " ").trim()

        // ---- 5. 商家 ----
        var merchant = ""
        // 5a. 介词引导
        MERCHANT_PREP.find(work)?.let { m ->
            val cand = cleanNoun(m.groupValues[1])
            if (cand.isNotBlank() && cand !in MERCHANT_STOP) merchant = cand
        }
        // 5b. 已知商家最长匹配
        if (merchant.isEmpty() && knownMerchants.isNotEmpty()) {
            val hit = knownMerchants.filter { it.isNotBlank() && work.contains(it) }
                .maxByOrNull { it.length }
            if (hit != null) merchant = hit
        }
        // 5c. 动宾宾语 + 商家后缀判定
        if (merchant.isEmpty()) {
            VERB_OBJECT.find(work)?.let { m ->
                val cand = cleanNoun(m.groupValues[1])
                if (cand.isNotBlank() && MERCHANT_SUFFIX.any { cand.endsWith(it) } && cand !in MERCHANT_STOP) {
                    merchant = cand
                }
            }
        }

        // ---- 6. 商品/备注 ----
        var product = ""
        // 6a. 餐点词优先
        MEAL_WORDS.firstOrNull { s.contains(it) }?.let { product = it }
        // 6b. 金额后名词
        if (product.isEmpty()) {
            AFTER_AMOUNT_NOUN.find(s)?.let { m ->
                val cand = cleanNoun(m.groupValues[1])
                if (cand.isNotBlank() && cand != merchant) product = cand
            }
        }
        // 6c. 动宾宾语（非商家后缀的归商品）
        if (product.isEmpty()) {
            VERB_OBJECT.find(work)?.let { m ->
                val cand = cleanNoun(m.groupValues[1])
                if (cand.isNotBlank() && cand != merchant &&
                    MERCHANT_SUFFIX.none { cand.endsWith(it) } && cand !in MERCHANT_STOP
                ) {
                    product = cand
                }
            }
        }
        // 互斥：商品命中商家名则清空一边
        if (product.isNotBlank() && product == merchant) product = ""

        // ---- 7. 分类 ----
        val haystack = "$merchant $product"
        var category = KeywordRules.classify(haystack)?.takeIf { it in validCategories }
        if (category == null && MEAL_WORDS.any { product == it || merchant.contains(it) }) {
            category = "餐饮".takeIf { it in validCategories }
        }
        if (category == null && type == Transaction.TYPE_INCOME && REFUND_HINTS.any { s.contains(it) }) {
            category = "退款".takeIf { it in validCategories }
        }
        val subCategory = KeywordRules.classifySub("$merchant $product $s", category)

        // ---- 8. 置信度与追问槽位 ----
        var conf = 0.35f
        if (amountFen != null) conf += 0.2f
        if (merchant.isNotBlank()) conf += 0.15f
        if (product.isNotBlank()) conf += 0.1f
        if (spoken.date != null) conf += 0.1f
        if (spoken.time != null) conf += 0.1f
        val needAsk = mutableListOf<String>()
        if (amountFen == null) needAsk += "amount"
        if (spoken.needsDateAsk()) needAsk += "date"
        if (spoken.needsTimeAsk()) needAsk += "time"

        return ParsedUtterance(
            amountFen = amountFen,
            merchant = merchant,
            product = product,
            note = "",
            type = type,
            date = spoken.date,
            time = spoken.time,
            dateExplicit = spoken.dateExplicit,
            timeExplicit = spoken.timeExplicit,
            timeIsPeriodDefault = spoken.timeIsPeriodDefault,
            timeAmbiguous = spoken.timeAmbiguous,
            category = category,
            subCategory = subCategory,
            confidence = conf.coerceIn(0f, 1f),
            needAsk = needAsk,
        )
    }

    // ---------------- 金额 ----------------

    private fun extractAmount(s: String): Pair<Long?, IntRange?> {
        // ¥15.5 / ￥15
        Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""").find(s)?.let { m ->
            MoneyUtil.parseToFen(m.groupValues[1])?.let { return it to m.range }
        }
        // 15元 / 15块钱 / 15.5块
        Regex("""(\d+(?:\.\d{1,2})?)\s*(?:元|块钱|块)""").find(s)?.let { m ->
            MoneyUtil.parseToFen(m.groupValues[1])?.let { return it to m.range }
        }
        // 中文数字金额：五十元 / 十五块 / 三块五 / 一百二十块
        Regex("""([零一二两三四五六七八九十百千万\d\.点]+)\s*(?:元|块钱|块)""").find(s)?.let { m ->
            parseChineseMoney(m.groupValues[1])?.let { return it to m.range }
            MoneyUtil.parseChineseToFen(m.groupValues[0])?.let { return it to m.range }
        }
        // 三块五（无"元"字兜底）
        Regex("""([零一二两三四五六七八九\d]+)\s*块\s*([零一二三四五六七八九\d])\b""").find(s)?.let { m ->
            val yuan = chineseInt(m.groupValues[1]) ?: m.groupValues[1].toIntOrNull()
            val jiao = chineseInt(m.groupValues[2]) ?: m.groupValues[2].toIntOrNull()
            if (yuan != null && jiao != null && yuan in 0..10_000_000) {
                return (yuan * 100L + jiao * 10L) to m.range
            }
        }
        // 记15 / 花了15（无单位兜底：仅在记账动词附近）
        Regex("""(?:记|花了|消费|付款|给了|转了)[^\d]{0,4}(\d+(?:\.\d{1,2})?)""").find(s)?.let { m ->
            // 排除日期残留（2026-09-07 中的数字）：要求前后不是日期分隔符
            val v = m.groupValues[1].toDoubleOrNull()
            if (v != null && v > 0 && v <= 10_000_000) {
                // 四位以上整数极可能是年份，跳过
                if (!(m.groupValues[1].length >= 4 && !m.groupValues[1].contains("."))) {
                    return MoneyUtil.parseToFen(m.groupValues[1])?.let { it to m.range }
                }
            }
        }
        return null to null
    }

    /** 中文金额 → 分：支持 万/千/百/十 + 小数点（"三点五"）。 */
    internal fun parseChineseMoney(token: String): Long? {
        var t = token.trim().replace("两", "二")
        if (t.isEmpty()) return null
        MoneyUtil.parseToFen(t)?.let { return it }
        // 小数：三点五
        var frac = 0L
        if (t.contains("点")) {
            val parts = t.split("点", limit = 2)
            val intPart = chineseInt(parts[0]) ?: return null
            val decStr = parts[1].take(2)
            var decVal = 0
            for (ch in decStr) {
                decVal = decVal * 10 + (CN_DIGIT[ch] ?: return null)
            }
            frac = if (decStr.length == 1) decVal * 10L else decVal.toLong()
            val total = intPart * 100L + frac
            return if (total in 1..1_000_000_000L) total else null
        }
        val n = chineseInt(t) ?: return null
        if (n <= 0 || n > 10_000_000) return null
        return n * 100L
    }

    private val CN_DIGIT = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    /** 中文整数（支持万/千/百/十组合，如一百二十、三千五百）。 */
    internal fun chineseInt(s: String): Int? {
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        var total = 0
        var section = 0
        var num = 0
        var hasDigit = false
        for (ch in s) {
            when (ch) {
                in CN_DIGIT.keys -> { num = CN_DIGIT[ch]!!; hasDigit = true }
                '十' -> {
                    section += if (num == 0 && !hasDigit) 10 else num * 10
                    num = 0; hasDigit = false
                }
                '百' -> { section += num * 100; num = 0; hasDigit = false }
                '千' -> { section += num * 1000; num = 0; hasDigit = false }
                '万' -> {
                    total += (section + num) * 10000
                    section = 0; num = 0; hasDigit = false
                }
                else -> return null
            }
        }
        total += section + num
        // "百"单独出现
        if (s == "百") return 100
        if (s == "千") return 1000
        if (s == "万") return 10000
        return if (total > 0) total else null
    }

    private fun cleanNoun(raw: String): String {
        var t = raw.trim().trim('，', '。', ',', '.', '、', '；', ';', '：', ':', '！', '？', '”', '“', '"', '\'')
        // 剥离尾部动词残留："食堂吃了"→"食堂"
        listOf("吃了", "买了", "点了", "喝了", "订了", "花了", "消费", "结账", "买单", "付款", "给了").forEach { v ->
            if (t.endsWith(v) && t.length > v.length) t = t.removeSuffix(v)
        }
        // 剥离头部量词残留
        listOf("一笔", "一下").forEach { q ->
            if (t.startsWith(q)) t = t.removePrefix(q)
        }
        return t.trim().take(16)
    }
}

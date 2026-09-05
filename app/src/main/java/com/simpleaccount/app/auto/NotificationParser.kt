package com.simpleaccount.app.auto

/**
 * 支付通知解析器：从微信/支付宝/云闪付/钱包类通知提取 金额/收支/商家。
 * 文案每年都在变，所以：
 * 1. 金额用多种格式抓（¥ / ￥ / xx元 / 人民币xx）；
 * 2. 支付 App 包名命中后，不再强制正文里出现「微信支付/支付宝」字样
 *    （很多真机通知标题是商家名、正文只有金额——旧规则会整条丢掉）；
 * 3. 解析不出金额才放弃，绝不猜金额入库。
 */
object NotificationParser {

    data class ParsedPayment(
        val amountFen: Long,
        /** "expense" | "income" */
        val type: String,
        val merchant: String,
        /** "wechat" | "alipay" | "unionpay" | "mipay" | "wallet" */
        val source: String,
    )

    private val AMOUNT_YUAN = Regex("""(\d{1,7}(?:\.\d{1,2})?)\s*元""")
    private val AMOUNT_SYMBOL = Regex("""[¥￥]\s*(\d{1,7}(?:\.\d{1,2})?)""")
    private val AMOUNT_RMB = Regex("""人民币\s*(\d{1,7}(?:\.\d{1,2})?)""")
    private val AMOUNT_MINUS = Regex("""[-－]\s*[¥￥]?\s*(\d{1,7}(?:\.\d{1,2})?)""")

    private val CHAT_NOISE = listOf(
        "收到一条新消息", "发来一条", "[链接]", "[图片]", "[视频]", "[语音]",
        "邀请你", "红包封面", "拍了拍", "撤回了一条"
    )

    private val PAY_HINTS = listOf(
        "支付", "付款", "已支付", "支出", "消费", "扣款", "收款", "入账", "退款",
        "转账", "凭证", "交易", "成功支付", "付款成功", "支付成功", "交易成功"
    )

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        val pkg = packageName.lowercase()
        val full = "${title.trim()} ${text.trim()}".replace('\n', ' ').replace('\u3000', ' ')
        if (CHAT_NOISE.any { full.contains(it) } && PAY_HINTS.none { full.contains(it) }) return null

        val amountFen = extractAmountFen(full) ?: return null
        return when {
            pkg.contains("tencent.mm") -> parseWeChat(title, text, full, amountFen)
            pkg.contains("alipaygphone") || pkg.contains("alipay") -> parseAlipay(title, text, full, amountFen)
            pkg.contains("unionpay") -> parseUnionPay(full, amountFen)
            pkg.contains("mipay") || pkg.contains("xiaomi.payment") || pkg.contains("miui.fm") ->
                parseGenericWallet(full, amountFen, "mipay", "小米支付")
            pkg.contains("huawei.wallet") || pkg.contains("huawei.payment") ->
                parseGenericWallet(full, amountFen, "wallet", "华为钱包")
            pkg.contains("bank") || pkg.contains("cmb") || pkg.contains("icbc") ||
                pkg.contains("ccb") || pkg.contains("abc") || pkg.contains("boc") ->
                parseBank(full, amountFen)
            else -> {
                // 未知包名：正文明确是支付才记，避免误抓聊天
                if (PAY_HINTS.any { full.contains(it) }) {
                    parseGenericWallet(full, amountFen, "wallet", extractMerchant(full) ?: "消费")
                } else null
            }
        }
    }

    private fun parseUnionPay(full: String, amountFen: Long): ParsedPayment? {
        val isIncome = full.contains("入账") || full.contains("收款") || full.contains("退款") || full.contains("收入")
        return ParsedPayment(
            amountFen = amountFen,
            type = if (isIncome) "income" else "expense",
            merchant = extractMerchant(full) ?: "云闪付",
            source = "unionpay",
        )
    }

    private fun parseBank(full: String, amountFen: Long): ParsedPayment? {
        val isIncome = full.contains("入账") || full.contains("转入") || full.contains("收款") ||
            full.contains("退款") || full.contains("工资")
        if (!PAY_HINTS.any { full.contains(it) } && !full.contains("人民币")) return null
        return ParsedPayment(
            amountFen = amountFen,
            type = if (isIncome) "income" else "expense",
            merchant = extractMerchant(full) ?: "银行卡",
            source = "wallet",
        )
    }

    private fun parseGenericWallet(full: String, amountFen: Long, source: String, fallback: String): ParsedPayment {
        val isIncome = full.contains("入账") || full.contains("收款") || full.contains("退款") || full.contains("收入")
        return ParsedPayment(
            amountFen = amountFen,
            type = if (isIncome) "income" else "expense",
            merchant = extractMerchant(full) ?: fallback,
            source = source,
        )
    }

    private fun extractAmountFen(full: String): Long? {
        val m = AMOUNT_SYMBOL.find(full)
            ?: AMOUNT_YUAN.find(full)
            ?: AMOUNT_RMB.find(full)
            ?: AMOUNT_MINUS.find(full)
            ?: return null
        val yuan = m.groupValues[1].toDoubleOrNull() ?: return null
        if (yuan <= 0.0) return null
        return Math.round(yuan * 100)
    }

    private fun extractMerchant(full: String): String? {
        val patterns = listOf(
            Regex("""(?:收款方|商户|商家|向|给)[:：]?\s*([^\s¥￥，。,]{2,24})"""),
            Regex("""[（(]([^）)]{2,24})[)）]"""),
            Regex("""来自\s*(\S{1,20})"""),
        )
        for (p in patterns) {
            val hit = p.find(full)?.groupValues?.get(1)?.trim().orEmpty()
            if (hit.length in 2..24 && !hit.contains("元") && !hit.contains("支付")) return hit
        }
        return null
    }

    /**
     * 微信：包名已是微信即可。标题经常是商家名、正文只有金额，
     * 旧实现要求正文含「微信支付/付款/已支付」，真机大量通知会被丢掉。
     */
    private fun parseWeChat(title: String, text: String, full: String, amountFen: Long): ParsedPayment? {
        val isTransferIn = full.contains("向你转账") || full.contains("向你收款") ||
            (full.contains("转账") && (full.contains("收到") || full.contains("入账")))
        val isRefund = full.contains("退款")
        val hasPayHint = PAY_HINTS.any { full.contains(it) }
        val hasYen = full.contains("¥") || full.contains("￥")
        // 纯聊天里偶尔出现数字，没有支付符号/关键词就丢掉
        if (!hasPayHint && !hasYen && !title.contains("微信")) return null

        return when {
            isRefund -> ParsedPayment(amountFen, "income", extractMerchant(full) ?: "微信退款", "wechat")
            isTransferIn -> ParsedPayment(
                amountFen, "income",
                Regex("""(\S{1,12})\s*向你(?:转账|收款)""").find(full)?.groupValues?.get(1) ?: "微信转账",
                "wechat",
            )
            else -> ParsedPayment(
                amountFen, "expense",
                extractMerchant(full)
                    ?: title.takeIf { it.isNotBlank() && !it.contains("微信") && it.length in 2..24 }
                    ?: "微信支付",
                "wechat",
            )
        }
    }

    /** 支付宝：包名已是支付宝，不再要求正文出现「支付宝」 */
    private fun parseAlipay(title: String, text: String, full: String, amountFen: Long): ParsedPayment? {
        return when {
            full.contains("退款") -> ParsedPayment(
                amountFen, "income", extractMerchant(full) ?: "支付宝退款", "alipay",
            )
            full.contains("成功收款") || full.contains("收款成功") ||
                (full.contains("收款") && !full.contains("付款")) -> ParsedPayment(
                amountFen, "income",
                Regex("""来自\s*(\S{1,20})""").find(full)?.groupValues?.get(1) ?: extractMerchant(full) ?: "支付宝收款",
                "alipay",
            )
            else -> ParsedPayment(
                amountFen, "expense",
                extractMerchant(full)
                    ?: title.takeIf { it.isNotBlank() && !it.contains("支付宝") && it.length in 2..24 }
                    ?: "支付宝支出",
                "alipay",
            )
        }
    }
}

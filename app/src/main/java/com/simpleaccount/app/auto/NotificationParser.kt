package com.simpleaccount.app.auto

/**
 * 支付通知解析器：从微信/支付宝的系统通知文本中提取 金额/收支方向/商家。
 * 通知文案变化时各分支都有兜底，解析不出金额即放弃（绝不猜测入库）。
 */
object NotificationParser {

    data class ParsedPayment(
        val amountFen: Long,
        /** "expense" | "income" */
        val type: String,
        val merchant: String,
        /** "wechat" | "alipay" */
        val source: String,
    )

    private val AMOUNT_YUAN = Regex("(\\d{1,7}(?:\\.\\d{1,2})?)\\s*元")
    private val AMOUNT_SYMBOL = Regex("[¥￥]\\s*(\\d{1,7}(?:\\.\\d{1,2})?)")

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        val pkg = packageName.lowercase()
        val full = "${title.trim()} ${text.trim()}".replace('\n', ' ')
        val amountFen = extractAmountFen(full) ?: return null
        return when {
            pkg.contains("tencent.mm") -> parseWeChat(title, text, amountFen)
            pkg.contains("alipaygphone") || pkg.contains("alipay") -> parseAlipay(title, text, amountFen)
            pkg.contains("unionpay") -> parseUnionPay(full, amountFen)
            else -> null
        }
    }

    /** 云闪付：交易/消费/付款 → 支出；入账/收款/退款 → 收入。商家名通知里通常没有，记为"云闪付" */
    private fun parseUnionPay(full: String, amountFen: Long): ParsedPayment? {
        if (!full.contains("云闪付") && !full.contains("银联")) return null
        val isIncome = full.contains("入账") || full.contains("收款") || full.contains("退款") || full.contains("收入")
        return ParsedPayment(
            amountFen = amountFen,
            type = if (isIncome) "income" else "expense",
            merchant = "云闪付",
            source = "unionpay",
        )
    }

    private fun extractAmountFen(full: String): Long? {
        val m = AMOUNT_YUAN.find(full) ?: AMOUNT_SYMBOL.find(full) ?: return null
        val yuan = m.groupValues[1].toDoubleOrNull() ?: return null
        if (yuan <= 0.0) return null
        return Math.round(yuan * 100)
    }

    /** 微信：支付凭证（支出）/ 转账收款（收入）。只有含"支付/付款/转账/收款"的通知才处理 */
    private fun parseWeChat(title: String, text: String, amountFen: Long): ParsedPayment? {
        val full = "$title $text"
        val isPayment = full.contains("微信支付") || full.contains("付款") || full.contains("已支付")
        val isTransferIn = full.contains("向你转账") || full.contains("向你收款")
        return when {
            isPayment -> ParsedPayment(
                amountFen = amountFen,
                type = "expense",
                merchant = Regex("(?:收款方|商户|商家)[:：]\\s*(\\S{1,24})").find(full)?.groupValues?.get(1)
                    ?: "微信支付",
                source = "wechat",
            )
            isTransferIn -> ParsedPayment(
                amountFen = amountFen,
                type = "income",
                merchant = Regex("(\\S{1,12})\\s*向你(?:转账|收款)").find(full)?.groupValues?.get(1)
                    ?: "微信转账",
                source = "wechat",
            )
            else -> null
        }
    }

    /** 支付宝：支出/收款/退款通知 */
    private fun parseAlipay(title: String, text: String, amountFen: Long): ParsedPayment? {
        val full = "$title $text"
        if (!full.contains("支付宝")) return null
        return when {
            full.contains("退款") -> ParsedPayment(
                amountFen = amountFen, type = "income",
                merchant = "支付宝退款", source = "alipay",
            )
            full.contains("成功收款") || full.contains("收款成功") -> ParsedPayment(
                amountFen = amountFen, type = "income",
                merchant = Regex("来自\\s*(\\S{1,20})").find(full)?.groupValues?.get(1)
                    ?: "支付宝收款", source = "alipay",
            )
            full.contains("支出") || full.contains("付款成功") || full.contains("支付成功") -> ParsedPayment(
                amountFen = amountFen, type = "expense",
                merchant = Regex("[（(]([^）)]{1,24})[)）]").find(full)?.groupValues?.get(1)
                    ?: "支付宝支出", source = "alipay",
            )
            else -> null
        }
    }
}

package com.simpleaccount.app.auto

import com.simpleaccount.app.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 无感记账模拟测试套件：内置真实世界的通知文案样本，逐条跑
 * 解析 → 去重 → 入库 全链路，用于在真机上验证功能是否生效。
 * 测试金额互不相同（且带小数），避免与真实账单撞车。
 */
@Singleton
class AutoRecordSimulator @Inject constructor(
    private val autoRecordManager: AutoRecordManager,
) {

    data class Case(val pkg: String, val title: String, val text: String, val expect: String)

    /** 真实世界通知文案样本（微信/支付宝/云闪付 + 一条应被忽略的噪音） */
    val cases: List<Case> = listOf(
        Case("com.tencent.mm", "微信支付", "已支付￥25.50 收款方：永辉超市", "微信支出25.5"),
        Case("com.tencent.mm", "微信支付", "微信支付凭证\n￥12.00\n收款方：蜜雪冰城", "微信支出12"),
        Case("com.tencent.mm", "微信转账", "张三向你转账￥200.00", "微信转账收入200"),
        Case("com.tencent.mm", "微信支付", "你已支付￥8.80 商户：瑞幸咖啡", "微信支出8.8"),
        Case("com.eg.android.AlipayGphone", "支付宝", "你有一笔35.50元的支出（蜜雪冰城）", "支付宝支出35.5"),
        Case("com.eg.android.AlipayGphone", "支付宝", "支付宝成功收款100.00元", "支付宝收入100"),
        Case("com.eg.android.AlipayGphone", "支付宝", "你有一笔5.00元的退款", "支付宝退款收入5"),
        Case("com.unionpay", "云闪付", "您尾号1234的账户交易人民币150.00元", "云闪付支出150"),
        Case("com.tencent.mm", "微信", "你收到一条新消息", "噪音：无金额应忽略"),
    )

    data class Result(val index: Int, val case: Case, val recorded: Boolean, val parsedDesc: String, val error: String?)

    /**
     * 跑全链路。返回每条结果（写入 AppLog + UI 展示）。
     * 注意：需要先在设置里打开「自动记账」总开关，否则全部返回未生效。
     */
    suspend fun runAll(): List<Result> {
        val results = mutableListOf<Result>()
        cases.forEachIndexed { i, c ->
            val parsed = NotificationParser.parse(c.pkg, c.title, c.text)
            if (parsed == null) {
                val ok = !c.expect.contains("忽略")
                results.add(Result(i, c, recorded = false, parsedDesc = "未解析出金额/模式", error = if (ok) "应当匹配却未匹配！" else null))
                AppLog.d("无感记账模拟 #${i + 1}: ${c.title} | ${c.text.take(30)} → 未解析（${c.expect}）${if (ok) " ← 异常！" else " ← 符合预期"}")
                return@forEachIndexed
            }
            val desc = "${if (parsed.type == "expense") "支出" else "收入"} ${parsed.amountFen / 100.0}元 · ${parsed.merchant}"
            val recorded = runCatching { autoRecordManager.onPaymentParsed(parsed) }
                .getOrElse {
                    results.add(Result(i, c, recorded = false, parsedDesc = desc, error = it.message))
                    return@forEachIndexed
                }
            results.add(Result(i, c, recorded = recorded, parsedDesc = desc, error = null))
            AppLog.d("无感记账模拟 #${i + 1}: ${c.title} | ${c.text.take(30)} → 解析=$desc 入库=$recorded（预期:${c.expect}）")
        }
        return results
    }
}

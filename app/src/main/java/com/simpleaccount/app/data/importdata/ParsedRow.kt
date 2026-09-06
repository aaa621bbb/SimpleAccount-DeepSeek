package com.simpleaccount.app.data.importdata

/** 特殊类型标记 */
enum class RowSpecial {
    /** 普通 */
    NORMAL,
    /** 转账（独立"转账"分类） */
    TRANSFER,
    /** 退款（找对应付款抵消，找不到记收入"退款"） */
    REFUND,
}

/** 解析出的一行账单原始数据 */
data class ParsedRow(
    val date: String,        // yyyy-MM-dd
    val type: String,        // expense | income
    val amount: Long,        // 分
    val merchant: String,
    val product: String,
    /** 收/付款方式（微信"支付方式"） */
    val paymentMethod: String = "",
    /** 交易单号 */
    val tradeOrderNo: String = "",
    /** 商家/商户单号 */
    val merchantOrderNo: String = "",
    /** 文件"交易分类/交易类型"列的原始值（用于兜底分类） */
    val sourceCategory: String = "",
    /** 特殊类型标记（转账/退款），普通为 NORMAL */
    val special: RowSpecial = RowSpecial.NORMAL,
    /** 时间 HH:mm（账单交易时间里带的时分；空=未知） */
    val time: String = "",
)

/** 一条被跳过/失败的原始行及其原因 */
data class ParseFailure(
    /** 该行的原始内容摘要（日期/金额/对象等便于识别） */
    val content: String,
    /** 失败原因（中文，可直接展示） */
    val reason: String,
)

/** 解析结果 */
data class ParseResult(
    val rows: List<ParsedRow>,
    val skipCount: Int,
    /** 失败明细（R1：逐条失败 + 原因） */
    val failures: List<ParseFailure> = emptyList(),
    /** 跳过原因统计（原因 → 条数），供导入结果页解释"跳过 N 条"的构成 */
    val skipReasons: Map<String, Int> = emptyMap(),
)

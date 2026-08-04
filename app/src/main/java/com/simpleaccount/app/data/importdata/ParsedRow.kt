package com.simpleaccount.app.data.importdata

/** 解析出的一行账单原始数据 */
data class ParsedRow(
    val date: String,        // yyyy-MM-dd
    val type: String,        // expense | income
    val amount: Long,        // 分
    val merchant: String,
    val product: String,
)

/** 解析结果 */
data class ParseResult(
    val rows: List<ParsedRow>,
    val skipCount: Int,
)

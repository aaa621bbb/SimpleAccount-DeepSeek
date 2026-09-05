package com.simpleaccount.app.data.importdata

import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

/**
 * 账单解析器：支持 微信/支付宝 的 xlsx / csv。
 * - xlsx 用 Apache POI，取第一个 sheet
 * - csv 自动检测编码（UTF-8/BOM/GBK）
 * - 表头语义关键词识别，不硬编码列号
 * - 金额清洗、日期兼容、收支方向判断、跳过类型
 */
object BillParser {

    /** 表头关键词 */
    private val HEADER_DATE_KW = listOf("交易时间", "创建时间", "时间", "日期")
    private val HEADER_AMOUNT_KW = listOf("金额")
    private val HEADER_MONEY_FLOW_KW = listOf("收/支", "收支", "资金流向")
    private val HEADER_MERCHANT_KW = listOf("交易对方", "对方", "商户")
    private val HEADER_PRODUCT_KW = listOf("商品名称", "商品", "名称")
    /** 交易分类列（支付宝特有，用于区分投资/退款/转账） */
    private val HEADER_CATEGORY_KW = listOf("交易分类", "交易类型", "类型")
    /** 收/付款方式列（微信"支付方式"，支付宝"收/付款方式"） */
    private val HEADER_PAYMENT_KW = listOf("支付方式", "付款方式", "收/付款方式")
    /** 交易单号列（存着不展示） */
    private val HEADER_TRADE_NO_KW = listOf("交易单号", "交易订单号", "交易号", "订单号")
    /** 商户/商家单号列（存着不展示） */
    private val HEADER_MERCHANT_NO_KW = listOf("商户单号", "商家单号", "商户订单号", "商家订单号")
    /** 交易状态列（支付宝"交易状态"，微信"当前状态"）：用于跳过交易关闭/失败行 */
    private val HEADER_STATUS_KW = listOf("当前状态", "交易状态", "状态")

    /** 支出关键词 */
    private val EXPENSE_KW = listOf("支出", "付款", "付费")
    /** 收入关键词 */
    private val INCOME_KW = listOf("收入", "收款")
    /** 应跳过类型（退款/充值/提现/理财/还款/转账） */
    private val SKIP_KW = listOf("退款", "充值", "提现", "理财", "还款", "转账")
    /** 投资理财（全部跳过，不计） */
    private val INVEST_KW = listOf("投资理财", "蚂蚁财富", "余额宝", "基金")
    /** 转账类型 */
    private val TRANSFER_KW = listOf("转账")

    fun parse(data: ByteArray, fileName: String): ParseResult {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "xlsx", "xls" -> parseXlsx(data)
            "csv", "txt" -> parseCsv(data)
            else -> ParseResult(emptyList(), 0)
        }
    }

    private fun parseXlsx(data: ByteArray): ParseResult {
        val wb = WorkbookFactory.create(ByteArrayInputStream(data))
        val sheet = wb.getSheetAt(0)
        val formatter = DataFormatter()
        val matrix = mutableListOf<List<String>>()
        val it = sheet.iterator()
        while (it.hasNext()) {
            val row = it.next()
            val cells = mutableListOf<String>()
            row.forEach { cell ->
                cells.add(formatter.formatCellValue(cell))
            }
            matrix.add(cells)
        }
        wb.close()
        return parseMatrix(matrix)
    }

    private fun parseCsv(data: ByteArray): ParseResult {
        val text = detectEncoding(data)
        val matrix = CsvParser.parse(text)
        return parseMatrix(matrix)
    }

    /** 检测编码：BOM → UTF-8；若含大量非法 UTF-8 字节则回退 GBK */
    private fun detectEncoding(data: ByteArray): String {
        if (data.size >= 3 &&
            data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()
        ) {
            return String(data, 3, data.size - 3, Charsets.UTF_8)
        }
        // 尝试严格 UTF-8 解码
        return try {
            val utf8 = String(data, Charsets.UTF_8)
            // 保守检查：是否包含常见中文，若无再尝试 GBK
            if (isLikelyUtf8(utf8)) utf8 else decodeGbk(data)
        } catch (e: Exception) {
            decodeGbk(data)
        }
    }

    private fun isLikelyUtf8(s: String): Boolean {
        // 若含替换符 � 说明不是 utf-8
        return !s.contains('\uFFFD')
    }

    private fun decodeGbk(data: ByteArray): String {
        return try {
            String(data, java.nio.charset.Charset.forName("GBK"))
        } catch (e: Exception) {
            String(data, Charsets.UTF_8)
        }
    }

    private fun parseMatrix(matrix: List<List<String>>): ParseResult {
        if (matrix.isEmpty()) return ParseResult(emptyList(), 0)

        // 找表头行：跳过空行/标题行，遇含"金额"关键词的行即表头
        var headerRow = -1
        var colDate = -1
        var colFlow = -1
        var colAmount = -1
        var colMerchant = -1
        var colProduct = -1
        var colCategory = -1
        var colPayment = -1
        var colTradeNo = -1
        var colMerchantNo = -1
        var colStatus = -1

        for (r in matrix.indices) {
            val row = matrix[r]
            val cellTexts = row.map { it.trim() }
            if (cellTexts.all { it.isEmpty() }) continue

            // 表头必须含"金额" + 至少一个日期关键词
            val hasAmount = cellTexts.any { HEADER_AMOUNT_KW.any { kw -> it.contains(kw) } }
            if (!hasAmount) continue
            val hasDate = cellTexts.any { HEADER_DATE_KW.any { kw -> it.contains(kw) } }
            if (!hasDate) continue

            headerRow = r
            // 按关键词特异性从高到低逐格识别；每个字段只认第一处命中。
            // 关键修复：支付宝"对方账号"列含"对方"会劫持商家列 —— 商家列匹配时排除含"账号/卡号"的表头；
            // "交易订单号/商家订单号"含"订单号"会劫持交易单号 —— 单号列先于泛化关键词判断。
            row.forEachIndexed { idx, cell ->
                val c = cell.trim()
                if (c.isEmpty()) return@forEachIndexed
                val isNoCol = c.contains("单号") || c.contains("订单号")
                when {
                    colStatus < 0 && !isNoCol && HEADER_STATUS_KW.any { c.contains(it) } -> colStatus = idx
                    colMerchantNo < 0 && HEADER_MERCHANT_NO_KW.any { c.contains(it) } -> colMerchantNo = idx
                    colTradeNo < 0 && HEADER_TRADE_NO_KW.any { c.contains(it) } -> colTradeNo = idx
                    colFlow < 0 && !isNoCol && HEADER_MONEY_FLOW_KW.any { c.contains(it) } -> colFlow = idx
                    colPayment < 0 && HEADER_PAYMENT_KW.any { c.contains(it) } -> colPayment = idx
                    colCategory < 0 && !isNoCol && HEADER_CATEGORY_KW.any { c.contains(it) } -> colCategory = idx
                    colDate < 0 && HEADER_DATE_KW.any { c.contains(it) } -> colDate = idx
                    colAmount < 0 && HEADER_AMOUNT_KW.any { c.contains(it) } -> colAmount = idx
                    colMerchant < 0 && !c.contains("账号") && !c.contains("卡号") &&
                        HEADER_MERCHANT_KW.any { c.contains(it) } -> colMerchant = idx
                    colProduct < 0 && HEADER_PRODUCT_KW.any { c.contains(it) } -> colProduct = idx
                }
            }
            break
        }

        if (headerRow < 0 || colAmount < 0) {
            // 无法识别表头
            return ParseResult(emptyList(), 0)
        }

        val rows = mutableListOf<ParsedRow>()
        var skipped = 0
        val failures = mutableListOf<ParseFailure>()
        val skipReasons = mutableMapOf<String, Int>()
        fun skip(reason: String) {
            skipped++
            skipReasons[reason] = (skipReasons[reason] ?: 0) + 1
        }

        for (r in headerRow + 1 until matrix.size) {
            val row = matrix[r]
            if (row.isEmpty()) continue
            val dateCell = if (colDate >= 0) row.getOrNull(colDate)?.trim() ?: "" else ""
            val amountCell = if (colAmount >= 0) row.getOrNull(colAmount)?.trim() ?: "" else ""
            val flowCell = if (colFlow >= 0) row.getOrNull(colFlow)?.trim() ?: "" else ""
            val merchantCell = if (colMerchant >= 0) row.getOrNull(colMerchant)?.trim() ?: "" else ""
            val productCell = if (colProduct >= 0) row.getOrNull(colProduct)?.trim() ?: "" else ""
            val paymentCell = if (colPayment >= 0) row.getOrNull(colPayment)?.trim() ?: "" else ""
            val tradeNoCell = if (colTradeNo >= 0) row.getOrNull(colTradeNo)?.trim() ?: "" else ""
            val merchantNoCell = if (colMerchantNo >= 0) row.getOrNull(colMerchantNo)?.trim() ?: "" else ""
            val statusCell = if (colStatus >= 0) row.getOrNull(colStatus)?.trim() ?: "" else ""

            // 空行跳过
            if (dateCell.isEmpty() && amountCell.isEmpty()) continue

            // 交易状态：关闭/失败的交易没有真实扣款，直接跳过（支付宝常见"交易关闭"行）。
            // 注意：退款类状态（退款成功/已全额退款）不在此列 —— 那些行是真实的退款入账，走 REFUND 特殊路径。
            if (statusCell.contains("关闭") || statusCell.contains("失败")) {
                skip("交易关闭/失败，未实际扣款")
                continue
            }

            // 微信"收/支"为"/"（中性交易：充值/提现/理财通/零钱通/信用卡还款）不记账
            if (flowCell == "/") {
                skip("中性交易（充值/提现/还款等）")
                continue
            }

            val categoryCell = if (colCategory >= 0) row.getOrNull(colCategory)?.trim() ?: "" else ""
            val rowSummary = "日期[$dateCell] 金额[$amountCell] 方向[$flowCell] 对象[$merchantCell]".take(80)

            // 支付宝"不计收支 / 交易分类"处理：投资理财一律跳过；退款/转账打特殊标记
            val special = when {
                categoryCell.isNotEmpty() && INVEST_KW.any { categoryCell.contains(it) } -> {
                    // 投资理财全部跳过，不计
                    skip("投资理财/余额宝/基金，不计收支")
                    continue
                }
                categoryCell.contains("退款") || flowCell.contains("退款") -> RowSpecial.REFUND
                categoryCell.contains("转账") -> RowSpecial.TRANSFER
                else -> RowSpecial.NORMAL
            }

            // 类型方向（收/支列）
            val type = resolveType(flowCell, amountCell, categoryCell, special)
            if (type == null) {
                failures.add(ParseFailure(rowSummary, "无法判断收支方向（收/支列不能识别）"))
                skipped++
                continue
            }

            // 金额（先解析，0元静默跳过则后续判断）
            val absAmountCell = amountCell.replace("-", "").trim()
            val amount = MoneyUtil.parseToFen(absAmountCell)
            if (amount == null) {
                failures.add(ParseFailure(rowSummary, "金额格式无法识别"))
                skipped++
                continue
            }
            if (amount <= 0) {
                // 金额为 0：静默跳过，不报失败（0元记录无记账意义）
                skip("金额为 0（立减全额抵扣）")
                continue
            }

            // 日期
            val date = DateUtil.parseFlexible(dateCell)
            if (date == null) {
                failures.add(ParseFailure(rowSummary, "日期格式无法识别"))
                skipped++
                continue
            }
            // 时间（微信/支付宝的交易时间都精确到秒，取 HH:mm 入库）
            val timeStr = Regex("(\\d{1,2}):(\\d{2})").find(dateCell)?.let { tm ->
                "%02d:%02d".format(
                    tm.groupValues[1].toInt().coerceIn(0, 23),
                    tm.groupValues[2].toInt().coerceIn(0, 59)
                )
            } ?: ""

            rows.add(
                ParsedRow(
                    date = date,
                    type = type,
                    amount = amount,
                    merchant = merchantCell,
                    product = productCell,
                    paymentMethod = paymentCell,
                    tradeOrderNo = tradeNoCell,
                    merchantOrderNo = merchantNoCell,
                    sourceCategory = categoryCell,
                    special = special,
                    time = timeStr,
                )
            )
        }

        return ParseResult(rows, skipped, failures, skipReasons)
    }

    private const val TYPE_SKIP = "skip"

    /**
     * 根据收支/金额列 + 交易分类判断方向。返回 expense/income/skip/null。
     * 支付宝"不计收支"的真实消费（日用百货/教育培训等）按支出处理。
     */
    private fun resolveType(
        flowCell: String,
        amountCell: String,
        categoryCell: String,
        special: RowSpecial,
    ): String? {
        // 转账/退款特殊标记：先定方向。
        // 修复：转账不再强制记为支出 —— 优先按「收/支」列判断（别人转给我 = 收入），未知才兜底支出
        if (special == RowSpecial.TRANSFER) {
            val fl = flowCell.lowercase()
            return when {
                INCOME_KW.any { fl.contains(it) } -> Transaction.TYPE_INCOME
                EXPENSE_KW.any { fl.contains(it) } -> Transaction.TYPE_EXPENSE
                else -> Transaction.TYPE_EXPENSE
            }
        }
        if (special == RowSpecial.REFUND) return Transaction.TYPE_INCOME

        val flow = flowCell.lowercase()
        val cat = categoryCell.lowercase()

        // 支付宝"不计收支"：投资理财已在上游跳过；剩下的真实消费按支出/收入处理
        if (flow.contains("不计收支") || flow == "") {
            // 用金额正负判断：正向通常为支出（微信/支付宝付款），负向为收入
            val trimmed = amountCell.replace("¥", "").trim()
            return if (trimmed.startsWith("-")) Transaction.TYPE_INCOME
            else Transaction.TYPE_EXPENSE
        }

        if (flow.isNotEmpty()) {
            if (SKIP_KW.any { flow.contains(it) }) return TYPE_SKIP
            if (EXPENSE_KW.any { flow.contains(it) }) return Transaction.TYPE_EXPENSE
            if (INCOME_KW.any { flow.contains(it) }) return Transaction.TYPE_INCOME
        }
        // 交易分类辅助判断
        if (cat.isNotEmpty()) {
            if (EXPENSE_KW.any { cat.contains(it) } || cat.contains("支付") || cat.contains("消费")) return Transaction.TYPE_EXPENSE
            if (INCOME_KW.any { cat.contains(it) } || cat.contains("收款")) return Transaction.TYPE_INCOME
        }
        // 用金额符号判断
        val trimmed = amountCell.trim()
        if (trimmed.startsWith("-")) {
            // 微信支出为负（常见），但也可能是收入为负，此处缺流判断则跳过
            return null
        }
        return null
    }
}

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
    private val HEADER_MONEY_FLOW_KW = listOf("收/支", "收支", "资金流向", "收/支")
    private val HEADER_MERCHANT_KW = listOf("交易对方", "对方", "商户")
    private val HEADER_PRODUCT_KW = listOf("商品名称", "商品", "名称")

    /** 支出关键词 */
    private val EXPENSE_KW = listOf("支出", "付款", "付费")
    /** 收入关键词 */
    private val INCOME_KW = listOf("收入", "收款")
    /** 应跳过类型（退款/充值/提现/理财/还款/转账） */
    private val SKIP_KW = listOf("退款", "充值", "提现", "理财", "还款", "转账")

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
            row.forEachIndexed { idx, cell ->
                val c = cell.trim()
                if (c.isEmpty()) return@forEachIndexed
                if (HEADER_DATE_KW.any { c.contains(it) }) colDate = idx
                if (HEADER_MONEY_FLOW_KW.any { c.contains(it) }) colFlow = idx
                if (HEADER_AMOUNT_KW.any { c.contains(it) }) colAmount = idx
                if (HEADER_MERCHANT_KW.any { c.contains(it) }) colMerchant = idx
                if (HEADER_PRODUCT_KW.any { c.contains(it) }) colProduct = idx
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

        for (r in headerRow + 1 until matrix.size) {
            val row = matrix[r]
            if (row.isEmpty()) continue
            val dateCell = if (colDate >= 0) row.getOrNull(colDate)?.trim() ?: "" else ""
            val amountCell = if (colAmount >= 0) row.getOrNull(colAmount)?.trim() ?: "" else ""
            val flowCell = if (colFlow >= 0) row.getOrNull(colFlow)?.trim() ?: "" else ""
            val merchantCell = if (colMerchant >= 0) row.getOrNull(colMerchant)?.trim() ?: "" else ""
            val productCell = if (colProduct >= 0) row.getOrNull(colProduct)?.trim() ?: "" else ""

            // 空行跳过
            if (dateCell.isEmpty() && amountCell.isEmpty()) continue

            // 类型方向
            val type = resolveType(flowCell, amountCell)
            val rowSummary = "日期[$dateCell] 金额[$amountCell] 方向[$flowCell] 对象[$merchantCell]".take(80)
            if (type == TYPE_SKIP) {
                skipped++
                // 中性交易（退款/充值/提现等）：属"按规则跳过"，不是错误，不记入失败明细
                continue
            }
            if (type == null) {
                failures.add(ParseFailure(rowSummary, "无法判断收支方向（收/支列不能识别）"))
                skipped++
                continue
            }

            // 金额
            val absAmountCell = amountCell.replace("-", "").trim()
            val amount = MoneyUtil.parseToFen(absAmountCell)
            if (amount == null) {
                failures.add(ParseFailure(rowSummary, "金额格式无法识别"))
                skipped++
                continue
            }
            if (amount <= 0) {
                failures.add(ParseFailure(rowSummary, "金额不是有效正数"))
                skipped++
                continue
            }

            // 日期
            val date = DateUtil.parseFlexible(dateCell)
            if (date == null) {
                failures.add(ParseFailure(rowSummary, "日期格式无法识别"))
                skipped++
                continue
            }

            rows.add(
                ParsedRow(
                    date = date,
                    type = type,
                    amount = amount,
                    merchant = merchantCell,
                    product = productCell,
                )
            )
        }

        return ParseResult(rows, skipped, failures)
    }

    private const val TYPE_SKIP = "skip"

    /** 根据收支/金额列判断方向。返回 expense/income/skip/null */
    private fun resolveType(flowCell: String, amountCell: String): String? {
        val flow = flowCell.lowercase()
        if (flow.isNotEmpty()) {
            if (SKIP_KW.any { flow.contains(it) }) return TYPE_SKIP
            if (EXPENSE_KW.any { flow.contains(it) }) return Transaction.TYPE_EXPENSE
            if (INCOME_KW.any { flow.contains(it) }) return Transaction.TYPE_INCOME
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

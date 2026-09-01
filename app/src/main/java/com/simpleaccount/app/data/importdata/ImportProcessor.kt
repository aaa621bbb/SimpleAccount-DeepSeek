package com.simpleaccount.app.data.importdata

import com.simpleaccount.app.data.dao.ImportFailureDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.entity.ImportFailure
import com.simpleaccount.app.data.entity.ImportLog
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.service.ClassificationService
import com.simpleaccount.app.util.CategoryPresets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账单导入处理：去重 → 自动分类 → 写库 → 写 Merchant 记忆 → 写 ImportLog。
 * 在后台 IO 线程执行（由调用方协程调度）。
 */
@Singleton
class ImportProcessor @Inject constructor(
    private val transactionDao: TransactionDao,
    private val importLogDao: ImportLogDao,
    private val importFailureDao: ImportFailureDao,
    private val categoryRepository: CategoryRepository,
    private val classificationService: ClassificationService,
    private val settingsRepository: com.simpleaccount.app.data.repository.SettingsRepository,
) {

    data class ImportResult(
        val inserted: Int,
        val skipped: Int,
        val failed: Int,
        val batchId: String,
        /** 跳过原因统计（原因 → 条数），供导入结果页解释跳过构成 */
        val skipReasons: Map<String, Int> = emptyMap(),
    )

    suspend fun process(
        rows: List<ParsedRow>,
        sourceType: String,
        fileName: String,
        parseSkip: Int,
        failures: List<ParseFailure> = emptyList(),
        parsedSkipReasons: Map<String, Int> = emptyMap(),
    ): ImportResult {
        val batchId = UUID.randomUUID().toString()
        val existing = transactionDao.getAll()
        val validNames = categoryRepository.getAll().map { it.name }.toHashSet()

        // 归一化判重（两级三集合）：
        // seenFull       完整键（base|单号）—— 同渠道同单号必重复
        // seenAnyBase    基础键（不含单号）—— 本次行无单号时，任何同基础键都算重复
        // seenBaseNoOrder 无单号记录的基础键 —— 本次行有单号、但库里是老版本导入（无单号列）的同笔交易
        // 注意：同日同商家同额但单号不同的多笔真实消费（两杯 2 元奶茶）不会被误并
        val seenFull = HashSet<String>()
        val seenAnyBase = HashSet<String>()
        val seenBaseNoOrder = HashSet<String>()
        existing.filter { it.source == Transaction.SOURCE_IMPORT }.forEach { t ->
            val b = baseKey(t.date, t.amount, t.merchant, t.product)
            seenAnyBase.add(b)
            if (t.tradeOrderNo.isNotBlank()) seenFull.add("$b|${t.tradeOrderNo.trim()}")
            else seenBaseNoOrder.add(b)
        }

        fun isDup(row: ParsedRow): Boolean {
            val b = baseKey(row.date, row.amount, row.merchant, row.product)
            return if (row.tradeOrderNo.isNotBlank()) {
                seenFull.contains("$b|${row.tradeOrderNo.trim()}") || seenBaseNoOrder.contains(b)
            } else {
                seenAnyBase.contains(b)
            }
        }

        fun markInserted(row: ParsedRow) {
            val b = baseKey(row.date, row.amount, row.merchant, row.product)
            seenAnyBase.add(b)
            if (row.tradeOrderNo.isBlank()) seenBaseNoOrder.add(b)
            else seenFull.add("$b|${row.tradeOrderNo.trim()}")
        }

        var inserted = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        val skipReasons = parsedSkipReasons.toMutableMap()
        fun skip(reason: String) {
            skipped++
            skipReasons[reason] = (skipReasons[reason] ?: 0) + 1
        }

        // --- 阶段1：先处理非退款行（含转账独立成类），为退款抵消准备"付款"候选 ---
        // 预收集本批次内"同商家付款"，用于退款抵消
        val merchantPayments = mutableMapOf<String, MutableList<Pair<Int, Long>>>()

        // 先把普通行入账，同时收集付款候选（仅支出，非退款/转账）
        // 自动记账（无感抓取）的记录在导入时被同笔账单覆盖：以导入为准
        val autoRows = transactionDao.getAllBySource(Transaction.SOURCE_AUTO)
        // 数据冲突优先级（用户可配）：
        // import=以导入账单为准（默认）：导入覆盖手动/截图/自动的同笔记录
        // manual=以手动·截图·AI记录为准：导入遇到同笔的 手动/自动 记录只跳过，不覆盖
        val manualPriority = settingsRepository.importPriority() ==
            com.simpleaccount.app.data.repository.SettingsRepository.PRIORITY_MANUAL
        for ((i, row) in rows.withIndex()) {
            if (isDup(row)) {
                skip("重复（此前已导入过）")
                continue
            }

            // 手动记账的同笔交易：按用户选择的优先级处理
            if (row.special != RowSpecial.REFUND) {
                val key = baseKey(row.date, row.amount, row.merchant, row.product)
                val manualHit = transactionDao.getAll().firstOrNull {
                    baseKey(it.date, it.amount, it.merchant, it.product) == key &&
                        it.source == Transaction.SOURCE_MANUAL
                }
                if (manualHit != null) {
                    if (manualPriority) {
                        skip("保留手动记账（优先级：手动为准）")
                        continue
                    } else {
                        // 导入为准：删除同笔手动记录，由导入数据取代（信息更全：商家/单号/分类）
                        transactionDao.deleteById(manualHit.id)
                    }
                }
            }

            // 自动记账/截图记账的同一笔交易 → 按优先级处理：
            //   导入为准（默认）→ 导入数据覆盖该行（补全商家/商品/单号/分类），不新增
            //   手动为准 → 跳过该导入行，保留自动记录
            // 匹配：日期+金额相同 且 商家模糊匹配（自动记录商家常与账单写法不同，如"微信支付"vs 实际商户）
            if (row.special != RowSpecial.REFUND) {
                val autoHit = autoRows.firstOrNull {
                    com.simpleaccount.app.data.agent.DedupEngine.isSameTx(
                        it.date, it.amount, it.merchant, it.time,
                        row.date, row.amount, row.merchant, row.time
                    )
                }
                if (autoHit != null) {
                    if (manualPriority) {
                        skip("保留自动记账（优先级：手动为准）")
                        continue
                    }
                    val cat = classificationService.classifyForImport(
                        row.merchant, row.product, row.sourceCategory, validNames, row.type
                    )
                    transactionDao.update(
                        autoHit.copy(
                            date = row.date,
                            amount = row.amount,
                            type = row.type,
                            category = cat,
                            merchant = row.merchant,
                            product = row.product,
                            paymentMethod = row.paymentMethod,
                            tradeOrderNo = row.tradeOrderNo,
                            merchantOrderNo = row.merchantOrderNo,
                            time = row.time,
                            source = Transaction.SOURCE_IMPORT,
                            importBatchId = batchId,
                            updatedAt = now
                        )
                    )
                    skip("覆盖自动记账（以导入账单为准）")
                    continue
                }
            }

            // 转账：独立"转账"分类（收入方向转账落"其它收入"，方向由解析器按收/支列判定）
            if (row.special == RowSpecial.TRANSFER) {
                val transferCategory = if (row.type == Transaction.TYPE_EXPENSE)
                    CategoryPresets.TRANSFER_CATEGORY
                else CategoryPresets.DEFAULT_INCOME_CATEGORY
                insertRow(row, row.amount, row.type, transferCategory, batchId, now, manualPriority)
                markInserted(row)
                inserted++
                continue
            }

            // 退款：留到阶段2处理
            if (row.special == RowSpecial.REFUND) continue

            // 普通支出：记录为付款候选（供后续退款抵消）
            if (row.type == Transaction.TYPE_EXPENSE) {
                merchantPayments.getOrPut(row.merchant) { mutableListOf() }
                    .add(i to row.amount)
            }

            val cat = classificationService.classifyForImport(
                row.merchant, row.product, row.sourceCategory, validNames, row.type
            )
            insertRow(row, row.amount, row.type, cat, batchId, now, manualPriority)
            markInserted(row)
            inserted++
        }

        // --- 阶段2：处理退款行 ---
        for ((i, row) in rows.withIndex()) {
            if (row.special != RowSpecial.REFUND) continue
            if (isDup(row)) {
                skip("退款重复（此前已导入过）")
                continue
            }
            val refundAmount = row.amount
            val payCandidates = merchantPayments[row.merchant] ?: emptyList()
            val matching = payCandidates.filter { it.second >= refundAmount }.sortedBy { it.first }
            if (matching.isNotEmpty()) {
                // 找到同商家且足够扣的付款 → 扣减最近一笔的金额，退款不入账
                // 注意：阶段1已写入库，这里需要按发生顺序更新最近的付款
                val target = matching.last()  // 最近（rowIdx 最大）的同商家足额付款
                // 该付款已通过 dedupKey 入账，找到它的 Transaction 并减少金额
                val targetRow = rows[target.first]
                val tKey = dedupKey(targetRow)
                val existingT = transactionDao.getAll()
                    .firstOrNull { dedupKey(it) == tKey && it.source == Transaction.SOURCE_IMPORT && it.importBatchId == batchId }
                if (existingT != null && existingT.amount >= refundAmount) {
                    val newAmount = existingT.amount - refundAmount
                    if (newAmount <= 0) {
                        // 扣减到 0：删除该付款记录（退款完全抵消）
                        transactionDao.delete(existingT)
                    } else {
                        transactionDao.update(existingT.copy(amount = newAmount))
                    }
                    markInserted(row)
                    skip("退款与同商家付款抵消")
                    continue
                }
            }
            // 找不到可抵消的付款 → 记入收入，分类"退款"
            insertRow(row, refundAmount, Transaction.TYPE_INCOME, CategoryPresets.REFUND_CATEGORY, batchId, now, manualPriority)
            markInserted(row)
            inserted++
        }

        val refreshed = transactionDao.getAll()
        val lastDate = refreshed.maxOfOrNull { it.date }

        importLogDao.insert(
            ImportLog(
                batchId = batchId,
                sourceType = sourceType,
                fileName = fileName,
                importDate = now,
                recordCount = inserted,
                skipCount = skipped + parseSkip,
                lastTransactionDate = lastDate
            )
        )

        // R1：把本次解析失败的行写入 import_failures 表，供用户查看失败明细
        if (failures.isNotEmpty()) {
            importFailureDao.insertAll(
                failures.map {
                    ImportFailure(
                        batchId = batchId,
                        content = it.content,
                        reason = it.reason,
                        createdAt = now
                    )
                }
            )
        }

        return ImportResult(
            inserted = inserted,
            skipped = skipped + parseSkip,
            failed = failures.size,
            batchId = batchId,
            skipReasons = skipReasons
        )
    }

    /**
     * 去重键：日期|金额|商家|商品|交易单号（完整键）。
     * 同一天同商家同金额的多笔真实消费（如两杯 2 元奶茶）各自单号不同，完整键可区分。
     * 基础键（不含单号）由 baseKey() 提供，供归一化两级判重使用。
     */
    private fun dedupKey(row: ParsedRow): String {
        val base = baseKey(row.date, row.amount, row.merchant, row.product)
        return if (row.tradeOrderNo.isNotBlank()) "$base|${row.tradeOrderNo.trim()}" else base
    }

    private fun dedupKey(t: Transaction): String {
        val base = baseKey(t.date, t.amount, t.merchant, t.product)
        return if (t.tradeOrderNo.isNotBlank()) "$base|${t.tradeOrderNo.trim()}" else base
    }

    /**
     * 文本归一化：trim + 去空白（含全角空格 U+3000）+ 全角括号/冒号/逗号转半角 + 小写。
     * 解决微信/支付宝、不同批次导出的同一笔交易因写法差异判重失败的问题。
     */
    private fun normalizeText(s: String): String =
        s.trim()
            .replace("\u3000", "")
            .replace(" ", "")
            .replace('（', '(')
            .replace('）', ')')
            .replace('：', ':')
            .replace('，', ',')
            .lowercase()

    /** 基础键：日期|金额|归一化商家|归一化商品（不含单号） */
    private fun baseKey(date: String, amount: Long, merchant: String, product: String): String {
        val hasDetail = merchant.isNotBlank() || product.isNotBlank()
        return if (hasDetail) "$date|$amount|${normalizeText(merchant)}|${normalizeText(product)}"
        else "$date|$amount"
    }

    /** 三键：日期|金额|归一化商家（忽略商品）——自动/截图记录与导入账单的合并口径 */
    private fun tripleKey(date: String, amount: Long, merchant: String): String =
        "$date|$amount|${normalizeText(merchant)}"

    /** 插入一条导入交易记录（若同基础键存在手动记录且导入优先，则覆盖为导入记录）。 */
    private suspend fun insertRow(
        row: ParsedRow,
        amount: Long,
        type: String,
        category: String,
        batchId: String,
        now: Long,
        manualPriority: Boolean,
    ) {
        val key = baseKey(row.date, row.amount, row.merchant, row.product)
        val existingManual = if (manualPriority) null else transactionDao.getAll().firstOrNull {
            baseKey(it.date, it.amount, it.merchant, it.product) == key && it.source == Transaction.SOURCE_MANUAL
        }
        if (existingManual != null) {
            transactionDao.update(
                existingManual.copy(
                    amount = amount,
                    type = type,
                    category = category,
                    date = row.date,
                    time = row.time,
                    merchant = row.merchant,
                    product = row.product,
                    paymentMethod = row.paymentMethod,
                    tradeOrderNo = row.tradeOrderNo,
                    merchantOrderNo = row.merchantOrderNo,
                    source = Transaction.SOURCE_IMPORT,
                    importBatchId = batchId,
                    updatedAt = now
                )
            )
        } else {
            transactionDao.insert(
                Transaction(
                    amount = amount,
                    type = type,
                    category = category,
                    date = row.date,
                    time = row.time,
                    merchant = row.merchant,
                    product = row.product,
                    paymentMethod = row.paymentMethod,
                    tradeOrderNo = row.tradeOrderNo,
                    merchantOrderNo = row.merchantOrderNo,
                    source = Transaction.SOURCE_IMPORT,
                    importBatchId = batchId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
}

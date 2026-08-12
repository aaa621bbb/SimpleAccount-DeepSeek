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
) {

    data class ImportResult(
        val inserted: Int,
        val skipped: Int,
        val failed: Int,
        val batchId: String,
    )

    suspend fun process(
        rows: List<ParsedRow>,
        sourceType: String,
        fileName: String,
        parseSkip: Int,
        failures: List<ParseFailure> = emptyList(),
    ): ImportResult {
        val batchId = UUID.randomUUID().toString()
        val existing = transactionDao.getAll()
        val validNames = categoryRepository.getAll().map { it.name }.toHashSet()

        // 已存在的「导入」记录 key 集合（用于判重跳过）。
        // 注意：手动记录不加入 —— 同 key 的手动记录应被本次导入覆盖（终稿要求）。
        val importKeys = HashSet<String>()
        existing.filter { it.source == Transaction.SOURCE_IMPORT }
            .forEach { importKeys.add(dedupKey(it)) }

        var inserted = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        val insertedKeys = HashSet<String>()

        // --- 阶段1：先处理非退款行（含转账独立成类），为退款抵消准备"付款"候选 ---
        // 预收集本批次内"同商家付款"，用于退款抵消
        val merchantPayments = mutableMapOf<String, MutableList<Pair<Int, Long>>>() // merchant -> list of (rowIdx, amount)

        // 先把普通行入账，同时收集付款候选（仅支出，非退款/转账）
        for ((i, row) in rows.withIndex()) {
            val key = dedupKey(row)
            if (importKeys.contains(key) || insertedKeys.contains(key)) {
                skipped++
                continue
            }

            // 转账：独立"转账"支出分类
            if (row.special == RowSpecial.TRANSFER) {
                insertRow(row, row.amount, Transaction.TYPE_EXPENSE, CategoryPresets.TRANSFER_CATEGORY, batchId, now)
                insertedKeys.add(key)
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
                row.merchant, row.product, row.sourceCategory, validNames
            )
            insertRow(row, row.amount, row.type, cat, batchId, now)
            insertedKeys.add(key)
            inserted++
        }

        // --- 阶段2：处理退款行 ---
        for ((i, row) in rows.withIndex()) {
            if (row.special != RowSpecial.REFUND) continue
            val key = dedupKey(row)
            if (importKeys.contains(key) || insertedKeys.contains(key)) {
                skipped++
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
                    insertedKeys.add(key)
                    skipped++  // 退款被抵消，不新增
                    continue
                }
            }
            // 找不到可抵消的付款 → 记入收入，分类"退款"
            insertRow(row, refundAmount, Transaction.TYPE_INCOME, CategoryPresets.REFUND_CATEGORY, batchId, now)
            insertedKeys.add(key)
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
            batchId = batchId
        )
    }

    private fun dedupKey(row: ParsedRow): String {
        val hasDetail = row.merchant.isNotBlank() || row.product.isNotBlank()
        return if (hasDetail) "${row.date}|${row.amount}|${row.merchant.trim()}|${row.product.trim()}"
        else "${row.date}|${row.amount}"
    }

    private fun dedupKey(t: Transaction): String {
        val hasDetail = t.merchant.isNotBlank() || t.product.isNotBlank()
        return if (hasDetail) "${t.date}|${t.amount}|${t.merchant.trim()}|${t.product.trim()}"
        else "${t.date}|${t.amount}"
    }

    /** 插入一条导入交易记录（若同 key 存在手动记录则覆盖为导入记录）。 */
    private suspend fun insertRow(
        row: ParsedRow,
        amount: Long,
        type: String,
        category: String,
        batchId: String,
        now: Long,
    ) {
        val key = dedupKey(row)
        val existingManual = transactionDao.getAll().firstOrNull {
            dedupKey(it) == key && it.source == Transaction.SOURCE_MANUAL
        }
        if (existingManual != null) {
            transactionDao.update(
                existingManual.copy(
                    amount = amount,
                    type = type,
                    category = category,
                    date = row.date,
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

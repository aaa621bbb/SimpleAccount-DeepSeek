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
        val fallback = CategoryPresets.DEFAULT_EXPENSE_CATEGORY

        // 已存在的「导入」记录 key 集合（用于判重跳过）。
        // 注意：手动记录不加入 —— 同 key 的手动记录应被本次导入覆盖（终稿要求）。
        val importKeys = HashSet<String>()
        existing.filter { it.source == Transaction.SOURCE_IMPORT }
            .forEach { importKeys.add(dedupKey(it)) }

        var inserted = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        val insertedKeys = HashSet<String>()

        for (row in rows) {
            val key = dedupKey(row)
            if (importKeys.contains(key) || insertedKeys.contains(key)) {
                skipped++
                continue
            }

            // 自动分类
            val rawCat = classificationService.classify(row.merchant, row.product)
            val cat = if (rawCat in validNames || rawCat in listOf(fallback)) rawCat else fallback

            // 是否存在同 key 的「手动」记录 → 被本次导入覆盖
            val existingManual = existing.firstOrNull { dedupKey(it) == key && it.source == Transaction.SOURCE_MANUAL }
            if (existingManual != null) {
                transactionDao.update(
                    existingManual.copy(
                        amount = row.amount,
                        type = row.type,
                        category = cat,
                        date = row.date,
                        merchant = row.merchant,
                        product = row.product,
                        source = Transaction.SOURCE_IMPORT,
                        importBatchId = batchId,
                        updatedAt = now
                    )
                )
            } else {
                transactionDao.insert(
                    Transaction(
                        amount = row.amount,
                        type = row.type,
                        category = cat,
                        date = row.date,
                        merchant = row.merchant,
                        product = row.product,
                        source = Transaction.SOURCE_IMPORT,
                        importBatchId = batchId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
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
}

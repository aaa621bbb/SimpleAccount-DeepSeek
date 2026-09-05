package com.simpleaccount.app.data.repository

import com.simpleaccount.app.data.dao.CategoryTotal
import com.simpleaccount.app.data.dao.RangeRow
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.dao.TotalRow
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 月份卡片汇总 */
data class MonthSummary(
    val expense: Long,
    val income: Long,
) {
    val balance: Long get() = income - expense
}

@Singleton
class AccountRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {

    fun observeAll(): Flow<List<Transaction>> = transactionDao.observeAll()

    fun observeMonth(monthPrefix: String): Flow<List<Transaction>> =
        transactionDao.observeByMonth(monthPrefix)

    fun observeFiltered(monthPrefix: String?, category: String?): Flow<List<Transaction>> =
        transactionDao.observeFiltered(monthPrefix, category)

    fun observeSearch(q: String, monthPrefix: String? = null, category: String? = null): Flow<List<Transaction>> =
        transactionDao.observeSearch(q, monthPrefix, category)

    suspend fun getAll(): List<Transaction> = transactionDao.getAll()

    suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)

    suspend fun insert(t: Transaction): Long = transactionDao.insert(t)

    suspend fun update(t: Transaction) = transactionDao.update(t)

    suspend fun delete(id: Long) = transactionDao.deleteById(id)

    suspend fun getLastDate(): String? = transactionDao.lastDate()

    /** 本月收支汇总。monthPrefix 如 "2026-08"。 */
    suspend fun monthSummary(monthPrefix: String): MonthSummary {
        val rows = transactionDao.monthTotals(monthPrefix)
        return toSummary(rows)
    }

    /** 全部账目汇总（首页用不到，统计页"全部"用） */
    suspend fun allSummary(): MonthSummary {
        val rows = transactionDao.allTotals()
        return toSummary(rows)
    }

    private fun toSummary(rows: List<TotalRow>): MonthSummary {
        var expense = 0L
        var income = 0L
        rows.forEach { row ->
            val total = row.total ?: 0L
            when (row.type) {
                Transaction.TYPE_EXPENSE -> expense = total
                Transaction.TYPE_INCOME -> income = total
            }
        }
        return MonthSummary(expense = expense, income = income)
    }

    /** 最近记录（倒序前 N 条） */
    suspend fun recent(limit: Int): List<Transaction> =
        transactionDao.getAll().take(limit)

    suspend fun getAllImport(): List<Transaction> = transactionDao.getAllImport()

    suspend fun categoryTotals(monthPrefix: String, type: String): List<CategoryTotal> =
        transactionDao.categoryTotals(monthPrefix, type)

    suspend fun categoryTotalsAll(type: String): List<CategoryTotal> =
        transactionDao.categoryTotalsAll(type)

    suspend fun rangeTotals(startDate: String, endDate: String): List<RangeRow> =
        transactionDao.rangeTotals(startDate, endDate)
}

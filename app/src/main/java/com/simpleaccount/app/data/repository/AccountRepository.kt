package com.simpleaccount.app.data.repository

import com.simpleaccount.app.data.dao.CategoryTotal
import com.simpleaccount.app.data.dao.RangeRow
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.dao.TotalRow
import com.simpleaccount.app.data.entity.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/** 月份卡片汇总 */
data class MonthSummary(
    val expense: Long,
    val income: Long,
) {
    val balance: Long get() = income - expense
}

/**
 * 账本仓库。所有观察/读写都绑在「当前账本」上，AI 和统计不会串到别的账本。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AccountRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ledgerRepository: LedgerRepository,
) {

    private fun lid(): Long = ledgerRepository.currentId()

    fun observeAll(): Flow<List<Transaction>> =
        ledgerRepository.currentIdFlow.flatMapLatest { transactionDao.observeAll(it) }

    fun observeMonth(monthPrefix: String): Flow<List<Transaction>> =
        ledgerRepository.currentIdFlow.flatMapLatest { transactionDao.observeByMonth(it, monthPrefix) }

    fun observeFiltered(
        monthPrefix: String?,
        category: String?,
        type: String? = null,
    ): Flow<List<Transaction>> =
        ledgerRepository.currentIdFlow.flatMapLatest {
            transactionDao.observeFiltered(it, monthPrefix, category, type)
        }

    fun observeSearch(
        q: String,
        monthPrefix: String? = null,
        category: String? = null,
        type: String? = null,
    ): Flow<List<Transaction>> =
        ledgerRepository.currentIdFlow.flatMapLatest {
            transactionDao.observeSearch(q, it, monthPrefix, category, type)
        }

    suspend fun getAll(): List<Transaction> = transactionDao.getAll(lid())

    suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)

    suspend fun insert(t: Transaction): Long {
        val ready = if (t.id == 0L) t.copy(ledgerId = lid()) else t
        return transactionDao.insert(ready)
    }

    suspend fun update(t: Transaction) = transactionDao.update(t)

    suspend fun delete(id: Long) = transactionDao.deleteById(id)

    suspend fun getLastDate(): String? = transactionDao.lastDate()

    suspend fun monthSummary(monthPrefix: String): MonthSummary {
        var expense = 0L
        var income = 0L
        getAll().filter { it.date.startsWith(monthPrefix) }.forEach { t ->
            if (t.type == Transaction.TYPE_EXPENSE) expense += t.amount else income += t.amount
        }
        return MonthSummary(expense, income)
    }

    suspend fun allSummary(): MonthSummary {
        var expense = 0L
        var income = 0L
        getAll().forEach { t ->
            if (t.type == Transaction.TYPE_EXPENSE) expense += t.amount else income += t.amount
        }
        return MonthSummary(expense, income)
    }

    suspend fun recent(limit: Int): List<Transaction> = getAll().take(limit)

    suspend fun getAllImport(): List<Transaction> = transactionDao.getAllImport(lid())

    suspend fun categoryTotals(monthPrefix: String, type: String): List<CategoryTotal> =
        transactionDao.categoryTotals(monthPrefix, type)

    suspend fun categoryTotalsAll(type: String): List<CategoryTotal> =
        transactionDao.categoryTotalsAll(type)

    suspend fun rangeTotals(startDate: String, endDate: String): List<RangeRow> =
        transactionDao.rangeTotals(startDate, endDate)
}

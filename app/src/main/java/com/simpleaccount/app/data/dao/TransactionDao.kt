package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simpleaccount.app.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(t: Transaction): Long

    @Insert
    suspend fun insertAll(list: List<Transaction>): List<Long>

    @Update
    suspend fun update(t: Transaction)

    @Delete
    suspend fun delete(t: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    /** 按月份（yyyy-MM 前缀）筛选 */
    @Query("SELECT * FROM transactions WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC, id DESC")
    fun observeByMonth(monthPrefix: String): Flow<List<Transaction>>

    /** 按月 + 分类筛选（AND）。monthPrefix/category 传 null 表示不限 */
    @Query("""
        SELECT * FROM transactions
        WHERE (:monthPrefix IS NULL OR date LIKE :monthPrefix || '%')
          AND (:category IS NULL OR category = :category)
        ORDER BY date DESC, id DESC
    """)
    fun observeFiltered(monthPrefix: String?, category: String?): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE (note LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%'
            OR merchant LIKE '%' || :q || '%' OR product LIKE '%' || :q || '%')
          AND (:monthPrefix IS NULL OR date LIKE :monthPrefix || '%')
          AND (:category IS NULL OR category = :category)
        ORDER BY date DESC, id DESC
    """)
    fun observeSearch(q: String, monthPrefix: String?, category: String?): Flow<List<Transaction>>

    /** 全部（判重用） */
    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<Transaction>

    /** 某月收支合计 */
    @Query("SELECT SUM(amount) as total, type FROM transactions WHERE date LIKE :monthPrefix || '%' GROUP BY type")
    suspend fun monthTotals(monthPrefix: String): List<TotalRow>

    @Query("SELECT SUM(amount) as total, type FROM transactions GROUP BY type")
    suspend fun allTotals(): List<TotalRow>

    /** 某月按分类合计 */
    @Query("""
        SELECT category, SUM(amount) as total FROM transactions
        WHERE date LIKE :monthPrefix || '%' AND type = :type
        GROUP BY category
    """)
    suspend fun categoryTotals(monthPrefix: String, type: String): List<CategoryTotal>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = :type GROUP BY category")
    suspend fun categoryTotalsAll(type: String): List<CategoryTotal>

    /** 指定日期区间内的月度趋势聚合 */
    @Query("""
        SELECT date, type, SUM(amount) as total FROM transactions
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY date, type
    """)
    suspend fun rangeTotals(startDate: String, endDate: String): List<RangeRow>

    @Query("SELECT COUNT(*) FROM transactions WHERE importBatchId = :batchId")
    suspend fun countByBatch(batchId: String): Int

    @Query("SELECT DISTINCT date FROM transactions ORDER BY date DESC LIMIT 1")
    suspend fun lastDate(): String?

    @Query("SELECT * FROM transactions WHERE source = 'import' ORDER BY id DESC")
    suspend fun getAllImport(): List<Transaction>

    /** 按来源取全部（供导入时覆盖自动记账记录等） */
    @Query("SELECT * FROM transactions WHERE source = :source ORDER BY id DESC")
    suspend fun getAllBySource(source: String): List<Transaction>
}

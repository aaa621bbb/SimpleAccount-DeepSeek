package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simpleaccount.app.data.entity.Merchant
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Merchant): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Merchant>)

    @Update
    suspend fun update(m: Merchant)

    @Query("DELETE FROM merchants WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM merchants")
    suspend fun deleteAll()

    @Query("SELECT * FROM merchants ORDER BY merchant ASC")
    fun observeAll(): Flow<List<Merchant>>

    @Query("SELECT * FROM merchants WHERE status = :status ORDER BY merchant ASC")
    fun observeByStatus(status: String): Flow<List<Merchant>>

    @Query("SELECT * FROM merchants WHERE merchant LIKE '%' || :q || '%' ORDER BY merchant ASC")
    fun observeSearch(q: String): Flow<List<Merchant>>

    @Query("SELECT * FROM merchants WHERE merchant = :name LIMIT 1")
    suspend fun getByMerchant(name: String): Merchant?

    @Query("SELECT * FROM merchants")
    suspend fun getAll(): List<Merchant>

    @Query("SELECT * FROM merchants WHERE status = :status")
    suspend fun getByStatus(status: String): List<Merchant>

    /**
     * 清理孤立商家：指定状态（pending）且超期未处理、且没有任何交易引用的行。
     * 用于防商家表随导入无限膨胀；用户手动设置（user_set）永不删。
     */
    @Query(
        "DELETE FROM merchants WHERE status = :status AND updatedAt < :cutoff " +
            "AND merchant NOT IN (SELECT DISTINCT merchant FROM transactions WHERE merchant != '')"
    )
    suspend fun deleteStaleWithoutTransactions(status: String, cutoff: Long)

    /** 待清理孤立商家的数量（供清理前统计） */
    @Query(
        "SELECT COUNT(*) FROM merchants WHERE status = :status AND updatedAt < :cutoff " +
            "AND merchant NOT IN (SELECT DISTINCT merchant FROM transactions WHERE merchant != '')"
    )
    suspend fun countStaleWithoutTransactions(status: String, cutoff: Long): Int
}

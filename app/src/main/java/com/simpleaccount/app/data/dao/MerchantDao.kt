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
}

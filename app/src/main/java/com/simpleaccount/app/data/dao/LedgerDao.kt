package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simpleaccount.app.data.entity.Ledger
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(ledger: Ledger): Long

    @Update
    suspend fun update(ledger: Ledger)

    @Query("DELETE FROM ledgers WHERE id = :id AND isDefault = 0")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ledgers ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<Ledger>>

    @Query("SELECT * FROM ledgers ORDER BY isDefault DESC, id ASC")
    suspend fun getAll(): List<Ledger>

    @Query("SELECT * FROM ledgers WHERE id = :id")
    suspend fun getById(id: Long): Ledger?

    @Query("SELECT COUNT(*) FROM ledgers")
    suspend fun count(): Int
}

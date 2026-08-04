package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simpleaccount.app.data.entity.ImportFailure

@Dao
interface ImportFailureDao {

    @Insert
    suspend fun insertAll(list: List<ImportFailure>)

    @Query("DELETE FROM import_failures")
    suspend fun deleteAll()

    /** 按批次取失败明细 */
    @Query("SELECT * FROM import_failures WHERE batchId = :batchId ORDER BY id ASC")
    suspend fun getByBatch(batchId: String): List<ImportFailure>

    /** 全部失败明细（倒序） */
    @Query("SELECT * FROM import_failures ORDER BY id DESC")
    suspend fun getAll(): List<ImportFailure>
}

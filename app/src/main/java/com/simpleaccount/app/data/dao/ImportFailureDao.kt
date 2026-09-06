package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simpleaccount.app.data.entity.ImportFailure
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COUNT(*) FROM import_failures")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM import_failures")
    fun observeCount(): Flow<Int>

    /** 存量上限：只保留最近 keep 条，防止无限膨胀 */
    @Query(
        "DELETE FROM import_failures WHERE id NOT IN " +
            "(SELECT id FROM import_failures ORDER BY id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}

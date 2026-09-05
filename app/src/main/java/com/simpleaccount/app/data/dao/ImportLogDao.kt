package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simpleaccount.app.data.entity.ImportLog

@Dao
interface ImportLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ImportLog)

    @Query("DELETE FROM import_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM import_logs ORDER BY importDate DESC")
    suspend fun getAll(): List<ImportLog>

    @Query("SELECT * FROM import_logs WHERE batchId = :batchId LIMIT 1")
    suspend fun getByBatch(batchId: String): ImportLog?

    @Query("SELECT COUNT(*) FROM import_logs")
    suspend fun count(): Int

    /** 存量上限：只保留最近 keep 条（按导入时间），防止无限膨胀 */
    @Query(
        "DELETE FROM import_logs WHERE batchId NOT IN " +
            "(SELECT batchId FROM import_logs ORDER BY importDate DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}

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
}

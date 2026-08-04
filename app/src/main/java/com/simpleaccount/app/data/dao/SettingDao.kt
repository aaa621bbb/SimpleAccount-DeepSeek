package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simpleaccount.app.data.entity.Setting

@Dao
interface SettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: Setting)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Setting>)

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): Setting?

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<Setting>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}

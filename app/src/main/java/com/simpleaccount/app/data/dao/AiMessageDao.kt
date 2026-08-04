package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simpleaccount.app.data.entity.AiMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMessageDao {

    @Insert
    suspend fun insert(m: AiMessage): Long

    @Query("DELETE FROM ai_messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM ai_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<AiMessage>

    @Query("SELECT * FROM ai_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<AiMessage>>

    @Query("DELETE FROM ai_messages WHERE id NOT IN (SELECT id FROM ai_messages ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimBeyond(keep: Int)
}

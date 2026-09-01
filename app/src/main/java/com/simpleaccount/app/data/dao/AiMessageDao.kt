package com.simpleaccount.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simpleaccount.app.data.entity.AiMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMessageDao {

    @Insert
    suspend fun insert(m: AiMessage)

    @Query("DELETE FROM ai_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM ai_messages")
    suspend fun deleteAll()

    /** 单条状态更新（撤回/完成/出错） */
    @Query("UPDATE ai_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /** 某会话消息（升序，最多 limit 条；取"最后 limit 条"用子查询） */
    @Query(
        "SELECT * FROM (SELECT * FROM ai_messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp DESC LIMIT :limit) " +
            "ORDER BY timestamp ASC"
    )
    suspend fun getForConversation(conversationId: String, limit: Int): List<AiMessage>

    /** 某会话消息流（UI 常驻仅当前会话、有上限） */
    @Query(
        "SELECT * FROM (SELECT * FROM ai_messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp DESC LIMIT :limit) " +
            "ORDER BY timestamp ASC"
    )
    fun observeByConversation(conversationId: String, limit: Int): Flow<List<AiMessage>>

    @Query("SELECT COUNT(*) FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun countByConversation(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM ai_messages")
    suspend fun countGlobal(): Int

    @Query("SELECT MAX(timestamp) FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun latestTimestamp(conversationId: String): Long?

    @Query("SELECT * FROM ai_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AiMessage?

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC")
    suspend fun getForConversationAllDesc(conversationId: String): List<AiMessage>

    // ---------------- 存量上限（存储防膨胀） ----------------

    /** 单会话上限：超出 keep 条时删最旧 */
    @Query(
        "DELETE FROM ai_messages WHERE conversationId = :conversationId AND id NOT IN " +
            "(SELECT id FROM ai_messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun trimConversation(conversationId: String, keep: Int)

    /** 全局上限：全局消息超 keep 条时删最旧 */
    @Query(
        "DELETE FROM ai_messages WHERE id NOT IN " +
            "(SELECT id FROM ai_messages ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun trimGlobal(keep: Int)

    // ---------------- 兼容旧接口 ----------------

    @Query("SELECT * FROM ai_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<AiMessage>
}

package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.ConversationDao
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Conversation
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话管理器：AI 多会话的 CRUD、消息持久化、上下文裁剪的唯一入口。
 *
 * 上下文窗口规则（活跃会话生命周期 = 30 分钟，三分支判定）：
 * 1. 30 分钟内有过对话 → 取 30 分钟内的全部消息
 * 2. 30 分钟内对话不足 20 条 → 兜底取最近 20 条
 * 3. 30 分钟以上无对话（冷启动）→ 取最近 20 条
 *
 * 存储上限（防膨胀）：单会话最近 200 条、全局最近 2000 条，写入后自动修剪。
 */
@Singleton
class ConversationManager @Inject constructor(
    private val conversationDao: ConversationDao,
    private val aiMessageDao: AiMessageDao,
) {

    companion object {
        /** 活跃会话生命周期：30 分钟 */
        const val SESSION_WINDOW_MS = 30 * 60 * 1000L

        /** 上下文兜底条数 */
        const val CONTEXT_FALLBACK_COUNT = 20

        /** UI 常驻消息上限（每会话） */
        const val MESSAGES_PAGE = 300

        /** 单会话存量上限 */
        const val PER_SESSION_CAP = 200

        /** 全局消息存量上限 */
        const val GLOBAL_CAP = 2000
    }

    // ---------------- 会话 CRUD ----------------

    fun observeConversations(): Flow<List<Conversation>> = conversationDao.observeAll()

    /** 取最近活跃的会话；没有则创建一个。 */
    suspend fun ensureCurrentConversation(): Conversation {
        conversationDao.getLatest()?.let { return it }
        val c = Conversation(id = UUID.randomUUID().toString(), title = "新对话")
        conversationDao.insert(c)
        return c
    }

    suspend fun newConversation(): Conversation {
        val c = Conversation(id = UUID.randomUUID().toString(), title = "新对话")
        conversationDao.insert(c)
        return c
    }

    suspend fun getConversation(id: String): Conversation? = conversationDao.getById(id)

    suspend fun renameConversation(id: String, title: String) {
        if (title.isNotBlank()) conversationDao.setTitle(id, title.trim(), System.currentTimeMillis())
    }

    /** 删除会话及其全部消息 */
    suspend fun deleteConversation(id: String) {
        aiMessageDao.deleteByConversation(id)
        conversationDao.deleteById(id)
    }

    /** 清空某会话的消息（保留会话本身） */
    suspend fun clearMessages(conversationId: String) {
        aiMessageDao.deleteByConversation(conversationId)
    }

    // ---------------- 消息 ----------------

    /** 当前会话消息流（有上限，不常驻全量） */
    fun observeMessages(conversationId: String): Flow<List<AiMessage>> =
        aiMessageDao.observeByConversation(conversationId, MESSAGES_PAGE)

    suspend fun getMessages(conversationId: String): List<AiMessage> =
        aiMessageDao.getForConversation(conversationId, MESSAGES_PAGE)

    /** 插入一条消息并 touch 会话；返回带最终字段的实体 */
    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        toolCallId: String? = null,
        toolName: String? = null,
        status: String = AiMessage.STATUS_DONE,
    ): AiMessage {
        val m = AiMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            toolCallId = toolCallId,
            toolName = toolName,
            status = status,
        )
        aiMessageDao.insert(m)
        conversationDao.touch(conversationId, m.timestamp)
        trimCaps(conversationId)
        return m
    }

    suspend fun deleteMessage(id: String) = aiMessageDao.deleteById(id)

    /** 撤回：状态置 withdrawn，UI 显示"已撤回"占位 */
    suspend fun withdrawMessage(id: String) = aiMessageDao.updateStatus(id, AiMessage.STATUS_WITHDRAWN)

    suspend fun getMessage(id: String): AiMessage? = aiMessageDao.getById(id)

    // ---------------- 上下文裁剪 ----------------

    /**
     * 按会话窗口规则构建发给模型的历史（不含 system）。
     * 每次发送时从 DB 现取现裁，不在内存常驻。
     */
    suspend fun buildContext(conversationId: String): List<Pair<String, String>> {
        val prior = aiMessageDao.getForConversation(conversationId, MESSAGES_PAGE)
            .filter { it.status == AiMessage.STATUS_DONE }
            .filter { it.role == AiMessage.ROLE_USER || it.role == AiMessage.ROLE_ASSISTANT }
        if (prior.isEmpty()) return emptyList()

        val cutoff = System.currentTimeMillis() - SESSION_WINDOW_MS
        val withinWindow = prior.filter { it.timestamp >= cutoff }
        val chosen = if (withinWindow.isNotEmpty() && withinWindow.size >= CONTEXT_FALLBACK_COUNT) withinWindow
        else prior.takeLast(CONTEXT_FALLBACK_COUNT)
        return chosen.map { it.role to it.content }
    }

    /** 某会话最后一条用户消息内容（供"重新生成"） */
    suspend fun lastUserMessage(conversationId: String): AiMessage? =
        aiMessageDao.getForConversationAllDesc(conversationId)
            .firstOrNull { it.role == AiMessage.ROLE_USER && it.status == AiMessage.STATUS_DONE }

    /** 某会话时间上晚于某消息的全部消息 id（供"重新生成"清理旧回复） */
    suspend fun messagesAfter(conversationId: String, timestamp: Long): List<AiMessage> =
        aiMessageDao.getForConversationAllDesc(conversationId).filter { it.timestamp > timestamp }

    // ---------------- 存量修剪 ----------------

    /** 单会话 200 条 + 全局 2000 条上限，写入后调用 */
    suspend fun trimCaps(conversationId: String) {
        aiMessageDao.trimConversation(conversationId, PER_SESSION_CAP)
        aiMessageDao.trimGlobal(GLOBAL_CAP)
    }
}

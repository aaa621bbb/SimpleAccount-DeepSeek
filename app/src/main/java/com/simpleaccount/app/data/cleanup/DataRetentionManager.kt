package com.simpleaccount.app.data.cleanup

import android.content.Context
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.ImportFailureDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.db.AppDatabase
import com.simpleaccount.app.data.entity.Merchant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储防膨胀：所有"历史/日志/缓冲"类数据的上限与自动清理。
 * - 导入日志 / 失败记录：各保留最近 500 条
 * - 商家表：pending 超 30 天且无交易引用的孤立行
 * - AI 消息：全局最近 2000 条（单会话 200 条在写入时已由 ConversationManager 修剪）
 * - 大量删除后 VACUUM 回收磁盘空洞
 *
 * 触发时机：App 启动（每自然日至多一次，低频）+ 每次导入完成后（轻量部分）+ 手动"一键清理"。
 */
@Singleton
class DataRetentionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importLogDao: ImportLogDao,
    private val importFailureDao: ImportFailureDao,
    private val merchantDao: MerchantDao,
    private val aiMessageDao: AiMessageDao,
    private val db: AppDatabase,
) {

    companion object {
        const val IMPORT_LOG_KEEP = 500
        const val IMPORT_FAILURE_KEEP = 500
        const val GLOBAL_MESSAGE_CAP = 2000
        const val PENDING_MERCHANT_TTL_DAYS = 30

        private const val PREFS = "retention_prefs"
        private const val KEY_LAST_CLEANUP_DAY = "last_cleanup_day"
    }

    data class CleanupReport(
        val importLogsDeleted: Int = 0,
        val failuresDeleted: Int = 0,
        val merchantsDeleted: Int = 0,
        val messagesDeleted: Int = 0,
        val vacuumed: Boolean = false,
    ) {
        fun summary(): String = buildString {
            append("已清理：导入日志 ${importLogsDeleted} 条、失败记录 ${failuresDeleted} 条、")
            append("孤立商家 ${merchantsDeleted} 个、旧消息 ${messagesDeleted} 条")
            if (vacuumed) append("，数据库已整理（VACUUM）")
        }
    }

    /**
     * 轻量清理：每次导入完成后调用（无 VACUUM，避免频繁重建）。
     */
    suspend fun cleanupAfterImport() = withContext(Dispatchers.IO) {
        importLogDao.trimTo(IMPORT_LOG_KEEP)
        importFailureDao.trimTo(IMPORT_FAILURE_KEEP)
    }

    /**
     * 完整清理：含孤立商家、全局消息上限与 VACUUM。
     * @param force true = 手动触发（无条件执行含 VACUUM）；false = 启动时的每日低频触发
     */
    suspend fun runFullCleanup(force: Boolean): CleanupReport = withContext(Dispatchers.IO) {
        if (!force && !shouldRunToday()) return@withContext CleanupReport()

        val logsBefore = importLogDao.count()
        importLogDao.trimTo(IMPORT_LOG_KEEP)

        val failuresBefore = importFailureDao.count()
        importFailureDao.trimTo(IMPORT_FAILURE_KEEP)

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PENDING_MERCHANT_TTL_DAYS.toLong())
        val merchantsBefore = merchantDao.countStaleWithoutTransactions(Merchant.STATUS_PENDING, cutoff)
        merchantDao.deleteStaleWithoutTransactions(Merchant.STATUS_PENDING, cutoff)

        val messagesBefore = aiMessageDao.countGlobal()
        aiMessageDao.trimGlobal(GLOBAL_MESSAGE_CAP)
        val messagesAfter = aiMessageDao.countGlobal()

        // VACUUM 需要独立连接无活动事务时执行；Room 的 openHelper 直接执行即可
        var vacuumed = false
        try {
            db.openHelper.writableDatabase.execSQL("VACUUM")
            vacuumed = true
        } catch (_: Throwable) {
            // VACUUM 失败不影响数据（可能存在并发连接），下次清理再试
        }

        markCleanedToday()
        CleanupReport(
            importLogsDeleted = (logsBefore - importLogDao.count()).coerceAtLeast(0),
            failuresDeleted = (failuresBefore - importFailureDao.count()).coerceAtLeast(0),
            merchantsDeleted = merchantsBefore.coerceAtLeast(0),
            messagesDeleted = (messagesBefore - messagesAfter).coerceAtLeast(0),
            vacuumed = vacuumed
        )
    }

    private fun shouldRunToday(): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) * 10000 +
            Calendar.getInstance().get(Calendar.YEAR)
        return prefs.getInt(KEY_LAST_CLEANUP_DAY, -1) != today
    }

    private fun markCleanedToday() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR) * 10000 + cal.get(Calendar.YEAR)
        prefs.edit().putInt(KEY_LAST_CLEANUP_DAY, today).apply()
    }
}

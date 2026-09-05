package com.simpleaccount.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.simpleaccount.app.data.cleanup.DataRetentionManager
import com.simpleaccount.app.data.dao.CategoryDao
import com.simpleaccount.app.util.AppLog
import com.simpleaccount.app.util.CategoryPresets
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SimpleAccountApp : Application() {

    @Inject
    lateinit var categoryDao: CategoryDao

    @Inject
    lateinit var dataRetentionManager: DataRetentionManager

    @Inject
    lateinit var ledgerRepository: com.simpleaccount.app.data.repository.LedgerRepository

    @Inject
    lateinit var settingsRepository: com.simpleaccount.app.data.repository.SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 初始化日志工具（写入文件 + 捕获崩溃），无需 USB 调试即可抓取日志
        AppLog.init(this)
        AppLog.installCrashHandler()
        AppLog.d("App onCreate")
        // 首次启动保证预置分类存在
        appScope.launch {
            ensurePresetCategories()
            runCatching { ledgerRepository.ensureDefault() }
            if (settingsRepository.isAutoRecordEnabled()) {
                com.simpleaccount.app.auto.AutoRecordKeepAliveService.start(this@SimpleAccountApp)
            }
        }
        // 存储防膨胀：每日启动低频自动清理（日志/失败记录/孤立商家/旧消息上限）
        appScope.launch {
            runCatching { dataRetentionManager.runFullCleanup(force = false) }
                .onFailure { AppLog.w("retention cleanup failed: ${it.message}") }
        }
    }

    /** 低内存回调：释放非必要内存（清空日志环形缓冲） */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AppLog.clearRing()
        }
    }

    private suspend fun ensurePresetCategories() {
        val existing = categoryDao.count()
        if (existing == 0) {
            categoryDao.insertAll(CategoryPresets.presetCategories())
        }
    }
}

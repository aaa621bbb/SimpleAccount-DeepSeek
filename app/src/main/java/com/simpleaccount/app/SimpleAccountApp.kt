package com.simpleaccount.app

import android.app.Application
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
        }
    }

    private suspend fun ensurePresetCategories() {
        val existing = categoryDao.count()
        if (existing == 0) {
            categoryDao.insertAll(CategoryPresets.presetCategories())
        }
    }
}

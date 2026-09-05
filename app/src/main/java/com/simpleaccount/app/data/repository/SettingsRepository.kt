package com.simpleaccount.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.entity.Setting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 相关设置 + 外观设置。
 * 普通值（开关/BaseUrl/模型名/主题模式）存 Room Setting 表；
 * API Key 存 EncryptedSharedPreferences（Keystore + AES-GCM）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingDao: SettingDao,
) {
    companion object {
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_KEY = "ai_api_key"
        const val KEY_AI_PROVIDER_ID = "ai_provider_id"

        /** 识图 API Key（独立于主 Key，存加密 prefs） */
        const val KEY_VISION_KEY = "vision_api_key"

        /** 外观模式：system 跟随系统 / light 浅色 / dark 深色 */
        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** 每月预算（单位：分，0 = 未设置） */
        const val KEY_MONTHLY_BUDGET = "monthly_budget_fen"

        /** 无感记账总开关 */
        const val KEY_AUTO_RECORD = "auto_record_enabled"

        /** 数据冲突优先级：import=以导入账单为准 / manual=以手动·截图·AI记录为准 */
        const val KEY_IMPORT_PRIORITY = "import_priority"
        const val PRIORITY_IMPORT = "import"
        const val PRIORITY_MANUAL = "manual"

        /** 首页最近记录条数 */
        const val KEY_RECENT_COUNT = "recent_count"

        /** 截图记账用的视觉模型（需支持 image_url 输入，如 glm-4v-flash / qwen-vl 系列） */
        const val KEY_VISION_MODEL = "vision_model"
        const val DEFAULT_VISION_MODEL = "glm-4v-flash"

        /** 识图专用接口地址（与主接口独立，默认智谱） */
        const val KEY_VISION_BASE_URL = "vision_base_url"
        const val DEFAULT_VISION_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"

        /** 主模型是多模态时优先直接用它识图（失败自动回退独立识图配置） */
        const val KEY_VISION_USE_MAIN = "vision_use_main_model"

        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-chat"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_ai_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun setAiEnabled(enabled: Boolean) = setSetting(KEY_AI_ENABLED, enabled.toString())
    fun isAiEnabled(): Boolean = readSetting(KEY_AI_ENABLED) == "true"

    suspend fun setBaseUrl(url: String) = setSetting(KEY_AI_BASE_URL, url)
    fun baseUrl(): String = readSetting(KEY_AI_BASE_URL) ?: DEFAULT_BASE_URL

    suspend fun setModel(model: String) = setSetting(KEY_AI_MODEL, model)
    fun model(): String = readSetting(KEY_AI_MODEL) ?: DEFAULT_MODEL

    suspend fun setProviderId(id: String) = setSetting(KEY_AI_PROVIDER_ID, id)
    fun providerId(): String = readSetting(KEY_AI_PROVIDER_ID).orEmpty()

    fun apiKey(): String = prefs.getString(KEY_AI_KEY, "") ?: ""
    fun setApiKey(key: String) {
        prefs.edit { putString(KEY_AI_KEY, key) }
    }

    // ---------------- 外观（主题模式） ----------------

    /** 主题模式的响应式流：切换后 MainActivity 收集并即时重建主题 */
    private val _themeMode = MutableStateFlow(themeMode())
    val themeModeFlow: StateFlow<String> = _themeMode.asStateFlow()

    fun themeMode(): String {
        val v = readSetting(KEY_THEME_MODE)
        return if (v == THEME_LIGHT || v == THEME_DARK || v == THEME_SYSTEM) v else THEME_SYSTEM
    }

    suspend fun setThemeMode(mode: String) {
        setSetting(KEY_THEME_MODE, mode)
        _themeMode.value = mode
    }

    // ---------------- 数据冲突优先级 ----------------

    private val _importPriority = MutableStateFlow(importPriority())
    val importPriorityFlow: StateFlow<String> = _importPriority.asStateFlow()

    fun importPriority(): String {
        val v = readSetting(KEY_IMPORT_PRIORITY)
        return if (v == PRIORITY_MANUAL) PRIORITY_MANUAL else PRIORITY_IMPORT
    }

    suspend fun setImportPriority(p: String) {
        setSetting(KEY_IMPORT_PRIORITY, p)
        _importPriority.value = p
    }

    // ---------------- 首页最近记录条数 ----------------

    private val _recentCount = MutableStateFlow(recentCount())
    val recentCountFlow: StateFlow<Int> = _recentCount.asStateFlow()

    fun recentCount(): Int = readSetting(KEY_RECENT_COUNT)?.toIntOrNull()?.coerceIn(5, 100) ?: 20

    suspend fun setRecentCount(n: Int) {
        setSetting(KEY_RECENT_COUNT, n.toString())
        _recentCount.value = n.coerceIn(5, 100)
    }

    // ---------------- 每月预算 ----------------

    /** 每月预算（分）；未设置为 0 */
    fun monthlyBudget(): Long = readSetting(KEY_MONTHLY_BUDGET)?.toLongOrNull() ?: 0L

    suspend fun setMonthlyBudget(fen: Long) {
        setSetting(KEY_MONTHLY_BUDGET, fen.toString())
    }

    // ---------------- 无感记账（通知监听自动记账） ----------------

    fun isAutoRecordEnabled(): Boolean = readSetting(KEY_AUTO_RECORD) == "true"

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        setSetting(KEY_AUTO_RECORD, enabled.toString())
    }

    // ---------------- 截图记账（视觉模型，独立接口+独立Key） ----------------

    /** 识图接口地址（默认智谱） */
    fun visionBaseUrl(): String = readSetting(KEY_VISION_BASE_URL)?.takeIf { it.isNotBlank() }
        ?: DEFAULT_VISION_BASE_URL

    suspend fun setVisionBaseUrl(url: String) {
        setSetting(KEY_VISION_BASE_URL, url.trim())
    }

    /** 识图模型 */
    fun visionModel(): String = readSetting(KEY_VISION_MODEL)?.takeIf { it.isNotBlank() }
        ?: DEFAULT_VISION_MODEL

    suspend fun setVisionModel(model: String) {
        setSetting(KEY_VISION_MODEL, model.trim())
    }

    /** 识图 API Key（与主 Key 独立，存加密 prefs） */
    fun visionApiKey(): String = prefs.getString(KEY_VISION_KEY, "") ?: ""

    fun setVisionApiKey(key: String) {
        prefs.edit { putString(KEY_VISION_KEY, key) }
    }

    /** 优先用主模型识图（主模型是多模态时开；失败自动回退独立识图配置）。默认开 */
    fun useMainModelForVision(): Boolean = readSetting(KEY_VISION_USE_MAIN) != "false"

    suspend fun setUseMainModelForVision(use: Boolean) {
        setSetting(KEY_VISION_USE_MAIN, use.toString())
    }

    private suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.insert(Setting(key, value))
    }

    private fun readSetting(key: String): String? = runBlocking { settingDao.get(key)?.value }

    /** 清空所有 AI 设置（含加密 key） */
    suspend fun clearAll() {
        settingDao.deleteByKey(KEY_AI_ENABLED)
        settingDao.deleteByKey(KEY_AI_BASE_URL)
        settingDao.deleteByKey(KEY_AI_MODEL)
        withContext(Dispatchers.IO) {
            prefs.edit { clear() }
        }
    }
}

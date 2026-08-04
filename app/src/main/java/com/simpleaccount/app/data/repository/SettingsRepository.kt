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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 相关设置。
 * 普通值（开关/BaseUrl/模型名）存 Room Setting 表；
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

    fun apiKey(): String = prefs.getString(KEY_AI_KEY, "") ?: ""
    fun setApiKey(key: String) {
        prefs.edit { putString(KEY_AI_KEY, key) }
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

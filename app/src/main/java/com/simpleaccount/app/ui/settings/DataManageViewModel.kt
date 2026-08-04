package com.simpleaccount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.dao.ImportLogDao
import com.simpleaccount.app.data.dao.MerchantDao
import com.simpleaccount.app.data.dao.SettingDao
import com.simpleaccount.app.data.dao.TransactionDao
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.util.CategoryPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DataManageUiState(
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class DataManageViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionDao: TransactionDao,
    private val merchantDao: MerchantDao,
    private val importLogDao: ImportLogDao,
    private val aiMessageDao: AiMessageDao,
    private val settingDao: SettingDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DataManageUiState())
    val state = _state.asStateFlow()

    /** 生成导出 JSON 字符串 */
    suspend fun buildExportJson(): String {
        val json = JSONObject()
        val txArr = JSONArray()
        accountRepository.getAll().forEach { t ->
            txArr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("amount", t.amount)
                    .put("type", t.type)
                    .put("category", t.category)
                    .put("date", t.date)
                    .put("note", t.note)
                    .put("merchant", t.merchant)
                    .put("product", t.product)
                    .put("source", t.source)
                    .put("importBatchId", t.importBatchId ?: "")
                    .put("createdAt", t.createdAt)
                    .put("updatedAt", t.updatedAt)
            )
        }
        val catArr = JSONArray()
        categoryRepository.getAll().forEach { c ->
            catArr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("type", c.type)
                    .put("sortOrder", c.sortOrder)
                    .put("isPreset", c.isPreset)
                    .put("iconName", c.iconName)
                    .put("colorHex", c.colorHex)
            )
        }
        val merArr = JSONArray()
        merchantDao.getAll().forEach { m ->
            merArr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("merchant", m.merchant)
                    .put("category", m.category)
                    .put("status", m.status)
                    .put("createdAt", m.createdAt)
                    .put("updatedAt", m.updatedAt)
            )
        }
        json.put("transactions", txArr)
        json.put("categories", catArr)
        json.put("merchants", merArr)
        json.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
        return json.toString(2)
    }

    /** 建议默认文件名 */
    fun defaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "simple_account_backup_${sdf.format(Date())}.json"
    }

    /** 清空所有数据：Room 全部表 + EncryptedSharedPreferences，随后重建预置分类 */
    suspend fun clearAllData(): Boolean {
        transactionDao.deleteAll()
        merchantDao.deleteAll()
        importLogDao.deleteAll()
        aiMessageDao.deleteAll()
        settingDao.deleteAll()
        categoryRepository.deleteAll()
        settingsRepository.clearAll()
        // 重建预置分类
        categoryRepository.addAll(CategoryPresets.presetCategories())
        return true
    }
}

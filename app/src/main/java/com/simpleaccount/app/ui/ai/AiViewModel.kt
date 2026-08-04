package com.simpleaccount.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val loading: Boolean = false,
    val typing: Boolean = false,
    val input: String = "",
    val error: String? = null,
    val pendingCount: Int = 0,
    val enabled: Boolean = false,
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiMessageDao: AiMessageDao,
    private val settingsRepository: SettingsRepository,
    private val aiService: AiService,
    private val merchantRepository: MerchantRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { loadInitial() }
    }

    private suspend fun loadInitial() {
        val enabled = settingsRepository.isAiEnabled()
        val msgs = aiMessageDao.getAll()
        val pending = merchantRepository.getByStatus(Merchant.STATUS_PENDING).size
        val welcome = if (msgs.isEmpty()) {
            listOf(
                AiMessage(
                    role = "assistant",
                    content = "你好，我是记账助手，可以帮你归类商家或回答记账问题。",
                    timestamp = System.currentTimeMillis()
                )
            )
        } else msgs
        if (msgs.isEmpty()) {
            aiMessageDao.insert(welcome[0])
        }
        _state.value = _state.value.copy(
            messages = if (msgs.isEmpty()) welcome else msgs,
            enabled = enabled,
            pendingCount = pending,
            loading = false
        )
    }

    fun onInputChange(v: String) {
        _state.value = _state.value.copy(input = v)
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val enabled = _state.value.enabled
        if (!enabled) {
            _state.value = _state.value.copy(error = "AI 功能未开启，请到「我的→AI辅助设置」开启并配置")
            return
        }
        viewModelScope.launch {
            val userMsg = AiMessage(role = "user", content = trimmed, timestamp = System.currentTimeMillis())
            aiMessageDao.insert(userMsg)
            val newList = _state.value.messages + userMsg
            aiMessageDao.trimBeyond(200)
            // 保留最近 20 条作为上下文
            val context = newList.takeLast(20)
            _state.value = _state.value.copy(messages = newList, input = "", typing = true, error = null)
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                _state.value = _state.value.copy(
                    messages = newList + AiMessage(
                        role = "assistant",
                        content = "未配置 API Key，请先在设置中填写。",
                        timestamp = System.currentTimeMillis()
                    ),
                    typing = false
                )
                return@launch
            }
            val result = aiService.chat(
                baseUrl = settingsRepository.baseUrl(),
                apiKey = apiKey,
                model = settingsRepository.model(),
                messages = context.map { it.role to it.content }
            )
            val replyMsg = AiMessage(
                role = "assistant",
                content = result.error ?: result.content,
                timestamp = System.currentTimeMillis()
            )
            aiMessageDao.insert(replyMsg)
            aiMessageDao.trimBeyond(200)
            _state.value = _state.value.copy(
                messages = _state.value.messages + replyMsg,
                typing = false
            )
        }
    }

    /** 批量归类 pending 商家（每批 ≤20） */
    fun classifyPendingMerchants() {
        val enabled = _state.value.enabled
        if (!enabled) {
            _state.value = _state.value.copy(error = "AI 未开启，无法批量归类")
            return
        }
        viewModelScope.launch {
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                _state.value = _state.value.copy(error = "未配置 API Key")
                return@launch
            }
            _state.value = _state.value.copy(typing = true, error = null)
            val pendings = merchantRepository.getByStatus(Merchant.STATUS_PENDING)
            if (pendings.isEmpty()) {
                _state.value = _state.value.copy(typing = false, error = "当前没有待归类的商家")
                return@launch
            }
            val validCategories = categoryRepository.getAll()
                .map { it.name }
                .filter { it != "其它" }
            var done = 0
            pendings.chunked(20).forEach { batch ->
                val prompt = buildClassifyPrompt(batch, validCategories)
                val result = aiService.chat(
                    baseUrl = settingsRepository.baseUrl(),
                    apiKey = apiKey,
                    model = settingsRepository.model(),
                    messages = listOf("user" to prompt)
                )
                if (result.error != null) {
                    _state.value = _state.value.copy(error = "归类失败：${result.error}")
                    return@launch
                }
                val parsed = parseClassifyResult(result.content, validCategories)
                applyClassification(parsed)
                done += batch.size
            }
            // 更新 pending 数
            val pendingLeft = merchantRepository.getByStatus(Merchant.STATUS_PENDING).size
            _state.value = _state.value.copy(typing = false, pendingCount = pendingLeft, error = "已完成 $done 个商家归类")
        }
    }

    /**
     * AI 输出格式要求：JSON {"merchant":"商家名1":"分类","商家名2":"分类"...}
     * 实际更宽容解析：多行 "商家名=分类" 或 JSON。
     */
    private suspend fun parseClassifyResult(text: String, validCategories: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: runCatching { JSONObject(text.substringAfter("```json").substringBefore("```")) }.getOrNull()
        if (json != null) {
            json.keys().forEach { k ->
                val v = json.optString(k)
                if (v.isNotBlank() && v in validCategories) map[k] = v
            }
        } else {
            text.lines().forEach { line ->
                val parts = line.trim().split(Regex("[=:：]"), limit = 2)
                if (parts.size == 2) {
                    val merchant = parts[0].trim()
                    val cat = Regex("\\s+").replace(parts[1].trim(), "")
                    if (merchant.isNotEmpty() && cat in validCategories) map[merchant] = cat
                }
            }
        }
        return map
    }

    private suspend fun applyClassification(map: Map<String, String>) {
        for ((merchant, category) in map) {
            val existing = merchantRepository.getByMerchant(merchant) ?: continue
            if (existing.status == Merchant.STATUS_USER_SET) continue
            merchantRepository.update(
                existing.copy(category = category, status = Merchant.STATUS_CLASSIFIED, updatedAt = System.currentTimeMillis())
            )
            // 历史追改：只改 source='import'
            accountRepository.getAllImport()
                .filter { it.merchant.trim() == merchant }
                .forEach { t ->
                    accountRepository.update(
                        t.copy(category = category, updatedAt = System.currentTimeMillis())
                    )
                }
        }
    }

    private fun buildClassifyPrompt(batch: List<Merchant>, validCategories: List<String>): String {
        val list = batch.joinToString("\n") { it.merchant }
        return """我是一名记账助手。请为下面的商家名分类（只看商家名推断消费类型）。
合法分类：${validCategories.joinToString("、")}
如果没有把握的，给"其它"。
请严格只返回 JSON 对象，键为商家名，值为分类，例如：
{"瑞幸咖啡":"餐饮"}
商家列表：
$list"""
    }
}

package com.simpleaccount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiProvider(
    val name: String,
    val baseUrl: String,
    val models: List<String>,
)

/** AI 提供商预设：选厂商自动填 baseUrl，模型从列表选择（也允许自定义）。 */
val AI_PROVIDERS = listOf(
    AiProvider("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner")),
    AiProvider("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")),
    AiProvider("阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen-plus", "qwen-turbo", "qwen-max")),
    AiProvider("智谱GLM", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-4-flash", "glm-4")),
    AiProvider("Kimi(Moonshot)", "https://api.moonshot.cn/v1", listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")),
)

data class AiSettingsUiState(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val showKey: Boolean = false,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiSettingsUiState())
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val repoBaseUrl = settingsRepository.baseUrl()
            val repoModel = settingsRepository.model()
            // 首次打开且未配置 baseUrl 时，自动填 DeepSeek 预设（默认厂商，开箱即用）
            val effectiveBaseUrl = repoBaseUrl.ifBlank { AI_PROVIDERS[0].baseUrl }
            val effectiveModel = repoModel.ifBlank { AI_PROVIDERS[0].models.first() }
            _state.value = AiSettingsUiState(
                enabled = settingsRepository.isAiEnabled(),
                baseUrl = effectiveBaseUrl,
                apiKey = settingsRepository.apiKey(),
                model = effectiveModel,
            )
        }
    }

    /** 选择厂商预设：自动填 baseUrl + 首个模型。 */
    fun selectProvider(provider: AiProvider) {
        _state.value = _state.value.copy(baseUrl = provider.baseUrl, model = provider.models.first())
    }

    fun onEnabledChange(v: Boolean) {
        _state.value = _state.value.copy(enabled = v)
        viewModelScope.launch { settingsRepository.setAiEnabled(v) }
    }

    fun onBaseUrlChange(v: String) {
        _state.value = _state.value.copy(baseUrl = v)
    }

    fun onKeyChange(v: String) {
        _state.value = _state.value.copy(apiKey = v)
    }

    fun onModelChange(v: String) {
        _state.value = _state.value.copy(model = v)
    }

    fun toggleKeyVisibility() {
        _state.value = _state.value.copy(showKey = !_state.value.showKey)
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.setBaseUrl(_state.value.baseUrl.ifBlank { SettingsRepository.DEFAULT_BASE_URL })
            settingsRepository.setModel(_state.value.model.ifBlank { SettingsRepository.DEFAULT_MODEL })
            settingsRepository.setApiKey(_state.value.apiKey)
        }
    }
}

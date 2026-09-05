package com.simpleaccount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<String>,
    /** material-icons 图标名（Icons.Filled.* 的字段名，如 "SmartToy"） */
    val iconName: String = "SmartToy",
    /** 品牌主色（ARGB） */
    val color: Long = 0xFF4CAF50,
    /** 卡片副标题，用来区分共用网关的厂商 */
    val subtitle: String = "",
)

/** AI 提供商预设。id 必须唯一——小米 MiMo 与硅基流动共用同一网关，不能用 URL 判断选中。 */
val AI_PROVIDERS = listOf(
    AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
        listOf("deepseek-v4-flash", "deepseek-chat", "deepseek-reasoner"),
        iconName = "Psychology", color = 0xFF4A6CF7, subtitle = "官方接口"),
    AiProvider("openai", "OpenAI", "https://api.openai.com/v1",
        listOf("gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "gpt-4o", "gpt-4o-mini", "o3", "o4-mini", "o1", "gpt-3.5-turbo"),
        iconName = "Hub", color = 0xFF4CAF50, subtitle = "官方接口"),
    AiProvider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
        listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-2.0-flash-lite"),
        iconName = "Public", color = 0xFF4285F4, subtitle = "OpenAI 兼容"),
    AiProvider("claude", "Anthropic Claude", "https://api.anthropic.com/v1",
        listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022"),
        iconName = "Science", color = 0xFFB8A86B, subtitle = "官方接口"),
    AiProvider("qwen", "阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1",
        listOf("qwen3-max", "qwen3-plus", "qwen3-flash", "qwen-max", "qwen-plus", "qwen-turbo",
            "qwen-long", "qwen2.5-72b-instruct", "qwen2.5-32b-instruct", "qwq-32b"),
        iconName = "Cloud", color = 0xFFFF7043, subtitle = "兼容模式"),
    AiProvider("glm", "智谱GLM", "https://open.bigmodel.cn/api/paas/v4",
        listOf("glm-4.6", "glm-4.5", "glm-4.5-air", "glm-4.5-flash", "glm-4-plus", "glm-4-air", "glm-4-flash", "glm-4-long"),
        iconName = "AutoAwesome", color = 0xFFAB47BC, subtitle = "官方接口"),
    AiProvider("kimi", "Kimi(Moonshot)", "https://api.moonshot.cn/v1",
        listOf("kimi-k2-turbo-preview", "kimi-k2-0905-preview", "kimi-latest",
            "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k", "moonshot-v1-auto"),
        iconName = "FlashOn", color = 0xFF00BCD4, subtitle = "官方接口"),
    AiProvider("minimax", "MiniMax", "https://api.minimaxi.com/v1",
        listOf("MiniMax-M2", "MiniMax-M1", "MiniMax-Text-01", "abab6.5s-chat", "abab6.5g-chat"),
        iconName = "RocketLaunch", color = 0xFF3F51B5, subtitle = "官方接口"),
    AiProvider("doubao", "字节豆包", "https://ark.cn-beijing.volces.com/api/v3",
        listOf("doubao-seed-1.6", "doubao-seed-1.6-flash", "doubao-1.5-pro-256k", "doubao-1.5-pro-32k",
            "doubao-1.5-lite-32k", "doubao-pro-128k", "doubao-pro-32k", "doubao-lite-32k"),
        iconName = "ElectricBolt", color = 0xFF00A3FF, subtitle = "火山方舟"),
    AiProvider("hunyuan", "腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1",
        listOf("hunyuan-turbos-latest", "hunyuan-t1-latest", "hunyuan-turbo", "hunyuan-pro", "hunyuan-standard", "hunyuan-lite"),
        iconName = "Star", color = 0xFF2D6CDF, subtitle = "官方接口"),
    AiProvider("xiaomi", "小米MiMo", "https://api.siliconflow.cn/v1",
        listOf("XiaomiMiMo/MiMo-7B-RL", "XiaomiMiMo/MiMo-VL-7B-RL"),
        iconName = "LooksOne", color = 0xFF7C4DFF, subtitle = "硅基流动通道"),
    AiProvider("siliconflow", "硅基流动(聚合)", "https://api.siliconflow.cn/v1",
        listOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "moonshotai/Kimi-K2-Instruct",
            "Qwen/Qwen2.5-72B-Instruct", "Qwen/Qwen2.5-32B-Instruct", "THUDM/glm-4-9b-chat"),
        iconName = "AccountTree", color = 0xFF00B8A9, subtitle = "聚合多家模型"),
)

/** 根据已存的 providerId / 模型名 / 接口地址推断当前厂商（共用网关时以模型名为准） */
fun inferProviderId(providerId: String, baseUrl: String, model: String): String {
    fun host(url: String) = url.replace("https://", "").replace("http://", "").trimEnd('/')
    val bh = host(baseUrl)
    val current = AI_PROVIDERS.firstOrNull { it.id == providerId }
    if (current != null) {
        val ph = host(current.baseUrl)
        if (bh == ph || bh.contains(ph) || ph.contains(bh)) return providerId
    }
    if (model.contains("MiMo", true) || model.contains("XiaomiMiMo", true)) return "xiaomi"
    val matches = AI_PROVIDERS.filter {
        val ph = host(it.baseUrl)
        bh == ph || bh.contains(ph) || ph.contains(bh)
    }
    if (matches.size == 1) return matches[0].id
    if (matches.size > 1) {
        matches.firstOrNull { it.models.any { m -> m == model } }?.id?.let { return it }
        return matches.firstOrNull { it.id != "xiaomi" }?.id ?: matches.first().id
    }
    return "custom"
}

data class AiSettingsUiState(
    val enabled: Boolean = false,
    val providerId: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val visionBaseUrl: String = "",
    val visionApiKey: String = "",
    val visionModel: String = "",
    /** 主模型是多模态时，优先直接用它识图（失败自动回退独立识图配置） */
    val useMainForVision: Boolean = true,
    val showKey: Boolean = false,
    /** 从接口拉到的可用模型列表（借鉴 OpenMinis：填好 Key 自动 GET /models） */
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val thinkingLevel: String = SettingsRepository.THINKING_OFF,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
    private val aiService: AiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AiSettingsUiState())
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var keyDebounceJob: Job? = null

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
            val pid = inferProviderId(settingsRepository.providerId(), effectiveBaseUrl, effectiveModel)
            _state.value = AiSettingsUiState(
                enabled = settingsRepository.isAiEnabled(),
                providerId = pid,
                baseUrl = effectiveBaseUrl,
                apiKey = settingsRepository.apiKey(),
                model = effectiveModel,
                visionBaseUrl = settingsRepository.visionBaseUrl(),
                visionApiKey = settingsRepository.visionApiKey(),
                visionModel = settingsRepository.visionModel(),
                useMainForVision = settingsRepository.useMainModelForVision(),
                thinkingLevel = settingsRepository.thinkingLevel(),
            )
            // 已配好 Key → 自动拉取该接口的可用模型列表
            if (settingsRepository.apiKey().isNotBlank()) refreshModels()
        }
    }

    /**
     * 拉取模型列表（GET /models）。成功后芯片展示真实可用模型；
     * 失败（接口不支持/网络）保留当前列表。
     */
    fun refreshModels() {
        val base = _state.value.baseUrl
        val key = _state.value.apiKey
        if (base.isBlank() || key.isBlank()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingModels = true)
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                aiService.getModels(base, key)
            }
            if (list.isNotEmpty()) {
                _state.value = _state.value.copy(models = list.sorted(), loadingModels = false)
            } else {
                _state.value = _state.value.copy(loadingModels = false)
            }
        }
    }

    /** 选择厂商预设：按 id 选中（即使两家共用同一网关也不会串选）。 */
    fun selectProvider(provider: AiProvider) {
        _state.value = _state.value.copy(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            model = provider.models.first(),
            models = emptyList(),
        )
    }

    fun onEnabledChange(v: Boolean) {
        _state.value = _state.value.copy(enabled = v)
        viewModelScope.launch { settingsRepository.setAiEnabled(v) }
    }

    fun onBaseUrlChange(v: String) {
        _state.value = _state.value.copy(baseUrl = v)
        scheduleModelsFetch()
    }

    fun onKeyChange(v: String) {
        _state.value = _state.value.copy(apiKey = v)
        scheduleModelsFetch()
    }

    /** Key/接口变化后 800ms 防抖自动拉取模型列表 */
    private fun scheduleModelsFetch() {
        keyDebounceJob?.cancel()
        keyDebounceJob = viewModelScope.launch {
            delay(800)
            refreshModels()
        }
    }

    fun onModelChange(v: String) {
        _state.value = _state.value.copy(model = v)
    }

    fun onVisionModelChange(v: String) {
        _state.value = _state.value.copy(visionModel = v)
    }

    fun onVisionBaseUrlChange(v: String) {
        _state.value = _state.value.copy(visionBaseUrl = v)
    }

    fun onVisionKeyChange(v: String) {
        _state.value = _state.value.copy(visionApiKey = v)
    }

    fun setUseMainForVision(use: Boolean) {
        _state.value = _state.value.copy(useMainForVision = use)
        viewModelScope.launch { settingsRepository.setUseMainModelForVision(use) }
    }

    fun toggleKeyVisibility() {
        _state.value = _state.value.copy(showKey = !_state.value.showKey)
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.setBaseUrl(_state.value.baseUrl.ifBlank { SettingsRepository.DEFAULT_BASE_URL })
            settingsRepository.setModel(_state.value.model.ifBlank { SettingsRepository.DEFAULT_MODEL })
            settingsRepository.setProviderId(_state.value.providerId)
            settingsRepository.setApiKey(_state.value.apiKey)
            settingsRepository.setVisionBaseUrl(_state.value.visionBaseUrl.ifBlank { SettingsRepository.DEFAULT_VISION_BASE_URL })
            settingsRepository.setVisionApiKey(_state.value.visionApiKey)
            settingsRepository.setVisionModel(_state.value.visionModel.ifBlank { SettingsRepository.DEFAULT_VISION_MODEL })
            settingsRepository.setUseMainModelForVision(_state.value.useMainForVision)
            settingsRepository.setThinkingLevel(_state.value.thinkingLevel)
        }
    }

    fun setThinkingLevel(level: String) {
        _state.value = _state.value.copy(thinkingLevel = level)
        viewModelScope.launch { settingsRepository.setThinkingLevel(level) }
    }
}

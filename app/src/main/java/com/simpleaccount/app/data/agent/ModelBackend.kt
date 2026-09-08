package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.ondevice.OnDeviceInferenceEngine
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import com.simpleaccount.app.data.service.ToolChatMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型后端抽象（可插拔）：云侧大模型与端侧小模型无缝切换。
 *
 * - 云侧：[CloudModelBackend]，走 OpenAI 兼容 HTTP（AiService）；
 * - 端侧：[OnDeviceModelBackend]，经 [OnDeviceInferenceEngine] 本地推理：
 *   GPU（MNN/Vulkan）优先，回退 CPU（GGUF），再回退内置精简规划器。
 *   模型就位后飞行模式可用，无账号、数据不出设备。
 *
 * AgentLoop 只依赖本抽象；切换后端只需改一个设置项。
 */
data class BackendChatResult(
    val content: String,
    val toolCalls: List<AgentToolCall> = emptyList(),
    val error: String? = null,
)

interface ModelBackend {
    /** 后端 id：cloud / ondevice。 */
    val id: String

    /** 展示名。 */
    val displayName: String

    /** 是否支持 function calling 工具协议。 */
    val supportsTools: Boolean

    /** 是否使用精简提示词（小模型档）。 */
    val compactPrompt: Boolean

    suspend fun chat(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        thinkingLevel: String,
        onDelta: ((String) -> Unit)?,
        onReasoning: ((String) -> Unit)?,
    ): BackendChatResult
}

/** 云端大模型后端：复用现有 OpenAI 兼容通道。 */
@Singleton
class CloudModelBackend @Inject constructor(
    private val aiService: AiService,
    private val settingsRepository: SettingsRepository,
) : ModelBackend {
    override val id: String = SettingsRepository.BACKEND_CLOUD
    override val displayName: String = "云端大模型"
    override val supportsTools: Boolean = true
    override val compactPrompt: Boolean = false

    override suspend fun chat(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        thinkingLevel: String,
        onDelta: ((String) -> Unit)?,
        onReasoning: ((String) -> Unit)?,
    ): BackendChatResult {
        val r = aiService.chatWithTools(
            baseUrl = settingsRepository.baseUrl(),
            apiKey = settingsRepository.apiKey(),
            model = settingsRepository.model(),
            messages = messages,
            tools = tools,
            onDelta = onDelta,
            thinkingLevel = thinkingLevel,
            onReasoning = onReasoning,
        )
        return BackendChatResult(r.content, r.toolCalls, r.error)
    }
}

/**
 * 端侧小模型后端：本地推理，全离线。
 * 模型未下载时返回可操作的引导，不编造任何账本结果。
 */
@Singleton
class OnDeviceModelBackend @Inject constructor(
    private val engine: OnDeviceInferenceEngine,
) : ModelBackend {
    override val id: String = SettingsRepository.BACKEND_ONDEVICE
    override val displayName: String = "端侧小模型"
    override val supportsTools: Boolean = true
    override val compactPrompt: Boolean = true

    override suspend fun chat(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        thinkingLevel: String,
        onDelta: ((String) -> Unit)?,
        onReasoning: ((String) -> Unit)?,
    ): BackendChatResult {
        return engine.chat(
            messages = messages,
            tools = tools,
            onDelta = onDelta,
            onReasoning = onReasoning,
        )
    }
}

/** 后端选择器：按用户设置返回当前后端（默认云端）。 */
@Singleton
class ModelBackendProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloud: CloudModelBackend,
    private val onDevice: OnDeviceModelBackend,
) {
    fun current(): ModelBackend =
        if (settingsRepository.modelBackend() == SettingsRepository.BACKEND_ONDEVICE) onDevice else cloud
}

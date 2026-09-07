package com.simpleaccount.app.data.agent

import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import com.simpleaccount.app.data.service.ToolChatMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型后端抽象（可插拔）：云侧大模型与端侧小模型无缝切换的前瞻预留。
 *
 * - 云侧：[CloudModelBackend]，走 OpenAI 兼容 HTTP（现有 AiService），完整提示词 + 全量工具；
 * - 端侧：[OnDeviceModelBackend]，给未来手机本地 0.6B–1.5B 小参数模型预留的协议位。
 *   目前本地推理引擎尚未接入，调用会返回一条**诚实**的不可用说明（绝不编造结果），
 *   但工具协议/提示词档位/数据接口已全部按"模型规模不敏感"设计好：
 *   小模型自动使用精简提示词（[ModelBackend.compactPrompt]）与收敛后的工具集。
 *
 * AgentLoop 只依赖本抽象，不直接依赖 AiService；切换后端只需改一个设置项。
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
 * 端侧小模型后端（预留桩）：未来接入手机本地 0.6B–1.5B 模型时，
 * 在此实现本地推理调用即可，AgentLoop/提示词/工具协议零改动。
 */
@Singleton
class OnDeviceModelBackend @Inject constructor() : ModelBackend {
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
        // 诚实不可用：引擎未接入，不编造任何结果
        return BackendChatResult(
            content = "",
            error = "端侧小模型尚未接入：本地推理引擎还在开发中。请先在 AI 设置里使用云端模型，" +
                "或等待后续版本推送端侧 0.6B–1.5B 免费模型包。",
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

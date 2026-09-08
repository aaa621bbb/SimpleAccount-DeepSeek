package com.simpleaccount.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.agent.AgentLoop
import com.simpleaccount.app.data.agent.ConversationManager
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Conversation
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import com.simpleaccount.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class AiUiState(
    /** 会话列表（按最近活跃倒序） */
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String = "",
    val currentTitle: String = "",
    /** 当前会话的消息（来自 DB Flow，有上限，不常驻全量） */
    val messages: List<AiMessage> = emptyList(),
    val typing: Boolean = false,
    /** Agent 过程状态（"正在思考…"/"正在查询账本…"） */
    val phase: String? = null,
    val streamingText: String? = null,
    val input: String = "",
    val error: String? = null,
    val pendingCount: Int = 0,
    val enabled: Boolean = false,
    /** 截图记账：识别结果待确认（用户勾选后才入账） */
    val screenshotPending: ScreenshotPendingUi? = null,
    val pendingConfirm: PendingConfirmUi? = null,
    /** Agent 过程日志（调用了什么工具、查了哪本账） */
    val traces: List<String> = emptyList(),
    val reasoning: String? = null,
)

data class PendingConfirmUi(
    val tool: String,
    val args: String,
    val summary: String,
)

/** 截图识别出的单笔（预览/勾选用） */
data class ScreenshotItemUi(
    val date: String?,
    val merchant: String,
    val product: String?,
    val amount: Double,
    val type: String,
    val time: String? = null,
    /** 与账本已有记录重复（默认不勾选、不可选） */
    val duplicate: Boolean,
    val selected: Boolean,
)

data class ScreenshotPendingUi(
    val conversationId: String,
    val items: List<ScreenshotItemUi>,
) {
    val selectableCount: Int get() = items.count { it.selected }
}

@HiltViewModel
class AiViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val conversationManager: ConversationManager,
    private val merchantRepository: MerchantRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val aiService: AiService,
    private val agentLoop: AgentLoop,
    private val agentTools: com.simpleaccount.app.data.agent.AgentTools,
    private val localAccountant: com.simpleaccount.app.data.agent.LocalAccountant,
    private val classificationService: com.simpleaccount.app.data.service.ClassificationService,
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState())
    val state = _state.asStateFlow()

    /** 当前会话消息流订阅（切换会话时取消旧的） */
    private var messagesJob: Job? = null

    /** 当前 Agent 回合任务（支持停止/新消息打断） */
    private var agentJob: Job? = null

    /** 本地写提案待执行的落库动作（用户在确认卡片上点"确认执行"后运行）。 */
    private var pendingLocalCommit: (suspend () -> String)? = null

private val welcomeText =
        "你好，我是 AI 记账管家。查实数本地秒回；分析、建议、体检交给模型。记账/改账可在设置里选「每次确认」或「授权后自动执行」；金额和时刻说不清时我会先问你，绝不瞎猜。每次进入默认开新会话，历史对话仍在左上角菜单可切换。"

    init {
        viewModelScope.launch {
            val engineReady = settingsRepository.engineAllowsModel() && (
                settingsRepository.engineUsesOnDevice() && settingsRepository.onDeviceModelId().isNotBlank() ||
                    settingsRepository.isAiEnabled()
                )
            val rulesOnly = settingsRepository.accountingEngine() == SettingsRepository.ENGINE_RULES
            val enabled = engineReady || rulesOnly || settingsRepository.engineAllowsRules()
            _state.value = _state.value.copy(enabled = enabled)
            refreshPendingCount()
            if (_state.value.currentConversationId.isEmpty()) {
                // v2.31.1：进入 AI 管家默认新建会话；历史仍可在列表切换
                createConversationInternal()
            }
        }
        // 会话列表：删除当前会话后自动切到最新会话
        viewModelScope.launch {
            conversationManager.observeConversations().collect { convs ->
                _state.value = _state.value.copy(conversations = convs)
                val cur = _state.value.currentConversationId
                if (cur.isNotEmpty() && convs.none { it.id == cur }) {
                    if (convs.isNotEmpty()) switchTo(convs.first().id)
                    else createConversationInternal()
                } else {
                    convs.firstOrNull { it.id == cur }?.let {
                        if (it.title != _state.value.currentTitle) {
                            _state.value = _state.value.copy(currentTitle = it.title)
                        }
                    }
                }
            }
        }
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            conversationManager.observeMessages(conversationId).collect { msgs ->
                _state.value = _state.value.copy(messages = msgs)
            }
        }
    }

    /** 新会话若为空则插入欢迎语 */
    private suspend fun ensureWelcome(conversationId: String) {
        if (conversationManager.getMessages(conversationId).isEmpty()) {
            conversationManager.addMessage(conversationId, AiMessage.ROLE_ASSISTANT, welcomeText)
        }
    }

    private suspend fun createConversationInternal() {
        val conv = conversationManager.newConversation()
        ensureWelcome(conv.id)
        _state.value = _state.value.copy(
            currentConversationId = conv.id,
            currentTitle = conv.title,
            error = null
        )
        observeMessages(conv.id)
    }

    // ---------------- 对外操作 ----------------

    fun onInputChange(v: String) {
        _state.value = _state.value.copy(input = v)
    }

/** 进入 AI 页时刷新启用状态，避免设置页开关后返回时缓存陈旧。
     * 规则/端侧/API 任一就绪即视为可用。 */
    fun refreshEnabled() {
        val engineReady = settingsRepository.engineAllowsModel() && (
            settingsRepository.engineUsesOnDevice() && settingsRepository.onDeviceModelId().isNotBlank() ||
                settingsRepository.isAiEnabled()
            )
        val rulesOnly = settingsRepository.accountingEngine() == SettingsRepository.ENGINE_RULES
        val cur = engineReady || rulesOnly || settingsRepository.engineAllowsRules()
        if (cur != _state.value.enabled) {
            _state.value = _state.value.copy(enabled = cur)
        }
    }

fun switchTo(conversationId: String) {
        if (conversationId == _state.value.currentConversationId) return
        viewModelScope.launch {
            val conv = conversationManager.getConversation(conversationId) ?: return@launch
            conversationManager.touchActive(conv.id)
            _state.value = _state.value.copy(
                currentConversationId = conv.id,
                currentTitle = conv.title,
                error = null,
                typing = false,
                phase = null
            )
            observeMessages(conv.id)
        }
    }

    fun newConversation() {
        if (_state.value.typing) return
        viewModelScope.launch { createConversationInternal() }
    }

    fun deleteConversation(conversationId: String) {
        if (_state.value.typing && conversationId == _state.value.currentConversationId) return
        viewModelScope.launch { conversationManager.deleteConversation(conversationId) }
    }

    fun clearCurrentConversation() {
        if (_state.value.typing) return
        viewModelScope.launch {
            conversationManager.clearMessages(_state.value.currentConversationId)
            ensureWelcome(_state.value.currentConversationId)
        }
    }

    /** 消息长按操作：复制由 UI 完成；删除为硬删；撤回置 withdrawn（显示"已撤回"） */
    fun deleteMessage(id: String) {
        viewModelScope.launch { conversationManager.deleteMessage(id) }
    }

    fun withdrawMessage(id: String) {
        viewModelScope.launch { conversationManager.withdrawMessage(id) }
    }

    /** 重新生成最后一条回复：删掉最后的 assistant 消息后按最后一条用户消息重跑 */
    /** 停止当前回合：掐断进行中的网络请求 + 取消任务，输入栏立即可用 */
    fun stopAgent() {
        val partial = _state.value.streamingText
        val convId = _state.value.currentConversationId
        aiService.cancelAllActive()
        agentJob?.cancel()
        agentJob = null
        _state.value = _state.value.copy(typing = false, phase = null, streamingText = null, reasoning = null)
        if (!partial.isNullOrBlank() && partial.length > 8 && convId.isNotEmpty()) {
            viewModelScope.launch {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT, partial.trim() + "\n\n（已停止）"
                )
            }
        }
    }

    fun regenerate() {
        if (_state.value.typing) return
        viewModelScope.launch {
            val convId = _state.value.currentConversationId
            val lastUser = conversationManager.lastUserMessage(convId)
            if (lastUser == null) {
                _state.value = _state.value.copy(error = "没有可重新生成的提问")
                return@launch
            }
            conversationManager.messagesAfter(convId, lastUser.timestamp)
                .forEach { conversationManager.deleteMessage(it.id) }
            _state.value = _state.value.copy(typing = true, phase = "正在办理…", error = null)
            runAgentTurn(convId, lastUser.content)
        }
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.typing) {
            stopAgent()
        }
        agentJob = viewModelScope.launch {
            val convId = _state.value.currentConversationId.ifEmpty {
                conversationManager.ensureCurrentConversation().id
            }
            if (_state.value.currentTitle == "新对话") {
                conversationManager.renameConversation(convId, trimmed.take(16))
            }
            conversationManager.addMessage(convId, AiMessage.ROLE_USER, trimmed)
            _state.value = _state.value.copy(
                input = "", typing = true, phase = "正在办理…", error = null,
                streamingText = null, pendingConfirm = null, traces = emptyList(), reasoning = null
            )
            // 记账引擎模式：规则 / API / 端侧 / 混用（互斥，禁止双引擎抢同一输入）
            val allowsRules = settingsRepository.engineAllowsRules()
            val allowsModel = settingsRepository.engineAllowsModel()
            val onDevice = settingsRepository.engineUsesOnDevice()
            val onDeviceReady = onDevice && settingsRepository.onDeviceModelId().isNotBlank()
            val modelOn = allowsModel && (
                onDeviceReady ||
                    (settingsRepository.isAiEnabled() && settingsRepository.apiKey().isNotBlank())
                )

            if (allowsRules) {
                val local = runCatching { localAccountant.tryAnswer(trimmed, modelOn) }.getOrNull()
                if (local != null) {
                    conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, local)
                    _state.value = _state.value.copy(typing = false, phase = null, streamingText = null)
                    return@launch
                }
                // 本地写提案（规则引擎）
                val proposal = runCatching { localAccountant.tryProposeWrite(trimmed) }.getOrNull()
                if (proposal != null) {
                    if (!proposal.needsConfirm) {
                        conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, proposal.preview)
                        _state.value = _state.value.copy(typing = false, phase = null, streamingText = null)
                    } else if (settingsRepository.isAgentAutoExecute()) {
                        _state.value = _state.value.copy(typing = true, phase = "正在执行…")
                        val text = runCatching { proposal.commit() }.getOrNull()
                            ?: "失败 rows_affected=0。这次操作未能落库。"
                        conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, text)
                        _state.value = _state.value.copy(typing = false, phase = null, streamingText = null)
                    } else {
                        pendingLocalCommit = proposal.commit
                        conversationManager.addMessage(
                            convId, AiMessage.ROLE_ASSISTANT,
                            "这项操作会改账本，点「确认执行」后才落库：\n\n${proposal.preview}"
                        )
                        _state.value = _state.value.copy(
                            typing = false, phase = null, streamingText = null,
                            pendingConfirm = PendingConfirmUi(LOCAL_WRITE_TOOL, "", proposal.preview)
                        )
                    }
                    return@launch
                }
            }

            // 纯规则模式：规则吃不了就明确告知，不调模型
            if (!allowsModel) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT,
                    "当前为「纯规则」模式，这条我还解析不了。可换个说法（含金额/时间/商户），或到「AI 设置 → 记账引擎」切换到混用/纯 API/纯端侧。",
                )
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }

            if (onDevice) {
                if (!onDeviceReady) {
                    conversationManager.addMessage(
                        convId, AiMessage.ROLE_ASSISTANT,
                        "端侧模型未就绪。请到「AI 设置 → 端侧模型」下载或导入本地模型包。",
                        status = AiMessage.STATUS_ERROR
                    )
                    _state.value = _state.value.copy(typing = false, phase = null)
                    return@launch
                }
            } else {
                if (!settingsRepository.isAiEnabled()) {
                    conversationManager.addMessage(
                        convId, AiMessage.ROLE_ASSISTANT,
                        "AI 功能未开启，请到设置中开启并配置。常见问题即使不开 AI，在规则/混用模式下也能直接答。",
                        status = AiMessage.STATUS_ERROR
                    )
                    _state.value = _state.value.copy(typing = false, phase = null)
                    return@launch
                }
                if (settingsRepository.apiKey().isBlank()) {
                    conversationManager.addMessage(
                        convId, AiMessage.ROLE_ASSISTANT,
                        "未配置 API Key，请先到「AI 辅助设置」填写；或切换到端侧/纯规则模式。",
                        status = AiMessage.STATUS_ERROR
                    )
                    _state.value = _state.value.copy(typing = false, phase = null)
                    return@launch
                }
            }
            runAgentTurn(convId, trimmed)
        }
    }

    fun confirmPending() {
        val p = _state.value.pendingConfirm ?: return
        val convId = _state.value.currentConversationId
        viewModelScope.launch {
            _state.value = _state.value.copy(pendingConfirm = null, typing = true, phase = "正在执行…")
            if (p.tool == LOCAL_WRITE_TOOL) {
                // 本地写提案：执行用户已批准的落库动作
                val commit = pendingLocalCommit
                pendingLocalCommit = null
                val text = runCatching { commit?.invoke() }.getOrNull()
                    ?: "失败 rows_affected=0。这次操作已过期，请重新说一次。"
                conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, text)
            } else {
                val result = agentTools.execute(
                    com.simpleaccount.app.data.agent.AgentToolCall("confirm-1", p.tool, p.args),
                    confirmed = true,
                )
                val audit = com.simpleaccount.app.data.agent.ToolExecutionRecord(
                    tool = result.name,
                    ok = result.ok,
                    affectedRows = result.affectedRows,
                    elapsedMs = result.elapsedMs,
                    permission = result.permission,
                    permissionNote = result.permissionNote,
                ).verdictLine()
                conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, result.content)
                _state.value = _state.value.copy(traces = (_state.value.traces + audit).takeLast(12))
            }
            _state.value = _state.value.copy(typing = false, phase = null)
        }
    }

    fun dismissPending() {
        val convId = _state.value.currentConversationId
        _state.value = _state.value.copy(pendingConfirm = null)
        pendingLocalCommit = null
        viewModelScope.launch {
            conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, "已取消这次操作，账本没有改动。")
        }
    }

    companion object {
        /** 本地写提案在确认卡片里的伪工具名（与 Agent 工具区分）。 */
        const val LOCAL_WRITE_TOOL = "__local_write__"
    }

    // ---------------- Agent 回合 ----------------

    private suspend fun runAgentTurn(conversationId: String, userText: String) {
        // 上下文按 30 分钟窗口/20 条兜底规则从 DB 现取现裁；
        // 若最后一条正是本次用户消息则去掉（AgentLoop 会重新追加，避免重复）
        var history = conversationManager.buildContext(conversationId)
        if (history.lastOrNull()?.second == userText) history = history.dropLast(1)

        AppLog.d("AI-Agent: 开始回合 conv=" + conversationId + " user=" + userText.take(50) + " 历史=" + history.size + "条")
        // 思考计时器：过程提示实时显示已等待秒数（结构化并发：停止/打断时一并取消）
        val startedAt = System.currentTimeMillis()
        var phaseBase = "正在办理…"
        val result: AgentLoop.AgentResult = kotlinx.coroutines.coroutineScope {
            val ticker = launch {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    val sec = (System.currentTimeMillis() - startedAt) / 1000
                    if (sec >= 2) {
                        _state.value = _state.value.copy(phase = phaseBase + "（" + sec + "s）")
                    }
                }
            }
            val r = agentLoop.run(
                userMessage = userText,
                history = history,
                onStatus = { p ->
                    phaseBase = p
                    val sec = (System.currentTimeMillis() - startedAt) / 1000
                    val traces = _state.value.traces + p
                    _state.value = _state.value.copy(
                        phase = if (sec >= 2) p + "（" + sec + "s）" else p,
                        traces = traces.takeLast(12),
                        reasoning = if (p.startsWith("思考中")) (_state.value.reasoning.orEmpty() + p.removePrefix("思考中：")) else _state.value.reasoning
                    )
                },
                onDelta = { d ->
                    val cur = _state.value.streamingText.orEmpty() + d
                    _state.value = _state.value.copy(streamingText = cur, phase = null)
                },
                onReasoning = { chunk ->
                    val merged = com.simpleaccount.app.ui.components.coalesceReasoning(
                        _state.value.reasoning.orEmpty() + chunk
                    )
                    _state.value = _state.value.copy(reasoning = merged)
                },
                onTrace = { line ->
                    _state.value = _state.value.copy(traces = (_state.value.traces + line).takeLast(12))
                },
            )
            ticker.cancel()
            r
        }
        AppLog.d(
            "AI-Agent: 完成 toolRounds=" + result.toolRounds + " " +
                (if (result.error != null) "error=" + result.error else "reply=" + result.reply.take(60))
        )
        if (result.error == "已停止") {
            _state.value = _state.value.copy(typing = false, phase = null, reasoning = null)
            return
        }
        val totalSec = ((System.currentTimeMillis() - startedAt) / 1000.0)
        // 耗时挂在「思考过程」旁，不进回答末尾
        val elapsedLabel = if (totalSec >= 0.5) {
            val toolHint = if (result.toolRounds > 0) " · ${result.toolRounds} 轮工具" else ""
            "总耗时 ${"%.1f".format(totalSec)}s（推理+工具$toolHint）"
        } else null
        if (result.error != null) {
            val packedErr = com.simpleaccount.app.ui.components.packCot(
                result.error, null, elapsedLabel,
            )
            conversationManager.addMessage(
                conversationId, AiMessage.ROLE_ASSISTANT, packedErr,
                status = AiMessage.STATUS_ERROR
            )
            _state.value = _state.value.copy(typing = false, phase = null, streamingText = null, error = result.error, reasoning = null)
        } else {
            val packed = com.simpleaccount.app.ui.components.packCot(
                result.reply.ifBlank { "（模型未返回内容）" },
                _state.value.reasoning,
                elapsedLabel,
            )
            conversationManager.addMessage(
                conversationId, AiMessage.ROLE_ASSISTANT, packed
            )
            _state.value = _state.value.copy(
                typing = false, phase = null, streamingText = null, error = null,
                reasoning = null,
                pendingConfirm = result.pending?.let {
                    PendingConfirmUi(it.tool, it.args, it.summary)
                }
            )
        }
    }

    private suspend fun refreshPendingCount() {
        val pending = merchantRepository.getByStatus(Merchant.STATUS_PENDING).size
        _state.value = _state.value.copy(pendingCount = pending)
    }

    // ---------------- 截图 AI 记账 ----------------

    /**
     * 用户发账单截图（支持多张/长图）：压缩 → 视觉模型提取 → 逐笔去重（同日同商家同金额不重记）→ 入账。
     * 视觉模型可在 AI 设置里配置（默认 glm-4v-flash，免费；主模型是 DeepSeek 时也能用）。
     */
    fun sendScreenshotMessage(daoUris: List<android.net.Uri>) {
        if (daoUris.isEmpty() || _state.value.typing) return
        val enabled = settingsRepository.isAiEnabled()
        if (!enabled) {
            _state.value = _state.value.copy(error = "AI 功能未开启，请到设置中开启并配置")
            return
        }
        if (_state.value.typing) stopAgent()
        agentJob = viewModelScope.launch {
            val convId = _state.value.currentConversationId.ifEmpty {
                conversationManager.ensureCurrentConversation().id
            }
            conversationManager.addMessage(
                convId, AiMessage.ROLE_USER, "📩 发来 ${daoUris.size} 张账单截图，请帮我记账"
            )
            _state.value = _state.value.copy(
                typing = true, phase = "正在读取截图…", error = null,
                reasoning = null, streamingText = null,
                traces = listOf("开始识图：${daoUris.size} 张")
            )

            // 识图智能路由：
            // 「优先用主模型」开 + 主模型本身是多模态（识图）→ 用主模型；
            // 主模型是纯文本（DeepSeek 等）或开关关 → 直接用独立识图配置。
            // 纯文本模型收到图片只会报错或凭空编造（"识别日期全是瞎猜"的主因），先一步拦掉。
            val useMain = settingsRepository.useMainModelForVision() && !isTextOnlyModel(settingsRepository.model())
            val visionKey = settingsRepository.visionApiKey()

            if (!useMain && visionKey.isBlank()) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT,
                    "识图模型未配置：请到「AI 辅助设置 → 截图识图」填写识图 API Key 和模型（默认 glm-4v-flash 免费可用），" +
                        "或在开启「优先用主模型」的前提下把主模型换成支持图片的多模态模型（DeepSeek 等纯文本模型不能识图）。",
                    status = AiMessage.STATUS_ERROR
                )
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }

            // 压缩/切片：长图自动切成多段（保证文字可读），转 base64
            val base64List = mutableListOf<String>()
            for (uri in daoUris) {
                runCatching { compressImageSlices(appContext, uri) }
                    .getOrNull()?.let { base64List.addAll(it) }
            }
            if (base64List.isEmpty()) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT, "图片读取失败，请重试或换截图。",
                    status = AiMessage.STATUS_ERROR
                )
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }

            val today = java.time.LocalDate.now()
            val prompt = """
你是账单识别助手。图上只要出现金额数字，就必须抽成账单，禁止因为「不够清晰」输出空数组。
请逐行阅读支付记录截图（可能有多笔，从上到下扫描，一行都不要漏）。
今天是 ${today}（${today.year}年${today.monthValue}月${today.dayOfMonth}日），昨天=${today.minusDays(1)}，前天=${today.minusDays(2)}。
只输出 JSON 数组，不要解释、不要 markdown 围栏：
[{"date":"日期列原文","time":"HH:mm","merchant":"商家或交易对象","product":"商品说明，可省略","amount":6.5,"type":"expense"}]
规则：
1. date 照抄截图日期列原文（今天/昨天/2026-08-30/8月30日 等），不要自己编年份。
2. time 必须填 HH:mm。下午8:15 → 20:15。只有「上午/下午/晚上/早上/中午」分别填 09:00/15:00/20:00/08:00/12:00。今天/昨天/前天没有钟点填 12:00。禁止输出「时间未知」或空 time。
3. 商家名完整抄写，不要截断、不要补「公司」。
4. amount 是元（不是分）。截图写 -50.00 / ¥50 / 50元 都写成 50。type：支出 expense、收入 income。
5. 重叠行只输出一次。看不清的字段留空，但 amount 能读就必须输出这一笔。
6. 只有确认图上没有任何金额时才输出 []。
            """.trimIndent()

            // 清晰度初筛：单段平均大小过小（<35KB）提示可能模糊，仍尽力识别但回执中提醒
            val avgKb = if (base64List.isNotEmpty()) base64List.sumOf { it.length } / 1024 / base64List.size else 0
            val clarityHint = if (avgKb > 0 && avgKb < 35) "（清晰度偏低 ${avgKb}KB/段，建议重截清晰原图）" else ""
            _state.value = _state.value.copy(
                phase = "正在识别截图（${if (useMain) "主模型" else "识图模型"}）…",
                traces = _state.value.traces + "已压缩 ${base64List.size} 段，一次发给模型$clarityHint"
            )

            // 所有切片一次请求发给模型（省 token、也更快）。最多 2 次：主模型失败才回退识图配置。
            suspend fun visionCall(base: String, key: String, model: String): Pair<String, String?> = try {
                kotlinx.coroutines.withTimeout(45_000) {
                    val r = aiService.chatWithImage(
                        baseUrl = base, apiKey = key, model = model,
                        prompt = prompt, imageBase64List = base64List,
                    )
                    r.content to r.error
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                "" to "识图超时（45秒）。网络较慢或图片太大，请裁切后重试——不是「图里没有账单」。"
            } catch (e: Exception) {
                "" to (e.message ?: "识图失败")
            }

            var lastVisionError: String? = null
            var lastRaw = ""
            val first = visionCall(
                if (useMain) settingsRepository.baseUrl() else settingsRepository.visionBaseUrl(),
                if (useMain) settingsRepository.apiKey() else settingsRepository.visionApiKey(),
                if (useMain) settingsRepository.model() else settingsRepository.visionModel(),
            )
            lastVisionError = first.second
            lastRaw = first.first
            var items = parseExtracted(first.first)

            if (items.isEmpty() && useMain && settingsRepository.visionApiKey().isNotBlank()) {
                AppLog.d("AI识图: 主模型识别为空，回退独立识图配置（不再第三次重试）")
                _state.value = _state.value.copy(phase = "主模型识别失败，改用识图模型…")
                val second = visionCall(
                    settingsRepository.visionBaseUrl(),
                    settingsRepository.visionApiKey(),
                    settingsRepository.visionModel(),
                )
                lastVisionError = second.second ?: lastVisionError
                lastRaw = second.first.ifBlank { lastRaw }
                items = parseExtracted(second.first)
            }
            val mergedItems = mergeExtracted(items)
            _state.value = _state.value.copy(
                traces = _state.value.traces + "模型返回 ${items.size} 笔，合并去重后 ${mergedItems.size} 笔"
            )

            // 识别完成 → 批内去重（切片重叠会把同一笔识别两次）+ 与账本比对 → 挂起待用户勾选确认
            if (mergedItems.isEmpty()) {
                val msg = when {
                    !lastVisionError.isNullOrBlank() -> "识图没有完成：$lastVisionError"
                    lastRaw.isNotBlank() ->
                        "模型有返回，但没有解析出结构化账单。原文片段：" +
                            lastRaw.trim().replace("\n", " ").take(180)
                    else -> "识图接口没有返回账单字段。请确认截图识图模型和 Key 已配置，并尽量用完整明细页。"
                }
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT, msg,
                    status = if (!lastVisionError.isNullOrBlank()) AiMessage.STATUS_ERROR else AiMessage.STATUS_DONE
                )
                _state.value = _state.value.copy(typing = false, phase = null, error = lastVisionError, reasoning = null)
                return@launch
            }
            val todayStr = java.time.LocalDate.now().toString()
            val seenBatch = mutableListOf<ExtractedTx>()
            val existingTxs = accountRepository.getAll()
            val existingMerchants = merchantRepository.getAll().map { it.merchant }

            val uiItems = mutableListOf<ScreenshotItemUi>()
            for (item in mergedItems) {
                val amountFen = Math.round(item.amount * 100)
                if (amountFen <= 0) continue
                val date = item.date ?: todayStr
                val merchant = com.simpleaccount.app.util.MerchantMatcher.canonicalize(item.merchant, existingMerchants)
                // 批内去重：同一笔只保留第一次识别（DedupEngine 智能匹配）
                if (seenBatch.any { o ->
                        com.simpleaccount.app.data.agent.DedupEngine.isSameTx(
                            o.date ?: "", Math.round(o.amount * 100), o.merchant, o.time ?: "",
                            date, amountFen, merchant, item.time ?: ""
                        )
                    }) continue
                seenBatch.add(item.copy(merchant = merchant, date = date))
                val duplicate = existingTxs.any { t ->
                    com.simpleaccount.app.data.agent.DedupEngine.isSameTx(
                        t.date, t.amount, t.merchant, t.time,
                        date, amountFen, merchant, item.time ?: ""
                    )
                }
                uiItems.add(
                    ScreenshotItemUi(
                        // 预览保留可空 date：识别不出时让用户看见"日期未知"，确认入账时才落今天
                        date = item.date, merchant = merchant, product = item.product,
                        amount = item.amount, type = item.type,
                        time = com.simpleaccount.app.util.DateResolver.resolveTimeOrPeriod(item.time, item.date),
                        duplicate = duplicate, selected = !duplicate,
                    )
                )
            }
            if (uiItems.isEmpty()) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT,
                    "没从截图里识别出有效的账单记录（或全部与已有账单重复）。"
                )
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }

            // 挂起待确认：用户在弹层里勾选，点「确认入账」才写库
            _state.value = _state.value.copy(
                typing = false, phase = null,
                screenshotPending = ScreenshotPendingUi(conversationId = convId, items = uiItems)
            )
        }
    }

    /** 勾选/取消预览里的某一笔（重复笔不可选） */
    fun toggleScreenshotItem(index: Int) {
        val pending = _state.value.screenshotPending ?: return
        val items = pending.items.toMutableList()
        val target = items.getOrNull(index) ?: return
        if (target.duplicate) return
        items[index] = target.copy(selected = !target.selected)
        _state.value = _state.value.copy(screenshotPending = pending.copy(items = items))
    }

    /** 全选/全不选（重复笔始终不选） */
    fun toggleAllScreenshot(select: Boolean) {
        val pending = _state.value.screenshotPending ?: return
        _state.value = _state.value.copy(
            screenshotPending = pending.copy(
                items = pending.items.map {
                    if (it.duplicate) it.copy(selected = false) else it.copy(selected = select)
                }
            )
        )
    }

    /** 放弃本次识别（不入账） */
    fun dismissScreenshot() {
        val pending = _state.value.screenshotPending
        _state.value = _state.value.copy(screenshotPending = null)
        pending?.let {
            viewModelScope.launch {
                conversationManager.addMessage(
                    it.conversationId, AiMessage.ROLE_ASSISTANT, "已放弃本次截图识别，未入账。"
                )
            }
        }
    }

    /** 确认入账：把勾选的笔写入账本（写库前再做一次新鲜去重） */
    fun commitScreenshot() {
        val pending = _state.value.screenshotPending ?: return
        val convId = pending.conversationId
        viewModelScope.launch {
            val validNames = categoryRepository.getAll().map { it.name }.toSet()
            var existingTxs = accountRepository.getAll()
            var recorded = 0
            var skipped = 0
            val lines = mutableListOf<String>()
            val today = java.time.LocalDate.now().toString()
            for (item in pending.items.filter { it.selected && !it.duplicate }) {
                val amountFen = Math.round(item.amount * 100)
                if (amountFen <= 0) continue
                val date = item.date ?: today
                val dup = existingTxs.any { t ->
                    com.simpleaccount.app.data.agent.DedupEngine.isSameTx(
                        t.date, t.amount, t.merchant, t.time,
                        date, amountFen, item.merchant, item.time ?: ""
                    )
                }
                if (dup) { skipped++; continue }
                val category = classificationService.classifyForImport(
                    item.merchant, item.product ?: "", "", validNames, item.type
                )
                val sub = com.simpleaccount.app.util.KeywordRules.classifySub(
                    "${item.merchant} ${item.product ?: ""}", category
                ).orEmpty()
                val id = accountRepository.insert(
                    com.simpleaccount.app.data.entity.Transaction(
                        amount = amountFen,
                        type = item.type,
                        category = category,
                        subCategory = sub,
                        date = date,
                        time = com.simpleaccount.app.util.DateResolver.resolveTimeOrPeriod(item.time, item.date),
                        merchant = item.merchant,
                        product = item.product ?: "",
                        source = com.simpleaccount.app.data.entity.Transaction.SOURCE_MANUAL,
                    )
                )
                existingTxs = existingTxs + com.simpleaccount.app.data.entity.Transaction(
                    amount = amountFen, type = item.type, category = category, date = date,
                    time = com.simpleaccount.app.util.DateResolver.resolveTimeOrPeriod(item.time, item.date), merchant = item.merchant, product = item.product ?: "",
                    source = com.simpleaccount.app.data.entity.Transaction.SOURCE_MANUAL, id = id
                )
                recorded++
                val dir = if (item.type == com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME) "收入" else "支出"
                lines.add("- ✅ (流水号:$id) $dir ${"%.2f".format(item.amount)}元 · ${item.merchant.ifBlank { "未记商家" }} · $category · $date ${com.simpleaccount.app.util.DateResolver.resolveTimeOrPeriod(item.time, item.date)}")
            }
            _state.value = _state.value.copy(screenshotPending = null)
            refreshPendingCount()
            conversationManager.addMessage(
                convId, AiMessage.ROLE_ASSISTANT,
                "### 截图记账完成\n已记 **$recorded** 笔" +
                    (if (skipped > 0) "，入库时又发现 $skipped 笔重复已跳过" else "") + "：\n" +
                    lines.ifEmpty { listOf("（没有勾选任何笔）") }.joinToString("\n")
            )
        }
    }

    /** 双轮识别结果合并去重（用 DedupEngine） */
    private fun mergeExtracted(list: List<ExtractedTx>): List<ExtractedTx> {
        val out = mutableListOf<ExtractedTx>()
        for (e in list) {
            val dup = out.any { o ->
                com.simpleaccount.app.data.agent.DedupEngine.isSameTx(
                    o.date ?: "", Math.round(o.amount * 100), o.merchant, o.time ?: "",
                    e.date ?: "", Math.round(e.amount * 100), e.merchant, e.time ?: ""
                )
            }
            if (!dup) out.add(e)
        }
        return out
    }

    /** 相对日期解析：昨天/前天/今天/几月几号/MM-dd/斜杠日期 → yyyy-MM-dd；解析失败返回 null */
    private fun resolveRelativeDate(raw: String?): String? {
        val s = raw?.trim() ?: return null
        val today = java.time.LocalDate.now()
        // 完整日期：2026-08-30 / 2026/8/30 / 2026年8月30日
        Regex("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})").find(s)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            if (m in 1..12 && d in 1..31) {
                return try { "%04d-%02d-%02d".format(y, m, d) } catch (_: Exception) { null }
            }
        }
        return when {
            s.contains("前天") -> today.minusDays(2).toString()
            s.contains("昨天") -> today.minusDays(1).toString()
            s.contains("今天") || s.contains("今日") -> today.toString()
            // "8月30日"/"08-30"/"8/30" → 今年
            else -> parseMonthDay(s, today)
        }
    }

    /** 裸日期 "8月30日"/"08-30"/"8/30" → yyyy-MM-dd；解析失败返回 null */
    private fun parseMonthDay(s: String, today: java.time.LocalDate): String? {
        val m = Regex("(\\d{1,2})[月/-](\\d{1,2})日?").find(s) ?: return null
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        if (month !in 1..12 || day !in 1..31) return null
        return try {
            java.time.LocalDate.of(today.year, month, day).toString()
        } catch (_: Exception) {
            null
        }
    }

    /** "20:15"/"下午8:15"/"昨天 20:15"/"晚上" → "20:15"；解析失败返回 null */
    private fun parseTimeText(raw: String?): String? =
        com.simpleaccount.app.util.DateResolver.parseTimeText(raw)

    /**
     * 判断模型是否纯文本（不认图片）：命中已知纯文本家族就直接用识图配置，
     * 避免白烧一次请求还拿到编造结果。不认识的模型按"可能识图"处理，尊重用户开关。
     */
    private fun isTextOnlyModel(modelId: String): Boolean {
        val id = modelId.lowercase()
        if (id.isBlank()) return false
        // 先看明确的识图特征（含视觉后缀/知名多模态家族），命中 = 不是纯文本
        val visionLike = listOf(
            "4v", "vl", "vision", "omni", "gpt-4o", "gpt-4.1", "gpt-5",
            "gemini", "claude", "llava", "minicpm", "glm-4.1v", "glm-4.5v", "glm-4.6v"
        )
        if (visionLike.any { id.contains(it) }) return false
        // 纯文本家族
        val textOnlyLike = listOf(
            "deepseek", "moonshot", "kimi", "doubao", "skylark", "abab", "ernie",
            "baichuan", "chatglm", "spark", "hunyuan", "minimax", "gpt-3.5"
        )
        if (textOnlyLike.any { id.contains(it) }) return true
        // GLM-4 系非 v 变体、Qwen 系非 vl 变体默认纯文本
        if (id.contains("glm-4") || id.contains("glm-4.5") || id.contains("glm-4.6")) return true
        if (id.contains("qwen") || id.contains("llama") || id.contains("yi")) return true
        return false
    }

    /** 从视觉模型输出里抠账单：JSON 数组/单对象/金额字符串/中文键。 */
    private fun parseExtracted(text: String): List<ExtractedTx> {
        val cleaned = text.replace("```json", "").replace("```", "").trim()
        val fromJson = parseExtractedJson(cleaned)
        if (fromJson.isNotEmpty()) return fromJson
        return parseExtractedLoose(cleaned)
    }

    private fun parseExtractedJson(cleaned: String): List<ExtractedTx> {
        val startArr = cleaned.indexOf('[')
        val endArr = cleaned.lastIndexOf(']')
        val startObj = cleaned.indexOf('{')
        val endObj = cleaned.lastIndexOf('}')
        val blob = when {
            startArr >= 0 && endArr > startArr -> cleaned.substring(startArr, endArr + 1)
            startObj >= 0 && endObj > startObj -> "[" + cleaned.substring(startObj, endObj + 1) + "]"
            else -> return emptyList()
        }
        return runCatching {
            val arr = org.json.JSONArray(blob)
            (0 until arr.length()).mapNotNull { i -> jsonToTx(arr.optJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    private fun jsonToTx(o: org.json.JSONObject?): ExtractedTx? {
        if (o == null) return null
        val amountRaw = sequenceOf("amount", "money", "金额", "price")
            .map { o.opt(it) }
            .firstOrNull { it != null && it != org.json.JSONObject.NULL }
        val amount = when (amountRaw) {
            is Number -> amountRaw.toDouble()
            is String -> {
                val fen = com.simpleaccount.app.util.MoneyUtil.parseToFen(amountRaw)
                    ?: com.simpleaccount.app.util.MoneyUtil.parseChineseToFen(amountRaw)
                fen?.div(100.0)
            }
            else -> null
        } ?: return null
        if (amount <= 0) return null
        val dateRaw = o.optString("date").ifBlank { o.optString("日期") }.trim()
        val date = com.simpleaccount.app.util.DateResolver.resolveFlexible(dateRaw)
        var timeRaw = o.optString("time").ifBlank { o.optString("时间") }.trim()
        if (timeRaw.isBlank()) timeRaw = dateRaw
        val merchant = com.simpleaccount.app.util.MerchantMatcher.stripEllipsis(
            o.optString("merchant").ifBlank { o.optString("商家") }.ifBlank { o.optString("商户") }.trim()
        )
        val product = o.optString("product").ifBlank { o.optString("商品") }.trim().ifBlank { null }
        val typeRaw = o.optString("type").ifBlank { o.optString("类型") }
        val type = if (typeRaw.contains("income") || typeRaw.contains("收入"))
            com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME
        else com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE
        return ExtractedTx(
            date, merchant, product, amount, type,
            com.simpleaccount.app.util.DateResolver.resolveTimeOrPeriod(timeRaw, dateRaw, merchant, product),
        )
    }

    private fun parseExtractedLoose(text: String): List<ExtractedTx> {
        val out = mutableListOf<ExtractedTx>()
        Regex("([\\u4e00-\\u9fa5A-Za-z0-9]{2,24}).{0,8}(?:¥|￥|-)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*元?")
            .findAll(text)
            .forEach { m ->
                val amount = m.groupValues[2].toDoubleOrNull() ?: return@forEach
                if (amount <= 0 || amount > 1_000_000) return@forEach
                val merchant = com.simpleaccount.app.util.MerchantMatcher.stripEllipsis(m.groupValues[1])
                if (merchant.length < 2) return@forEach
                out += ExtractedTx(null, merchant, null, amount, com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE, null)
            }
        return out.distinctBy { it.merchant + it.amount }
    }

    private data class ExtractedTx(
        val date: String?,
        val merchant: String,
        val product: String?,
        val amount: Double,
        val type: String,
        val time: String? = null,
    )

    /**
     * 图片压缩 + 长图切片（保文字可读是第一优先级）：
     * 1. 宽度保持 ≤1080（手机截图原生宽度，金额小字清晰）；
     * 2. 高度 ≤2400 整图发送；更长的图用 BitmapRegionDecoder 按 2000px 段解码（带 200px 重叠），
     *    不整图加载 —— 超长图（几万像素高）整图解码既爆内存又会被服务端压缩到看不清；
     * 3. 同一条记录可能出现在相邻两段里 —— 由调用方做"批内去重"。
     */
    private fun compressImageSlices(context: android.content.Context, uri: android.net.Uri): List<String> {
        val decoder = runCatching {
            @Suppress("DEPRECATION")
            android.graphics.BitmapRegionDecoder.newInstance(
                context.contentResolver.openInputStream(uri)!!, false
            )
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val fullW = decoder.width
            val fullH = decoder.height
            if (fullW <= 0 || fullH <= 0) return emptyList()

            // 宽度采样：>1080 时按比例减半采样（区域解码支持 inSampleSize）
            var sample = 1
            while (fullW / (sample * 2) >= 1080) sample *= 2

            val slices = mutableListOf<String>()
            fun encode(bmp: android.graphics.Bitmap): String {
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            }

            if (fullH <= 3200) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = decoder.decodeRegion(android.graphics.Rect(0, 0, fullW, fullH), opts)
                if (bmp != null) slices.add(encode(bmp))
            } else {
                // 最多 3 段，一次请求发给模型；重叠加厚避免一行被切断
                val overlap = 280
                val sliceHeight = ((fullH + overlap * 2) / 3).coerceAtLeast(1800)
                var y = 0
                var count = 0
                while (y < fullH && count < 3) {
                    val h = minOf(sliceHeight, fullH - y)
                    if (h < 150) break
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    val piece = decoder.decodeRegion(android.graphics.Rect(0, y, fullW, y + h), opts)
                    if (piece != null) {
                        slices.add(encode(piece))
                        piece.recycle()
                        count++
                    }
                    if (y + h >= fullH) break
                    y += sliceHeight - overlap
                }
            }
            slices
        }.also { runCatching { @Suppress("DEPRECATION") decoder.recycle() } }
            .getOrDefault(emptyList())
    }

    // ---------------- 批量归类 ----------------

    /** 批量归类 pending 商家（每批 ≤20），结果写为当前会话的一条 assistant 消息 */
    fun classifyPendingMerchants() {
        val enabled = _state.value.enabled
        if (!enabled) {
            _state.value = _state.value.copy(error = "AI 未开启，无法批量归类")
            return
        }
        if (_state.value.typing) return
        viewModelScope.launch {
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                _state.value = _state.value.copy(error = "未配置 API Key")
                return@launch
            }
            val convId = _state.value.currentConversationId.ifEmpty {
                conversationManager.ensureCurrentConversation().id
            }
            _state.value = _state.value.copy(typing = true, phase = "正在归类商家…", error = null)
            val pendings = merchantRepository.getByStatus(Merchant.STATUS_PENDING)
            if (pendings.isEmpty()) {
                conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, "当前没有待归类的商家。")
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }
            AppLog.d("AI归类: 开始批量归类 pending=${pendings.size}")
            val validCategories = categoryRepository.getAll().map { it.name }.filter { it != "其它" }
            var done = 0
            // 批量 40 个/次：83 个待归类从 5 次 API 调用降到 3 次，明显提速
            pendings.chunked(40).forEach { batch ->
                val prompt = buildClassifyPrompt(batch, validCategories)
                val result = aiService.chat(
                    baseUrl = settingsRepository.baseUrl(),
                    apiKey = apiKey,
                    model = settingsRepository.model(),
                    messages = listOf("user" to prompt)
                )
                if (result.error != null) {
                    conversationManager.addMessage(
                        convId, AiMessage.ROLE_ASSISTANT, "归类失败：${result.error}",
                        status = AiMessage.STATUS_ERROR
                    )
                    _state.value = _state.value.copy(typing = false, phase = null, error = result.error)
                    return@launch
                }
                val parsed = parseClassifyResult(result.content, validCategories)
                done += applyClassification(parsed, batch)
            }
            refreshPendingCount()
            val pendingLeft = merchantRepository.getByStatus(Merchant.STATUS_PENDING).size
            conversationManager.addMessage(
                convId, AiMessage.ROLE_ASSISTANT,
                "✅ 已归类 $done 个商家（剩余 $pendingLeft 待归类）。"
            )
            _state.value = _state.value.copy(typing = false, phase = null)
        }
    }

    /** AI 输出宽容解析：JSON 或多行 "商家=分类" */
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
                    val merchant = parts[0].trim().removeSurrounding("\"", "\"").removeSurrounding("“", "”").trim()
                    val cat = Regex("\\s+").replace(parts[1].trim(), "")
                        .removeSurrounding("\"", "\"").removeSurrounding("“", "”").trim()
                    if (merchant.isNotEmpty() && cat in validCategories) map[merchant] = cat
                }
            }
        }
        return map
    }

    private suspend fun applyClassification(map: Map<String, String>, batch: List<Merchant>): Int {
        var count = 0
        for ((aiName, category) in map) {
            val existing = batch.firstOrNull {
                com.simpleaccount.app.util.MerchantMatcher.isSameMerchant(it.merchant, aiName)
            } ?: merchantRepository.getByMerchant(aiName.trim())
                ?: merchantRepository.getAll().firstOrNull {
                    com.simpleaccount.app.util.MerchantMatcher.isSameMerchant(it.merchant, aiName)
                }
                ?: continue
            if (existing.status == Merchant.STATUS_USER_SET) continue
            merchantRepository.update(
                existing.copy(category = category, status = Merchant.STATUS_CLASSIFIED, updatedAt = System.currentTimeMillis())
            )
            val normalizedExisting = normalizeMerchant(existing.merchant)
            // 别名同名的其他商家行一并归一
            merchantRepository.getAll()
                .filter { normalizeMerchant(it.merchant) == normalizedExisting && it.id != existing.id }
                .forEach { m ->
                    merchantRepository.update(
                        m.copy(category = category, status = Merchant.STATUS_CLASSIFIED, updatedAt = System.currentTimeMillis())
                    )
                }
            // 历史追改：账本里该商家的导入交易同步改分类
            accountRepository.getAllImport()
                .filter { normalizeMerchant(it.merchant) == normalizedExisting }
                .forEach { t ->
                    accountRepository.update(
                        t.copy(category = category, updatedAt = System.currentTimeMillis())
                    )
                }
            count++
        }
        return count
    }

    private fun normalizeMerchant(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.trim()
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("（[^）]*）"), "")
            .replace(Regex("\\s+"), "")
            .lowercase()
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

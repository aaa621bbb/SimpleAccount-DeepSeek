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

    private val welcomeText =
        "你好，我是 AI 记账管家。查实数（昨天花了多少、本月花了多少、帮我记 15 元）本地秒回，数字跟账本一致；分析、建议、体检、怎么办交给模型写，不会用模板截胡。"

    init {
        viewModelScope.launch {
            val enabled = settingsRepository.isAiEnabled()
            _state.value = _state.value.copy(enabled = enabled)
            refreshPendingCount()
            if (_state.value.currentConversationId.isEmpty()) {
                val conv = conversationManager.ensureCurrentConversation()
                ensureWelcome(conv.id)
                _state.value = _state.value.copy(
                    currentConversationId = conv.id,
                    currentTitle = conv.title
                )
                observeMessages(conv.id)
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

    /** 进入 AI 页时刷新启用状态，避免设置页开关后返回时缓存陈旧 */
    fun refreshEnabled() {
        val cur = settingsRepository.isAiEnabled()
        if (cur != _state.value.enabled) {
            _state.value = _state.value.copy(enabled = cur)
        }
    }

    fun switchTo(conversationId: String) {
        if (conversationId == _state.value.currentConversationId) return
        viewModelScope.launch {
            val conv = conversationManager.getConversation(conversationId) ?: return@launch
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
        _state.value = _state.value.copy(typing = false, phase = null, streamingText = null)
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
            _state.value = _state.value.copy(typing = true, phase = "正在思考…", error = null)
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
                input = "", typing = true, phase = "正在思考…", error = null,
                streamingText = null, pendingConfirm = null, traces = emptyList(), reasoning = null
            )
            val modelOn = settingsRepository.isAiEnabled() && settingsRepository.apiKey().isNotBlank()
            val local = runCatching { localAccountant.tryAnswer(trimmed, modelOn) }.getOrNull()
            if (local != null) {
                conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, local)
                _state.value = _state.value.copy(typing = false, phase = null, streamingText = null)
                return@launch
            }
            if (!settingsRepository.isAiEnabled()) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT,
                    "AI 功能未开启，请到设置中开启并配置。常见问题（昨天花了多少、本月体检）即使不开 AI 也能直接答。",
                    status = AiMessage.STATUS_ERROR
                )
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT, "未配置 API Key，请先到「AI 辅助设置」填写。",
                    status = AiMessage.STATUS_ERROR
                )
                _state.value = _state.value.copy(typing = false, phase = null)
                return@launch
            }
            runAgentTurn(convId, trimmed)
        }
    }

    fun confirmPending() {
        val p = _state.value.pendingConfirm ?: return
        val convId = _state.value.currentConversationId
        viewModelScope.launch {
            _state.value = _state.value.copy(pendingConfirm = null, typing = true, phase = "正在执行…")
            val result = agentTools.execute(
                com.simpleaccount.app.data.agent.AgentToolCall("confirm-1", p.tool, p.args),
                confirmed = true,
            )
            conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, result.content)
            _state.value = _state.value.copy(typing = false, phase = null)
        }
    }

    fun dismissPending() {
        val convId = _state.value.currentConversationId
        _state.value = _state.value.copy(pendingConfirm = null)
        viewModelScope.launch {
            conversationManager.addMessage(convId, AiMessage.ROLE_ASSISTANT, "已取消这次操作，账本没有改动。")
        }
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
        var phaseBase = "正在思考…"
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
                }
            )
            ticker.cancel()
            r
        }
        AppLog.d(
            "AI-Agent: 完成 toolRounds=" + result.toolRounds + " " +
                (if (result.error != null) "error=" + result.error else "reply=" + result.reply.take(60))
        )
        if (result.error == "已停止") {
            _state.value = _state.value.copy(typing = false, phase = null)
            return
        }
        if (result.error != null) {
            conversationManager.addMessage(
                conversationId, AiMessage.ROLE_ASSISTANT, result.error,
                status = AiMessage.STATUS_ERROR
            )
            _state.value = _state.value.copy(typing = false, phase = null, streamingText = null, error = result.error)
        } else {
            conversationManager.addMessage(
                conversationId, AiMessage.ROLE_ASSISTANT, result.reply.ifBlank { "（模型未返回内容）" }
            )
            _state.value = _state.value.copy(
                typing = false, phase = null, streamingText = null, error = null,
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
                traces = listOf("开始识图：${daoUris.size} 张")
            )

            // 识图智能路由：
            // 「优先用主模型」开 → 先用主模型（多模态主模型零额外配置）；失败自动回退独立识图配置
            // 关 → 只用独立识图配置（主模型是纯文本如 DeepSeek 时用这个）
            val useMain = settingsRepository.useMainModelForVision()

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
你是账单识别助手。请仔细逐行阅读这些支付记录截图（可能有多笔，从上到下逐行扫描，一行都不要漏），提取每一笔账单。
今天是 ${today}（${today.year}年${today.monthValue}月${today.dayOfMonth}日），昨天=${today.minusDays(1)}，前天=${today.minusDays(2)}。
严格只输出 JSON 数组，不要任何解释、不要 markdown 代码块围栏：
[{"date":"yyyy-MM-dd","time":"HH:mm","merchant":"商家或交易对象","product":"商品说明，可省略","amount":6.5,"type":"expense"}]
识别规则：
- date 必须是 yyyy-MM-dd。截图写「昨天/前天/今天/今日」时用上面给出的具体日期，禁止输出「昨天」或空日期。
- 商家名必须完整抄写，不要截断，不要加省略号。被截图裁掉的尾字（如「有限…」）按能看见的部分抄，不要自己补「公司」。
- time 保留 HH:mm；amount 按截图原值（元，保留小数）；type：支出 expense、收入 income。
- 多张切片可能有重叠行，同一笔只输出一次。
- 不要编造截图里没有的字段；识别不出的行跳过。没有账单则输出 []。
            """.trimIndent()

            _state.value = _state.value.copy(
                phase = "正在识别截图（${if (useMain) "主模型" else "识图模型"}）…",
                traces = _state.value.traces + "已压缩 ${base64List.size} 段，一次发给模型（不再反复识别）"
            )

            // 所有切片一次请求发给模型（省 token、也更快）。最多 2 次：主模型失败才回退识图配置。
            suspend fun visionCall(base: String, key: String, model: String): String? = runCatching {
                aiService.chatWithImage(
                    baseUrl = base, apiKey = key, model = model,
                    prompt = prompt, imageBase64List = base64List,
                ).content
            }.getOrNull()

            var items = parseExtracted(
                visionCall(
                    if (useMain) settingsRepository.baseUrl() else settingsRepository.visionBaseUrl(),
                    if (useMain) settingsRepository.apiKey() else settingsRepository.visionApiKey(),
                    if (useMain) settingsRepository.model() else settingsRepository.visionModel(),
                ) ?: ""
            )

            if (items.isEmpty() && useMain && settingsRepository.visionApiKey().isNotBlank()) {
                AppLog.d("AI识图: 主模型识别为空，回退独立识图配置（不再第三次重试）")
                _state.value = _state.value.copy(phase = "主模型识别失败，改用识图模型…")
                items = parseExtracted(
                    visionCall(
                        settingsRepository.visionBaseUrl(),
                        settingsRepository.visionApiKey(),
                        settingsRepository.visionModel(),
                    ) ?: ""
                )
            }
            val mergedItems = mergeExtracted(items)
            _state.value = _state.value.copy(
                traces = _state.value.traces + "模型返回 ${items.size} 笔，合并去重后 ${mergedItems.size} 笔"
            )

            // 识别完成 → 批内去重（切片重叠会把同一笔识别两次）+ 与账本比对 → 挂起待用户勾选确认
            if (mergedItems.isEmpty()) {
                conversationManager.addMessage(
                    convId, AiMessage.ROLE_ASSISTANT,
                    "没从截图里识别出账单记录。如果截图里有明细，麻烦拍清楚一点再试。"
                )
                _state.value = _state.value.copy(typing = false, phase = null)
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
                        date = date, merchant = merchant, product = item.product,
                        amount = item.amount, type = item.type, time = item.time,
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
                val id = accountRepository.insert(
                    com.simpleaccount.app.data.entity.Transaction(
                        amount = amountFen,
                        type = item.type,
                        category = category,
                        date = date,
                        time = item.time ?: "",
                        merchant = item.merchant,
                        product = item.product ?: "",
                        source = com.simpleaccount.app.data.entity.Transaction.SOURCE_MANUAL,
                    )
                )
                existingTxs = existingTxs + com.simpleaccount.app.data.entity.Transaction(
                    amount = amountFen, type = item.type, category = category, date = date,
                    time = item.time ?: "", merchant = item.merchant, product = item.product ?: "",
                    source = com.simpleaccount.app.data.entity.Transaction.SOURCE_MANUAL, id = id
                )
                recorded++
                val dir = if (item.type == com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME) "收入" else "支出"
                lines.add("- ✅ (流水号:$id) $dir ${"%.2f".format(item.amount)}元 · ${item.merchant.ifBlank { "未记商家" }} · $category · $date ${item.time ?: ""}")
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

    /** 从视觉模型输出里抠 JSON 数组（容忍 ```json 围栏和前后杂文字） */
    private fun parseExtracted(text: String): List<ExtractedTx> {
        val cleaned = text.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(cleaned.substring(start, end + 1))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val amount = o.optDouble("amount", Double.NaN)
                if (amount.isNaN() || amount <= 0) return@mapNotNull null
                val dateRaw = o.optString("date", "").trim()
                val date = com.simpleaccount.app.util.DateResolver.resolveFlexible(dateRaw)
                    ?: java.time.LocalDate.now().toString()
                ExtractedTx(
                    date = date,
                    merchant = com.simpleaccount.app.util.MerchantMatcher.stripEllipsis(
                        o.optString("merchant", "").trim()
                    ),
                    product = o.optString("product", "").trim().ifBlank { null },
                    amount = amount,
                    type = if (o.optString("type") == "income")
                        com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME
                    else com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE,
                    time = o.optString("time").takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                        ?.let { tm ->
                            val p = tm.split(":")
                            "%02d:%02d".format(p[0].toInt().coerceIn(0, 23), p[1].toInt().coerceIn(0, 59))
                        },
                )
            }
        }.getOrDefault(emptyList())
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

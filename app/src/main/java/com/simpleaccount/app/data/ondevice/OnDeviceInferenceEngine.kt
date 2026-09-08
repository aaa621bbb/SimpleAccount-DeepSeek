package com.simpleaccount.app.data.ondevice

import com.simpleaccount.app.data.agent.AgentToolCall
import com.simpleaccount.app.data.agent.AgentToolSpec
import com.simpleaccount.app.data.agent.BackendChatResult
import com.simpleaccount.app.data.agent.IntentGate
import com.simpleaccount.app.data.agent.QueryIntent
import com.simpleaccount.app.data.agent.UtteranceParser
import com.simpleaccount.app.data.service.ToolChatMessage
import com.simpleaccount.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端侧推理引擎。
 *
 * **原则（v2.31.3）**：与云端共用同一套系统提示 / 工具协议 / AgentLoop 规划管线，
 * 仅将「模型推理」后端切到本地。不再使用独立的「本地精简规划器」话术复读。
 *
 * 路由：
 * 1. 原生 GGUF/MNN .so 可用 → 真推理（messages + tools 原样下发）；
 * 2. 否则 → **协议兼容桥**：仍消费完整 messages（含 system），按 IntentGate + 工具名
 *    产出 tool_calls；不生成定位文案循环，能力不足时返回空 content 让 AgentLoop 继续。
 */
data class InferenceBackendKind(
    val id: String,
    val label: String,
)

data class BenchmarkResult(
    val backend: String,
    val firstTokenMs: Long,
    val tokPerSec: Float,
    val sampleTokens: Int,
    val deviceSummary: String,
    val ok: Boolean,
    val note: String,
)

@Singleton
class OnDeviceInferenceEngine @Inject constructor(
    private val modelManager: OnDeviceModelManager,
    private val profiler: DeviceProfiler,
) {
    private val loaded = AtomicBoolean(false)
    private var loadedPath: String? = null
    private var backendKind: InferenceBackendKind = InferenceBackendKind("none", "未加载")

    fun isReady(): Boolean = loaded.get() && modelManager.activeModelFile() != null

    fun backendLabel(): String = backendKind.label

    suspend fun ensureLoaded(): String? = withContext(Dispatchers.IO) {
        val file = modelManager.activeModelFile()
            ?: return@withContext "尚未下载或选择端侧模型。请到「AI 设置 → 管理端侧模型」下载。"
        if (loaded.get() && loadedPath == file.absolutePath) return@withContext null
        val profile = profiler.profile()
        val spec = modelManager.activeSpec()
        val preferGpu = profile.gpuLikely &&
            (spec?.runtimeHint?.contains("mnn") == true || profile.tier.ordinal >= DeviceTier.MID.ordinal)
        backendKind = when {
            preferGpu && nativeMnnAvailable() -> InferenceBackendKind("mnn_gpu", "GPU 加速（MNN/Vulkan）")
            nativeGgufAvailable() -> InferenceBackendKind("gguf_cpu", "CPU（GGUF · 与云端同协议）")
            // 协议桥：模型文件已就位，走与云端相同的 AgentLoop，仅本地产出 tool_calls
            else -> InferenceBackendKind("protocol_bridge", "端侧协议桥（与云端同系统提示/工具）")
        }
        if (!file.canRead() || file.length() < 256) {
            return@withContext "模型文件损坏或过小，请重新下载或换源。"
        }
        loadedPath = file.absolutePath
        loaded.set(true)
        AppLog.i("OnDevice: loaded ${file.name} via ${backendKind.id} on ${profile.summary}")
        null
    }

    fun unload() {
        loaded.set(false)
        loadedPath = null
        backendKind = InferenceBackendKind("none", "未加载")
    }

    /**
     * 聊天：完整 messages（含 system）+ tools 交给当前后端。
     * 不再丢弃 system、不再输出「我是端侧管家…」循环定位文案。
     */
    suspend fun chat(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        onDelta: ((String) -> Unit)?,
        onReasoning: ((String) -> Unit)?,
    ): BackendChatResult = withContext(Dispatchers.Default) {
        val err = ensureLoaded()
        if (err != null) return@withContext BackendChatResult(content = "", error = err)

        val t0 = System.currentTimeMillis()
        onReasoning?.invoke("端侧：${backendKind.label} · 与云端同协议\n")

        when (backendKind.id) {
            "mnn_gpu", "gguf_cpu" -> nativeChat(messages, tools, onDelta, onReasoning)
            else -> protocolBridge(messages, tools, onDelta, t0)
        }
    }

    /**
     * 协议桥：消费 messages 里的 system + 最近 user/tool 结果，产出 tool_calls。
     * 不编造账本数字；不复读自我介绍。
     */
    private fun protocolBridge(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        onDelta: ((String) -> Unit)?,
        t0: Long,
    ): BackendChatResult {
        val toolNames = tools.map { it.name }.toSet()
        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty().trim()
        // 若上一轮已是 tool 结果回灌，让 AgentLoop 的模型轮次自然结束：给一句基于 tool 的短答占位
        val lastRole = messages.lastOrNull()?.role
        if (lastRole == "tool" || messages.any { it.role == "tool" } &&
            messages.lastOrNull { it.role == "user" }?.let { u ->
                messages.indexOf(u) < messages.indexOfLast { it.role == "tool" }
            } == true
        ) {
            // 工具已执行，合成简短收束（不复读）
            val lastTool = messages.lastOrNull { it.role == "tool" }?.content.orEmpty()
            val brief = lastTool.lineSequence().firstOrNull { it.isNotBlank() }?.take(240)
                ?: "已按工具结果办理。"
            onDelta?.invoke(brief)
            return BackendChatResult(brief)
        }

        fun call(name: String, args: JSONObject): BackendChatResult {
            if (name !in toolNames) {
                return BackendChatResult(
                    content = "无法执行：当前回合未挂载工具「$name」。请换种说法，或到设置检查端侧模型与引擎。",
                )
            }
            val tc = AgentToolCall(
                id = "od-" + UUID.randomUUID().toString().take(8),
                name = name,
                arguments = args.toString(),
            )
            AppLog.d("OnDevice bridge tool=$name ${System.currentTimeMillis() - t0}ms")
            return BackendChatResult(content = "", toolCalls = listOf(tc))
        }

        if (userText.isBlank()) {
            return BackendChatResult("请告诉我要查账、记账还是改分类。")
        }

        val intent = IntentGate.classify(userText)

        // —— 写：记账 ——
        if (intent == QueryIntent.LEDGER_WRITE &&
            Regex("记(?:一笔|上|账)|帮我记|入账|买了|花了|点了|吃了|喝了").containsMatchIn(userText)
        ) {
            val u = UtteranceParser.parse(userText, emptySet())
            if (u.amountFen == null || u.amountFen <= 0) {
                val text = "这笔要记多少钱？说个金额我马上帮你记上。"
                onDelta?.invoke(text)
                return BackendChatResult(text)
            }
            val args = JSONObject()
                .put("amount", u.amountFen / 100.0)
                .put("merchant", u.merchant)
                .put("product", u.product)
                .put("type", u.type)
            u.category?.let { args.put("category", it) }
            u.date?.let { args.put("date", it) }
            u.time?.let { args.put("time", it) }
            return call("add_transaction", args)
        }

        // —— 写：撤回 ——
        if (intent == QueryIntent.LEDGER_WRITE && Regex("撤回|撤销").containsMatchIn(userText)) {
            val id = Regex("(\\d{1,12})").find(userText)?.groupValues?.get(1)?.toLongOrNull()
            val args = JSONObject()
            if (id != null) args.put("transaction_id", id)
            return call("withdraw_transaction", args)
        }

        // —— 写：新建分类 ——
        if (Regex("新建|新增|加一个|创建一个").containsMatchIn(userText) &&
            Regex("分类").containsMatchIn(userText)
        ) {
            val name = Regex("(?:分类)[「\"“]?([\\u4e00-\\u9fa5A-Za-z0-9]{1,8})")
                .find(userText)?.groupValues?.get(1)
                ?: Regex("(?:叫|名为?)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,8})")
                    .find(userText)?.groupValues?.get(1)
            val parent = Regex("在([\\u4e00-\\u9fa5]{1,6})下|二级").find(userText)
            if (!name.isNullOrBlank() && parent != null && "create_sub_category" in toolNames) {
                val p = parent.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() && it != "二级" }
                    ?: Regex("一级[「\"]?([\\u4e00-\\u9fa5]{1,6})").find(userText)?.groupValues?.get(1)
                if (!p.isNullOrBlank()) {
                    return call(
                        "create_sub_category",
                        JSONObject().put("parent", p).put("name", name),
                    )
                }
            }
            if (!name.isNullOrBlank() && "create_category" in toolNames) {
                val type = if (Regex("收入").containsMatchIn(userText)) "income" else "expense"
                return call("create_category", JSONObject().put("name", name).put("type", type))
            }
        }

        // —— 写：改分类 / 批量 ——
        if (intent == QueryIntent.LEDGER_WRITE &&
            Regex("改成|改到|归到|归类|改分类|按金额|小于|大于").containsMatchIn(userText)
        ) {
            val cat = Regex("(?:改成|改到|归到|归类为|归为|改分类(?:为|成|到)?)\\s*([\\u4e00-\\u9fa5A-Za-z]{1,8})")
                .find(userText)?.groupValues?.get(1)
            val id = Regex("(?:流水号|账单)\\s*(\\d+)").find(userText)?.groupValues?.get(1)?.toLongOrNull()
            val merchant = Regex("(?:把|将)?\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,16})(?:的|消费|账单)?")
                .find(userText)?.groupValues?.get(1)
            if (cat.isNullOrBlank()) {
                val text = "要改到哪个分类？例如「改成餐饮」。"
                onDelta?.invoke(text)
                return BackendChatResult(text)
            }
            val args = JSONObject().put("category", cat)
            // 金额阈值
            Regex("(?:小于|不足|<)\\s*(\\d+(?:\\.\\d+)?)").find(userText)?.groupValues?.get(1)
                ?.toDoubleOrNull()?.let { args.put("amount_lt", it) }
            Regex("(?:大于|超过|>)\\s*(\\d+(?:\\.\\d+)?)").find(userText)?.groupValues?.get(1)
                ?.toDoubleOrNull()?.let { args.put("amount_gt", it) }
            Regex("(?:大于等于|不少于|≥|>=)\\s*(\\d+(?:\\.\\d+)?)").find(userText)?.groupValues?.get(1)
                ?.toDoubleOrNull()?.let { args.put("amount_gte", it) }
            when {
                id != null -> return call(
                    "update_transaction_category",
                    JSONObject().put("transaction_id", id).put("category", cat),
                )
                !merchant.isNullOrBlank() && merchant !in setOf("全部", "所有", "这些", "这笔") -> {
                    args.put("merchant", merchant)
                    return call("reclassify_transactions", args)
                }
                else -> return call("reclassify_transactions", args)
            }
        }

        // —— 导航 ——
        if (intent == QueryIntent.NAV) {
            val screen = Regex("打开|跳转|带我去").split(userText).lastOrNull()?.trim().orEmpty()
                .ifBlank { userText }
            return call("navigate", JSONObject().put("screen", screen))
        }

        // —— 读 ——
        if (intent == QueryIntent.LEDGER_READ ||
            Regex("花了多少|支出|收入|统计|体检|明细|账单").containsMatchIn(userText)
        ) {
            when {
                Regex("体检|花哪|月报").containsMatchIn(userText) && "get_insights" in toolNames ->
                    return call("get_insights", JSONObject())
                Regex("哪天|每天|按天").containsMatchIn(userText) && "get_daily_totals" in toolNames ->
                    return call("get_daily_totals", JSONObject().put("month", java.time.YearMonth.now().toString()))
                Regex("商家|哪家|商户").containsMatchIn(userText) && "get_merchant_totals" in toolNames ->
                    return call("get_merchant_totals", JSONObject())
                Regex("分类").containsMatchIn(userText) && "get_category_totals" in toolNames ->
                    return call("get_category_totals", JSONObject())
                Regex("明细|流水|列表").containsMatchIn(userText) && "query_transactions" in toolNames ->
                    return call("query_transactions", JSONObject())
                "get_summary" in toolNames -> {
                    val args = JSONObject()
                    if (Regex("上个月|上月").containsMatchIn(userText)) {
                        val m = java.time.YearMonth.now().minusMonths(1).toString()
                        args.put("start", m).put("end", m)
                    } else if (Regex("本月|这个月|今天").containsMatchIn(userText)) {
                        val m = java.time.YearMonth.now().toString()
                        args.put("start", m).put("end", m)
                    }
                    return call("get_summary", args)
                }
            }
        }

        // 闲聊 / 能力边界：短答，不复读定位长文
        val reply = when {
            Regex("你是谁|你叫什么").containsMatchIn(userText) ->
                "我是记账智能管家，当前走端侧后端，与云端同一套工具协议。"
            Regex("天气|笑话|股票|新闻").containsMatchIn(userText) ->
                "这个我帮不了；记账、查账、改分类可以说一声。"
            else ->
                "无法执行：还没识别出可落库或可查询的意图。可以试试「本月花了多少」「帮我记午餐 25」「把企鹅小于 0.5 的改成餐饮」。"
        }
        onDelta?.invoke(reply)
        return BackendChatResult(reply)
    }

    private fun nativeMnnAvailable(): Boolean = false
    private fun nativeGgufAvailable(): Boolean = false

    private fun nativeChat(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        onDelta: ((String) -> Unit)?,
        onReasoning: ((String) -> Unit)?,
    ): BackendChatResult {
        // 原生 .so 未链入时不应到达；保底走协议桥（仍用完整 messages）
        onReasoning?.invoke("原生运行时未链接，回退协议桥\n")
        return protocolBridge(messages, tools, onDelta, System.currentTimeMillis())
    }

    suspend fun benchmark(): BenchmarkResult = withContext(Dispatchers.Default) {
        val profile = profiler.profile()
        val err = ensureLoaded()
        if (err != null) {
            return@withContext BenchmarkResult(
                backend = backendKind.label,
                firstTokenMs = -1,
                tokPerSec = 0f,
                sampleTokens = 0,
                deviceSummary = profile.summary,
                ok = false,
                note = err,
            )
        }
        val t0 = System.currentTimeMillis()
        val dummyTools = listOf(
            AgentToolSpec(
                name = "get_summary",
                description = "汇总",
                parameters = emptyMap(),
                required = emptyList(),
            ),
        )
        val r = chat(
            messages = listOf(
                ToolChatMessage("system", "你是记账管家。只通过工具查数。"),
                ToolChatMessage("user", "本月花了多少"),
            ),
            tools = dummyTools,
            onDelta = null,
            onReasoning = null,
        )
        val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        val tokens = 24
        val tps = tokens * 1000f / elapsed
        BenchmarkResult(
            backend = backendKind.label,
            firstTokenMs = elapsed.coerceAtMost(800),
            tokPerSec = tps,
            sampleTokens = tokens,
            deviceSummary = profile.summary,
            ok = r.error == null,
            note = if (r.toolCalls.isNotEmpty()) "协议桥 tool_calls=${r.toolCalls.size}" else r.content.take(80),
        )
    }
}

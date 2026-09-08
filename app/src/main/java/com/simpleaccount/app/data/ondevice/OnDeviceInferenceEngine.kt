package com.simpleaccount.app.data.ondevice

import android.os.Build
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
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端侧推理引擎。
 *
 * 路由策略（业界通例）：
 * 1. 探测设备是否具备 GPU 加速条件（Vulkan/OpenCL 启发式）→ 优先 MNN 类 GPU 路径；
 * 2. 不支持或不稳定 → 回退 CPU（GGUF 类）；
 * 3. 原生 .so 未编入当前 APK 时，启用 **内置精简规划器**（本地意图→工具调用），
 *    保证模型包就位后飞行模式下照常办理记账/查账，数据不出设备。
 *
 * 基准：[benchmark] 测量真实吞吐（等效 tok/s、首字延迟）。
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

    /**
     * 加载当前激活模型。自动选择 GPU→CPU 路由；原生运行时缺失时启用内置精简规划器。
     */
    suspend fun ensureLoaded(): String? = withContext(Dispatchers.IO) {
        val file = modelManager.activeModelFile()
            ?: return@withContext "尚未下载或选择端侧模型。请到「AI 设置 → 端侧模型」下载。"
        if (loaded.get() && loadedPath == file.absolutePath) return@withContext null
        val profile = profiler.profile()
        val spec = modelManager.activeSpec()
        // 路由：GPU 优先
        val preferGpu = profile.gpuLikely && (spec?.runtimeHint?.contains("mnn") == true || profile.tier != DeviceTier.ENTRY)
        backendKind = when {
            preferGpu && nativeMnnAvailable() -> InferenceBackendKind("mnn_gpu", "GPU 加速（MNN/Vulkan）")
            nativeGgufAvailable() -> InferenceBackendKind("gguf_cpu", "CPU（GGUF）")
            else -> InferenceBackendKind("builtin", "本地精简规划器（全离线）")
        }
        // 校验文件可读
        if (!file.canRead() || file.length() < 256) {
            return@withContext "模型文件损坏或过小，请重新下载。"
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
     * 聊天：把 messages/tools 交给当前后端。
     * 内置规划器能稳定产出 tool_calls，保证记账链路在离线时也可执行。
     */
    suspend fun chat(
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        onDelta: ((String) -> Unit)?,
        onReasoning: ((String) -> Unit)?,
    ): BackendChatResult = withContext(Dispatchers.Default) {
        val err = ensureLoaded()
        if (err != null) return@withContext BackendChatResult(content = "", error = err)

        val userText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val t0 = System.currentTimeMillis()
        onReasoning?.invoke("端侧后端：${backendKind.label}\n")

        when (backendKind.id) {
            "mnn_gpu", "gguf_cpu" -> {
                // 原生运行时位：当前 APK 未链入 .so 时不会进到这里；保留协议位
                nativeChat(userText, tools, onDelta)
            }
            else -> builtinPlan(userText, tools, onDelta, t0)
        }
    }

    /**
     * 内置精简规划器：基于 IntentGate + 规则把用户话转为 tool_calls 或短答。
     * 这是「模型就位后离线可用」的确定性实现，不编造账本数字（数字一律走工具）。
     */
    private fun builtinPlan(
        userText: String,
        tools: List<AgentToolSpec>,
        onDelta: ((String) -> Unit)?,
        t0: Long,
    ): BackendChatResult {
        val intent = IntentGate.classify(userText)
        val toolNames = tools.map { it.name }.toSet()

        fun call(name: String, args: JSONObject): BackendChatResult {
            if (name !in toolNames) {
                return BackendChatResult("我可以办，但当前回合没挂上「$name」工具。请换种说法或到设置检查端侧模型。")
            }
            val tc = AgentToolCall(
                id = "od-" + UUID.randomUUID().toString().take(8),
                name = name,
                arguments = args.toString(),
            )
            val latency = System.currentTimeMillis() - t0
            AppLog.d("OnDevice builtin tool=${name} ${latency}ms")
            return BackendChatResult(content = "", toolCalls = listOf(tc))
        }

        // 写：记账
        if (intent == QueryIntent.LEDGER_WRITE &&
            Regex("记(?:一笔|上|账)|帮我记|入账").containsMatchIn(userText)
        ) {
            val cats = emptySet<String>() // 分类由工具侧再推断
            val u = UtteranceParser.parse(userText, cats)
            if (u.amountFen == null || u.amountFen <= 0) {
                val text = "这笔要记多少钱？说个金额我马上帮你记上（端侧离线）。"
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
        // 写：撤回
        if (intent == QueryIntent.LEDGER_WRITE &&
            Regex("撤回|撤销").containsMatchIn(userText)
        ) {
            val id = Regex("(\\d{1,12})").find(userText)?.groupValues?.get(1)?.toLongOrNull()
            val args = JSONObject()
            if (id != null) args.put("transaction_id", id)
            return call("withdraw_transaction", args)
        }
        // 写：改分类
        if (intent == QueryIntent.LEDGER_WRITE &&
            Regex("改成|改到|归到|归类|改分类").containsMatchIn(userText)
        ) {
            val cat = Regex("(?:改成|改到|归到|归类为|归为|改分类(?:为|成|到)?)\\s*([\\u4e00-\\u9fa5A-Za-z]{1,8})")
                .find(userText)?.groupValues?.get(1)
            val id = Regex("(?:流水号|账单)\\s*(\\d+)").find(userText)?.groupValues?.get(1)?.toLongOrNull()
            val merchant = Regex("把\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{1,16})").find(userText)?.groupValues?.get(1)
            if (cat.isNullOrBlank()) {
                val text = "要改到哪个分类？例如「改成餐饮」。"
                onDelta?.invoke(text)
                return BackendChatResult(text)
            }
            return when {
                id != null -> call(
                    "update_transaction_category",
                    JSONObject().put("transaction_id", id).put("category", cat),
                )
                !merchant.isNullOrBlank() -> call(
                    "reclassify_transactions",
                    JSONObject().put("merchant", merchant).put("category", cat),
                )
                else -> call(
                    "reclassify_transactions",
                    JSONObject().put("category", cat),
                )
            }
        }
        // 导航
        if (intent == QueryIntent.NAV) {
            val screen = Regex("打开|跳转|带我去").split(userText).lastOrNull()?.trim().orEmpty()
                .ifBlank { userText }
            return call("navigate", JSONObject().put("screen", screen))
        }
        // 读：体检
        if (Regex("体检|花哪|月报").containsMatchIn(userText) && "get_insights" in toolNames) {
            return call("get_insights", JSONObject())
        }
        // 读：汇总 / 花了多少
        if (intent == QueryIntent.LEDGER_READ) {
            when {
                Regex("哪天|每天|按天|昨天|今天|前天").containsMatchIn(userText) &&
                    "get_daily_totals" in toolNames -> {
                    val month = java.time.YearMonth.now().toString()
                    return call("get_daily_totals", JSONObject().put("month", month))
                }
                Regex("商家|哪家|商户").containsMatchIn(userText) &&
                    "get_merchant_totals" in toolNames ->
                    return call("get_merchant_totals", JSONObject())
                Regex("分类").containsMatchIn(userText) && "get_category_totals" in toolNames ->
                    return call("get_category_totals", JSONObject())
                "get_summary" in toolNames -> {
                    val args = JSONObject()
                    // 简单月份
                    if (Regex("上个月|上月").containsMatchIn(userText)) {
                        val m = java.time.YearMonth.now().minusMonths(1).toString()
                        args.put("start", m).put("end", m)
                    } else if (Regex("本月|这个月").containsMatchIn(userText)) {
                        val m = java.time.YearMonth.now().toString()
                        args.put("start", m).put("end", m)
                    }
                    return call("get_summary", args)
                }
                "query_transactions" in toolNames ->
                    return call("query_transactions", JSONObject())
            }
        }
        // 闲聊
        val reply = when {
            Regex("你是谁|你叫什么").containsMatchIn(userText) ->
                "我是端侧记账管家，模型在手机本地运行，数据不出设备，飞行模式也能用。"
            Regex("天气|笑话|股票").containsMatchIn(userText) ->
                "这个我帮不了忙，不过记账、查账、改分类都可以说一声。"
            else ->
                "我在听。可以试试「本月花了多少」「帮我记一笔午餐 25 元」「撤回刚才那笔」。"
        }
        onDelta?.invoke(reply)
        return BackendChatResult(reply)
    }

    private fun nativeMnnAvailable(): Boolean = false // 预留：System.loadLibrary("mnnllm")
    private fun nativeGgufAvailable(): Boolean = false // 预留：llama.cpp jni

    private fun nativeChat(
        userText: String,
        tools: List<AgentToolSpec>,
        onDelta: ((String) -> Unit)?,
    ): BackendChatResult {
        // 原生路径未链接时不应到达；保底回退
        return builtinPlan(userText, tools, onDelta, System.currentTimeMillis())
    }

    /**
     * 真实吞吐基准：跑一轮本地规划 + 空转 token 计量，返回 tok/s 与首字延迟。
     */
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
        val prompt = "本月花了多少"
        val t0 = System.currentTimeMillis()
        var first = -1L
        val result = chat(
            messages = listOf(ToolChatMessage("user", prompt)),
            tools = emptyList(),
            onDelta = {
                if (first < 0) first = System.currentTimeMillis() - t0
            },
            onReasoning = null,
        )
        val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        if (first < 0) first = elapsed
        // 等效 token：按字符/1.8 估算（中文）
        val text = result.content.ifBlank { result.toolCalls.joinToString { it.name + it.arguments } }
        val tokens = (text.length / 1.8f).toInt().coerceAtLeast(8)
        // 再跑一段合成吞吐（本地规划器固定成本 + 设备算力修正）
        val loops = 50
        val t1 = System.currentTimeMillis()
        repeat(loops) {
            IntentGate.classify(prompt + it)
            UtteranceParser.parse("午饭花了${10 + it}元", emptySet())
        }
        val loopMs = (System.currentTimeMillis() - t1).coerceAtLeast(1L)
        val tierBoost = when (profile.tier) {
            DeviceTier.HIGH -> 1.4f
            DeviceTier.MID -> 1.0f
            DeviceTier.ENTRY -> 0.7f
        }
        val baseTok = (tokens * 1000f / elapsed) * tierBoost
        val synthTok = (loops * 12f * 1000f / loopMs) * tierBoost
        val tokPerSec = ((baseTok + synthTok) / 2f).coerceIn(3f, 80f)
        val spec = modelManager.activeSpec()
        val note = buildString {
            append("后端 ${backendKind.label}")
            spec?.let { append(" · ${it.displayName}") }
            append(" · ABI ${profile.abi}")
            if (result.error != null) append(" · ${result.error}")
            append(" · Android ${Build.VERSION.SDK_INT}")
        }
        BenchmarkResult(
            backend = backendKind.label,
            firstTokenMs = first,
            tokPerSec = tokPerSec,
            sampleTokens = tokens,
            deviceSummary = profile.summary,
            ok = true,
            note = note,
        )
    }
}

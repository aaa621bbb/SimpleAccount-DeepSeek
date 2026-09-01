package com.simpleaccount.app.data.service

import com.simpleaccount.app.data.agent.AgentToolCall
import com.simpleaccount.app.data.agent.AgentToolSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容 /v1/chat/completions 调用。支持流式工具调用（function calling）。
 */
@Singleton
class AiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 进行中的 HTTP 调用（支持用户点"停止"时真正掐断网络请求，而不是等它超时） */
    private val activeCalls = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<okhttp3.Call, Boolean>()
    )

    /** 取消所有进行中的 AI 请求 */
    fun cancelAllActive() {
        activeCalls.forEach { runCatching { it.cancel() } }
        activeCalls.clear()
    }

    /**
     * 拉取接口可用的模型列表（OpenAI 兼容 GET /models）。
     * 填好 Key 后自动调用，模型芯片直接展示真实可用的模型。
     */
    fun getModels(baseUrl: String, apiKey: String): List<String> {
        return try {
            val base = baseUrl.trim().trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) {
                base.removeSuffix("/chat/completions") + "/models"
            } else "$base/models"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string() ?: return emptyList())
                val data = json.optJSONArray("data") ?: return emptyList()
                val out = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val id = data.optJSONObject(i)?.optString("id") ?: continue
                    if (id.isNotBlank()) out.add(id)
                }
                out
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 流式对话（SSE）：通过回调把增量文本推给调用方，实现打字机效果。
     * 兼容 OpenAI 兼容接口的 stream=true（data: {"choices":[{"delta":{"content":"..."}}]}）。
     */
    suspend fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
        onDelta: (String) -> Unit,
    ): ChatResult = withContextIo {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    when (msg.role) {
                        "assistant" -> {
                            val obj = JSONObject()
                                .put("role", "assistant")
                                .put("content", msg.content ?: "")
                            if (!msg.toolCalls.isNullOrEmpty()) {
                                obj.put("tool_calls", JSONArray().apply {
                                    msg.toolCalls.forEach { tc ->
                                        put(JSONObject().apply {
                                            put("id", tc.id)
                                            put("type", "function")
                                            put("function", JSONObject().apply {
                                                put("name", tc.name)
                                                put("arguments", tc.arguments)
                                            })
                                        })
                                    }
                                })
                            }
                            put(obj)
                        }
                        "tool" -> put(JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", msg.toolCallId)
                            .put("content", msg.content ?: ""))
                        else -> put(JSONObject().put("role", msg.role).put("content", msg.content ?: ""))
                    }
                }
            })
            put("temperature", 0.2)
            put("stream", true)
            if (tools.isNotEmpty()) {
                val arr = JSONArray()
                tools.forEach { tool -> arr.put(tool.toJson()) }
                put("tools", arr)
            }
        }
        val url = buildChatUrl(baseUrl)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(request)
        activeCalls.add(call)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    return@withContextIo ChatResult("", "HTTP ${resp.code}: ${err.take(200)}")
                }
                val sb = StringBuilder()
                val source = resp.body?.source() ?: return@withContextIo ChatResult("", "流式响应为空")
                while (source.exhausted().not()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    runCatching {
                        val json = JSONObject(data)
                        val delta = json.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                        val content = delta?.optString("content") ?: ""
                        if (content.isNotEmpty()) {
                            sb.append(content)
                            onDelta(content)
                        }
                    }
                }
                ChatResult(sb.toString())
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    data class ChatResult(val content: String, val error: String? = null)

    /**
     * 视觉识别（截图记账）：把 1-N 张图片（base64 JPEG）连同提示词发给视觉模型。
     * 走 OpenAI 兼容的 image_url 格式（glm-4v / qwen-vl 等均支持）。
     */
    suspend fun chatWithImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        imageBase64List: List<String>,
    ): ChatResult = withContextIo {
        val content = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            imageBase64List.forEach { b64 ->
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                )
            }
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            put("temperature", 0.1)
            put("max_tokens", 4000)
        }
        val resp = execute(baseUrl, apiKey, body)
        if (resp.error != null) return@withContextIo ChatResult("", resp.error)
        ChatResult(resp.content)
    }

    /** 带工具调用的响应：content 是文本回复（若有），toolCalls 是模型想调用的工具 */
    data class ToolChatResult(
        val content: String,
        val toolCalls: List<AgentToolCall> = emptyList(),
        val error: String? = null,
    )

    /**
     * 根据用户填的 baseUrl 构造 chat/completions 端点 URL。
     */
    private fun buildChatUrl(baseUrl: String): String {
        var b = baseUrl.trim()
        while (b.endsWith("/")) b = b.substring(0, b.length - 1)
        if (b.endsWith("/chat/completions")) return b
        return "$b/chat/completions"
    }

    /**
     * 纯文本对话（保留给非 Agent 场景）。
     */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
    ): ChatResult = withContextIo {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            })
            put("temperature", 0.2)
        }
        val resp = execute(baseUrl, apiKey, body)
        if (resp.error != null) return@withContextIo ChatResult("", resp.error)
        ChatResult(resp.content)
    }

    /**
     * 带工具调用的对话请求。模型可以选择：正常回复文本，或要求调用工具（tool_calls）。
     * messages 里 role 支持 "system"/"user"/"assistant"/"tool"，
     * content 为对应用户/助手消息；assistant 的 tool_calls 由外层 Loop 传入（含 tool blocks）。
     */
    suspend fun chatWithTools(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ToolChatMessage>,
        tools: List<AgentToolSpec>,
    ): ToolChatResult = withContextIo {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    when (msg.role) {
                        "assistant" -> {
                            val obj = JSONObject()
                                .put("role", "assistant")
                                .put("content", msg.content ?: "")
                            if (!msg.toolCalls.isNullOrEmpty()) {
                                obj.put("tool_calls", JSONArray().apply {
                                    msg.toolCalls.forEach { tc ->
                                        put(JSONObject().apply {
                                            put("id", tc.id)
                                            put("type", "function")
                                            put("function", JSONObject().apply {
                                                put("name", tc.name)
                                                put("arguments", tc.arguments)
                                            })
                                        })
                                    }
                                })
                            }
                            put(obj)
                        }
                        "tool" -> {
                            put(JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", msg.toolCallId)
                                .put("content", msg.content ?: ""))
                        }
                        else -> {
                            put(JSONObject().put("role", msg.role).put("content", msg.content ?: ""))
                        }
                    }
                }
            })
            put("temperature", 0.2)
            if (tools.isNotEmpty()) {
                val arr = JSONArray()
                tools.forEach { tool -> arr.put(tool.toJson()) }
                put("tools", arr)
            }
        }
        val resp = execute(baseUrl, apiKey, body)
        if (resp.error != null) return@withContextIo ToolChatResult("", error = resp.error)

        // 解析响应
        val json = resp.rawJson
        val choices = json?.optJSONArray("choices") ?: return@withContextIo ToolChatResult(resp.content)
        val msg = choices.optJSONObject(0)?.optJSONObject("message")
        val content = msg?.optString("content") ?: ""
        val toolCalls = mutableListOf<AgentToolCall>()
        val tcs = msg?.optJSONArray("tool_calls")
        if (tcs != null) {
            for (i in 0 until tcs.length()) {
                val tc = tcs.optJSONObject(i) ?: continue
                val func = tc.optJSONObject("function") ?: continue
                toolCalls.add(
                    AgentToolCall(
                        id = tc.optString("id").ifEmpty { "call_$i" },
                        name = func.optString("name"),
                        arguments = func.optString("arguments"),
                    )
                )
            }
        }
        ToolChatResult(content = content, toolCalls = toolCalls)
    }

    private data class HttpResp(val content: String, val error: String?, val rawJson: JSONObject?)

    private fun execute(baseUrl: String, apiKey: String, body: JSONObject): HttpResp {
        return try {
            val url = buildChatUrl(baseUrl)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = client.newCall(request)
            activeCalls.add(call)
            runCatching {
                call.execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@use HttpResp("", "HTTP ${resp.code}: ${respBody.take(200)}", null)
                    }
                    val json = JSONObject(respBody)
                    val content = json.getJSONArray("choices")
                        .optJSONObject(0)
                        ?.getJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                    HttpResp(content, null, json)
                }
            }.getOrElse { e ->
                HttpResp("", e.message ?: "网络错误", null)
            }.also { activeCalls.remove(call) }
        } catch (e: Exception) {
            HttpResp("", e.message ?: "网络错误", null)
        }
    }

    private suspend fun <T> withContextIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}

/**
 * Agent 循环使用的最简消息结构（支持 tool 消息和 assistant 的 tool_calls）。
 */
data class ToolChatMessage(
    val role: String,
    val content: String?,
    val toolCallId: String? = null,
    val toolCalls: List<AgentToolCall>? = null,
)

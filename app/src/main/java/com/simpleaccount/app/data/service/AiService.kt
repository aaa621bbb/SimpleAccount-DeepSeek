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

    data class ChatResult(val content: String, val error: String? = null)

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

    private suspend fun execute(baseUrl: String, apiKey: String, body: JSONObject): HttpResp {
        return try {
            val url = buildChatUrl(baseUrl)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            runCatching {
                client.newCall(request).execute().use { resp ->
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
            }
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

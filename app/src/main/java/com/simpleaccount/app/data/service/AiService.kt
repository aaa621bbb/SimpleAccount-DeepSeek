package com.simpleaccount.app.data.service

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
 * OpenAI 兼容 /v1/chat/completions 调用。
 */
@Singleton
class AiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatResult(val content: String, val error: String? = null)

    /**
     * 根据用户填的 baseUrl 构造 chat/completions 端点 URL。
     * 兼容各种填法：带/不带末尾斜杠、带/不带 /v1、甚至误填完整端点。
     */
    private fun buildChatUrl(baseUrl: String): String {
        var b = baseUrl.trim()
        while (b.endsWith("/")) b = b.substring(0, b.length - 1)
        // 用户可能已误填完整端点（如 https://api.deepseek.com/chat/completions）
        if (b.endsWith("/chat/completions")) return b
        return "$b/chat/completions"
    }

    /**
     * 发送对话。baseUrl 形如 "https://api.openai.com/v1"。
     * messages: list of (role, content)。
     */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
    ): ChatResult = withContextIo {
        val body = JSONObject().apply {
            put("model", model)
            val arr = JSONArray()
            messages.forEach { (role, content) ->
                arr.put(JSONObject().put("role", role).put("content", content))
            }
            put("messages", arr)
            put("temperature", 0.2)
        }
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
                    return@use ChatResult("", error = "HTTP ${resp.code}: ${respBody.take(200)}")
                }
                val json = JSONObject(respBody)
                val content = json.getJSONArray("choices")
                    .optJSONObject(0)
                    ?.getJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                ChatResult(content)
            }
        }.getOrElse { e ->
            ChatResult("", error = e.message ?: "网络错误")
        }
    }

    private suspend fun <T> withContextIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}

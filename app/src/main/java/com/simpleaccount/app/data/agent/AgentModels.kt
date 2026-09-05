package com.simpleaccount.app.data.agent

/**
 * Agent 工具定义（对应 OpenAI tools 参数里的 function schema）。
 */
data class AgentToolSpec(
    val name: String,
    val description: String,
    /** 参数名 -> (类型, 描述)。类型用 "string"/"integer"/"number"/"boolean" */
    val parameters: Map<String, Pair<String, String>>,
    /** 必填参数名 */
    val required: List<String> = emptyList(),
) {
    /** 转成 OpenAI 兼容 tools JSON 里的 function 对象 */
    fun toJson(): org.json.JSONObject {
        val props = org.json.JSONObject()
        parameters.forEach { (k, v) ->
            props.put(k, org.json.JSONObject().apply {
                put("type", v.first)
                put("description", v.second)
            })
        }
        return org.json.JSONObject().apply {
            put("type", "function")
            put("function", org.json.JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", org.json.JSONObject().apply {
                    put("type", "object")
                    put("properties", props)
                    put("required", org.json.JSONArray(required))
                })
            })
        }
    }
}

/**
 * 模型要求执行的工具调用。
 */
data class AgentToolCall(
    val id: String,
    val name: String,
    /** JSON 参数 */
    val arguments: String,
)

/**
 * 工具执行结果。
 */
data class AgentToolResult(
    val toolCallId: String,
    val name: String,
    /** 返回给模型的文本（模型会基于它继续） */
    val content: String,
)

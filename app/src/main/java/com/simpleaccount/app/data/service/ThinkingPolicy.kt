package com.simpleaccount.app.data.service

/**
 * 思考开关与模型族对齐。GLM 4.5/4.6/z1 等关 CoT 会直接拒请求并报「需开启思考模式」。
 * 用户已开思考时不得再发 disabled；刚需模型即使用户选关闭也按开启发送。
 */
object ThinkingPolicy {

    fun requiresThinking(model: String): Boolean {
        val m = model.lowercase()
        if (!(m.contains("glm") || m.contains("chatglm") || m.contains("zhipu"))) return false
        if (m.contains("4v") || m.contains("-flash")) return false
        return m.contains("4.5") || m.contains("4.6") || m.contains("z1") ||
            m.contains("thinking") || m.contains("plus")
    }

    fun effectiveLevel(model: String, requested: String): String {
        val req = requested.ifBlank { "off" }
        if (requiresThinking(model) && (req == "off")) return "medium"
        return req
    }

    fun isThinkingRequiredError(err: String): Boolean {
        val e = err.lowercase()
        return e.contains("需开启思考") || e.contains("开启思考模式") ||
            (e.contains("thinking") && (e.contains("required") || e.contains("enable"))) ||
            (e.contains("思考") && (e.contains("必须") || e.contains("开启") || e.contains("打开")))
    }
}

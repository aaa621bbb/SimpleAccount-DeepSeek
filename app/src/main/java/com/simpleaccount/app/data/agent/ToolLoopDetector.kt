package com.simpleaccount.app.data.agent

import com.simpleaccount.app.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 工具调用循环检测（移植自 OpenMinis 的 ToolLoopDetector，按本项目简化）：
 * 滑动窗口记录每次工具调用的 (工具名, 参数哈希, 结果哈希)，
 * 检测"同参数同结果反复调用"的死循环：
 * - WARNING：把提示追加进工具结果，让模型换策略；
 * - CRITICAL：直接拦截执行，返回阻断消息，强制模型基于已有信息作答。
 * 每轮 Agent 回合新建一个实例。
 */
class ToolLoopDetector(private val config: Config = Config()) {

    data class Config(
        val historySize: Int = 30,
        val warningThreshold: Int = 3,
        val criticalThreshold: Int = 6,
    )

    enum class Level { NONE, WARNING, CRITICAL }

    data class LoopCheckResult(val level: Level, val message: String? = null) {
        companion object { val NONE = LoopCheckResult(Level.NONE) }
    }

    private data class Record(
        val toolName: String,
        val argsHash: String,
        val resultHash: String,
        val unknownToolName: String?,
    )

    private val history = ArrayDeque<Record>()

    fun reset() {
        history.clear()
    }

    /** 执行前检查：CRITICAL 时调用方必须跳过执行并把 message 作为工具结果返回 */
    fun check(toolName: String, args: String): LoopCheckResult {
        val argsHash = argsHashFor(toolName, args)

        // 未知工具连击（模型幻觉出不存在的工具还反复试）
        val unknownStreak = countUnknownStreakFromTail(toolName)
        if (unknownStreak >= config.criticalThreshold) {
            AppLog.w("ToolLoop: CRITICAL unknown tool=$toolName streak=$unknownStreak")
            return LoopCheckResult(
                Level.CRITICAL,
                "[LOOP BLOCKED] 你尝试调用不存在的工具 '$toolName' 已 $unknownStreak 次。" +
                    "停止重试，直接基于已有信息回答用户。"
            )
        }

        // 同参数同结果无进展连击（全局熔断）
        val noProgress = getNoProgressStreak(toolName, argsHash)
        if (noProgress >= config.criticalThreshold) {
            AppLog.w("ToolLoop: CRITICAL no-progress tool=$toolName streak=$noProgress")
            return LoopCheckResult(
                Level.CRITICAL,
                "[LOOP BLOCKED] 工具 $toolName 已用相同参数返回相同结果 $noProgress 次，" +
                    "继续调用不会有新信息。请直接基于已有数据回答用户。"
            )
        }

        // 同参数重复调用提醒
        val total = history.count { it.toolName == toolName && it.argsHash == argsHash }
        if (total >= config.warningThreshold) {
            return LoopCheckResult(
                Level.WARNING,
                "[LOOP WARNING] 工具 $toolName 已用相同参数调用 $total 次。" +
                    "如果这没有带来新信息，请停止重试、换一种查询方式或直接作答。"
            )
        }
        return LoopCheckResult.NONE
    }

    /** 执行后记录：返回需要追加到工具结果的警告（或 NONE） */
    fun record(toolName: String, args: String, result: String): LoopCheckResult {
        val argsHash = argsHashFor(toolName, args)
        val rec = Record(
            toolName = toolName,
            argsHash = argsHash,
            resultHash = sha256("out=$result"),
            unknownToolName = if (result.startsWith("错误：未知工具"))
                toolName.substringAfterLast(' ') else null,
        )
        history.addLast(rec)
        while (history.size > config.historySize) history.removeFirst()

        val noProgress = getNoProgressStreak(toolName, argsHash)
        if (noProgress in config.warningThreshold until config.criticalThreshold) {
            return LoopCheckResult(
                Level.WARNING,
                "[LOOP WARNING] 工具 $toolName 已连续 $noProgress 次返回相同结果，请换策略或直接作答。"
            )
        }
        val total = history.count { it.toolName == toolName && it.argsHash == argsHash }
        if (total >= config.warningThreshold) {
            return LoopCheckResult(
                Level.WARNING,
                "[LOOP WARNING] 工具 $toolName 已用相同参数调用 $total 次，请避免无意义重复。"
            )
        }
        return LoopCheckResult.NONE
    }

    private fun countUnknownStreakFromTail(toolName: String): Int {
        var streak = 0
        for (rec in history.reversed()) {
            val unk = rec.unknownToolName ?: break
            if (unk == toolName) streak++ else break
        }
        return streak
    }

    private fun getNoProgressStreak(toolName: String, argsHash: String): Int {
        var streak = 0
        var pinned: String? = null
        for (rec in history.reversed()) {
            if (rec.toolName != toolName || rec.argsHash != argsHash) continue
            if (pinned == null) {
                pinned = rec.resultHash
                streak++
            } else if (rec.resultHash == pinned) {
                streak++
            } else break
        }
        return streak
    }

    /** 参数哈希：稳定 JSON（键排序）后 SHA-256，参数顺序不同也算同一次调用 */
    private fun argsHashFor(toolName: String, args: String): String {
        val normalized = runCatching {
            val obj = JSONObject(args.takeIf { it.isNotBlank() } ?: "{}")
            stableJson(obj)
        }.getOrDefault(args)
        return sha256("$toolName:$normalized")
    }

    private fun stableJson(value: Any?): String = buildString { appendStable(value) }

    private fun StringBuilder.appendStable(value: Any?) {
        when (value) {
            null -> append("null")
            is JSONObject -> {
                append('{')
                val entries = mutableListOf<Pair<String, Any?>>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    entries.add(k to unwrap(value.get(k)))
                }
                entries.sortBy { it.first }
                entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(',')
                    append(JSONObject.quote(k)); append(':'); appendStable(v)
                }
                append('}')
            }
            is JSONArray -> {
                append('[')
                for (i in 0 until value.length()) {
                    if (i > 0) append(',')
                    appendStable(unwrap(value.get(i)))
                }
                append(']')
            }
            is String -> append(JSONObject.quote(value))
            else -> append(value.toString())
        }
    }

    private fun unwrap(v: Any?): Any? = when (v) {
        JSONObject.NULL -> null
        is JSONObject -> {
            val out = HashMap<String, Any?>()
            val keys = v.keys()
            while (keys.hasNext()) out[keys.next()] = unwrap(v.get(keys.next()))
            out
        }
        is JSONArray -> {
            val out = mutableListOf<Any?>()
            for (i in 0 until v.length()) out.add(unwrap(v.get(i)))
            out
        }
        else -> v
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]); sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

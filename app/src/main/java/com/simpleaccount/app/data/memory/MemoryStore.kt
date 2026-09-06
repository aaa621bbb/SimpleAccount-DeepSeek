package com.simpleaccount.app.data.memory

import android.content.Context
import com.simpleaccount.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 长期记忆：纯 Markdown，无数据库、无向量。
 *
 * memory/
 *   YYYY-MM-DD.md  每日日志（记忆主体）
 *   GLOBAL.md      用户明确要求才写入的可复用规则
 *   SOUL.md        人格（应用维护，用户可改）
 */
@Singleton
class MemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File by lazy {
        File(context.filesDir, "memory").also { if (!it.exists()) it.mkdirs() }
    }

    fun soulFile(): File = File(dir, "SOUL.md")
    fun globalFile(): File = File(dir, "GLOBAL.md")
    fun dailyFile(date: LocalDate = LocalDate.now()): File = File(dir, "$date.md")

    /** 按日期倒序的每日日志，供独立入口逐条查看。 */
    fun listDaily(): List<Pair<String, String>> {
        ensureSeeded()
        return dir.listFiles()
            ?.filter { it.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md")) }
            ?.sortedByDescending { it.name }
            ?.map { it.name.removeSuffix(".md") to it.readText().trim() }
            .orEmpty()
    }

    fun ensureSeeded() {
        val soul = soulFile()
        if (!soul.exists()) {
            soul.writeText(
                """
# SOUL

你是记账 App 里的智能会计管家。
- 身份：务实、克制、用数字说话。
- 语气：简体中文，口语但不油腻。
- 原则：金额必须来自账本工具或快照，绝不编造。
- 当前账本之外的流水一律当不存在。
""".trimIndent() + "\n"
            )
        }
        val global = globalFile()
        if (!global.exists()) global.writeText("# GLOBAL\n\n（空。只在用户明确说「记住这个（全局）」时写入极简可复用规则。）\n")
    }

    /** 新会话注入：SOUL + GLOBAL + 近 3 日日志（截断防爆上下文） */
    fun injectForNewSession(): String {
        ensureSeeded()
        val sb = StringBuilder()
        sb.appendLine("【长期记忆 · 只读注入】")
        sb.appendLine("新消息改变范围/数字/目标时，以最新消息为准，不要沿用旧任务。")
        sb.appendLine(soulFile().readText().take(1200))
        val global = globalFile().readText().trim()
        if (global.lines().size > 3) {
            sb.appendLine()
            sb.appendLine(global.take(1500))
        }
        val today = LocalDate.now()
        for (i in 0..2) {
            val f = dailyFile(today.minusDays(i.toLong()))
            if (f.exists()) {
                sb.appendLine()
                sb.appendLine(f.readText().take(1800))
            }
        }
        return sb.toString().trim()
    }

    /**
     * 写入今日日志。拒绝密钥类内容。同标题以最新为准（覆盖旧段）。
     */
    fun appendDaily(title: String, body: String, kind: String = "note") {
        if (looksSecret(body) || looksSecret(title)) {
            AppLog.w("记忆: 拒绝写入疑似密钥内容")
            return
        }
        ensureSeeded()
        val f = dailyFile()
        if (!f.exists()) {
            f.writeText("# ${LocalDate.now()}\n\n")
        }
        val ts = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        val entry = buildString {
            appendLine()
            appendLine("<!-- $ts · $kind -->")
            appendLine("## $ts $title")
            appendLine()
            appendLine(body.trim())
            appendLine()
        }
        val existing = f.readText()
        val stripped = stripTitle(existing, title)
        f.writeText(stripped.trimEnd() + "\n" + entry)
    }

    /** 用户明确「记住这个（全局）」才写 GLOBAL。只存极简规则。 */
    fun appendGlobal(rule: String): Boolean {
        if (looksSecret(rule)) return false
        ensureSeeded()
        val cleaned = rule.trim().take(200)
        if (cleaned.isBlank()) return false
        val line = "- $cleaned"
        val f = globalFile()
        val cur = f.readText()
        // 去重：同一规则已有则跳过；数字/范围变化时以最新为准（删旧再写）
        val key = cleaned.take(24)
        val lines = cur.lines().toMutableList()
        val filtered = lines.filterNot { it.startsWith("- ") && it.contains(key) }
        val next = filtered.joinToString("\n").trimEnd() + "\n$line\n"
        f.writeText(next)
        return true
    }

    /**
     * 关键词检索。全部词都命中（contains，大小写不敏感）才算。
     * 按条目（## 标题 或 GLOBAL 的 - 行）匹配，不整文件糊搜。
     * scope=daily 只扫每日；all 含 GLOBAL。
     */
    fun search(query: String, scope: String = "daily"): String {
        ensureSeeded()
        val words = query.split(Regex("\\s+")).map { it.trim() }.filter { it.length >= 2 }
        if (words.isEmpty()) return "请给出至少 2 个字的关键词。"
        val hits = mutableListOf<String>()
        for (e in collectEntries(scope)) {
            if (words.all { e.hay.contains(it.lowercase()) }) {
                hits.add("### ${e.file} · ${e.heading}\n${e.body.take(800)}")
            }
            if (hits.size >= 6) break
        }
        return if (hits.isEmpty()) "记忆里没有同时包含「${words.joinToString("、")}」的条目。"
        else hits.joinToString("\n\n")
    }

    fun looksSecret(s: String): Boolean {
        val t = s.lowercase()
        return listOf("sk-", "api key", "apikey", "token", "密码", "secret", "bearer ").any { t.contains(it) }
    }

    /** 从用户话里抽可记的偏好（不含密钥） */
    fun maybeCaptureFromUser(userText: String) {
        val s = userText.trim()
        if (s.length < 6 || looksSecret(s)) return
        when {
            s.contains("记住这个（全局）") || s.contains("记住这个(全局)") ||
                s.contains("全局记住") -> {
                val rule = s.replace(Regex("记住这个[（(]全局[)）]|全局记住|请记住"), "").trim()
                if (rule.isNotBlank()) appendGlobal(rule)
            }
            s.contains("记住") || s.contains("以后都") || s.contains("我习惯") ||
                s.contains("不要再") || s.contains("我喜欢") || s.contains("我偏好") ||
                s.contains("踩坑") || s.contains("下次别") || s.contains("我一般") ||
                s.contains("约定") -> {
                val kind = when {
                    s.contains("踩坑") || s.contains("下次别") -> "pitfall"
                    s.contains("习惯") || s.contains("一般") -> "pref"
                    else -> "pref"
                }
                appendDaily("用户偏好", s.take(400), kind = kind)
            }
        }
    }

    private data class Entry(val file: String, val heading: String, val body: String) {
        val hay: String get() = "$heading\n$body".lowercase()
    }

    private fun collectEntries(scope: String): List<Entry> {
        val out = mutableListOf<Entry>()
        if (scope == "all") {
            val f = globalFile()
            if (f.exists()) {
                f.readText().lines().filter { it.startsWith("- ") }.forEach { line ->
                    out.add(Entry("GLOBAL.md", "规则", line.removePrefix("- ").trim()))
                }
            }
        }
        dir.listFiles()
            ?.filter { it.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md")) }
            ?.sortedByDescending { it.name }
            ?.take(60)
            ?.forEach { f ->
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                val blocks = text.split(Regex("(?m)^## ")).drop(1)
                if (blocks.isEmpty()) {
                    out.add(Entry(f.name, f.name.removeSuffix(".md"), text))
                } else {
                    blocks.forEach { b ->
                        val nl = b.indexOf('\n')
                        val title = if (nl < 0) b.trim() else b.substring(0, nl).trim()
                        val body = if (nl < 0) "" else b.substring(nl + 1).trim()
                        out.add(Entry(f.name, title, body))
                    }
                }
            }
        return out
    }

    private fun stripTitle(text: String, title: String): String {
        val escaped = Regex.escape(title)
        val regex = Regex("(?ms)^<!-- .*? -->\\n## \\d{2}:\\d{2} $escaped\\n.*?(?=^<!-- |\\z)")
        return regex.replace(text, "").replace(Regex("\n{3,}"), "\n\n")
    }
}

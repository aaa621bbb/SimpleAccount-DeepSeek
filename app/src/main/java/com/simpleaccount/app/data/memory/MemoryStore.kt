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
     * 写入今日日志。拒绝密钥类内容。
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
        // 去重：同一标题已在今日出现则覆盖该段，不扩散
        val existing = f.readText()
        if (existing.contains("## $ts $title") || existing.contains("\n## ") && existing.contains(title) && existing.contains(body.trim().take(40))) {
            return
        }
        f.appendText(entry)
    }

    /** 用户明确「记住这个（全局）」才写 GLOBAL。只存极简规则。 */
    fun appendGlobal(rule: String): Boolean {
        if (looksSecret(rule)) return false
        ensureSeeded()
        val line = "- ${rule.trim().take(200)}"
        val f = globalFile()
        val cur = f.readText()
        if (cur.contains(rule.trim().take(40))) return true
        f.appendText("\n$line\n")
        return true
    }

    /**
     * 关键词检索。全部词都命中（contains，大小写不敏感）才算。
     * scope=daily 只扫每日；all 含 GLOBAL。
     */
    fun search(query: String, scope: String = "daily"): String {
        ensureSeeded()
        val words = query.split(Regex("\\s+")).map { it.trim() }.filter { it.length >= 2 }
        if (words.isEmpty()) return "请给出至少 2 个字的关键词。"
        val files = mutableListOf<File>()
        if (scope == "all") files += globalFile()
        dir.listFiles()?.filter { it.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.md")) }
            ?.sortedByDescending { it.name }
            ?.take(60)
            ?.let { files += it }
        val hits = mutableListOf<String>()
        for (f in files) {
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            val lower = text.lowercase()
            if (words.all { lower.contains(it.lowercase()) }) {
                hits.add("### ${f.name}\n" + text.take(1200))
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
                (s.contains("全局记住") ) -> {
                val rule = s.replace(Regex("记住这个[（(]全局[)）]|全局记住|请记住"), "").trim()
                if (rule.isNotBlank()) appendGlobal(rule)
            }
            s.contains("记住") || s.contains("以后都") || s.contains("我习惯") ||
                s.contains("不要再") || s.contains("我喜欢") || s.contains("我偏好") -> {
                appendDaily("用户偏好", s.take(400), kind = "pref")
            }
        }
    }
}

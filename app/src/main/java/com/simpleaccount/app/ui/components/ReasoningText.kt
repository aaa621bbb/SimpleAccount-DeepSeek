package com.simpleaccount.app.ui.components

/** 写入消息正文的思考链围栏；展示时拆开，发给模型时剥掉。 */
const val COT_START = "<!--SA_COT-->"
const val COT_END = "<!--/SA_COT-->"

/** 耗时元数据围栏：展示在「思考过程」旁，不进回答正文。 */
const val ELAPSED_START = "<!--SA_ELAPSED-->"
const val ELAPSED_END = "<!--/SA_ELAPSED-->"

fun packCot(reply: String, cot: String?, elapsedLabel: String? = null): String {
    val c = coalesceReasoning(cot.orEmpty()).trim()
    val e = elapsedLabel?.trim().orEmpty()
    val head = buildString {
        if (c.isNotEmpty()) append("$COT_START\n${c.take(4000)}\n$COT_END\n")
        if (e.isNotEmpty()) append("$ELAPSED_START$e$ELAPSED_END\n")
    }
    return head + reply
}

/**
 * @return Triple(reply, cot, elapsedLabel)
 */
fun unpackCot(content: String): Triple<String, String?, String?> {
    var rest = content
    var cot: String? = null
    var elapsed: String? = null
    val i = rest.indexOf(COT_START)
    val j = rest.indexOf(COT_END)
    if (i >= 0 && j > i) {
        cot = rest.substring(i + COT_START.length, j).trim().ifBlank { null }
        rest = (rest.take(i) + rest.substring(j + COT_END.length)).trim()
    }
    val ei = rest.indexOf(ELAPSED_START)
    val ej = rest.indexOf(ELAPSED_END)
    if (ei >= 0 && ej > ei) {
        elapsed = rest.substring(ei + ELAPSED_START.length, ej).trim().ifBlank { null }
        rest = (rest.take(ei) + rest.substring(ej + ELAPSED_END.length)).trim()
    }
    // 兼容旧版：回答末尾「—— 总耗时 …」
    val legacy = Regex("""\n*—+\s*总耗时\s+([^\n]+)""").find(rest)
    if (legacy != null && elapsed == null) {
        elapsed = "总耗时 " + legacy.groupValues[1].trim()
        rest = rest.removeRange(legacy.range).trim()
    }
    return Triple(rest, cot, elapsed)
}

/**
 * 把模型推理链里「一字一行 / 一词一断」拼回可读段落。
 * 连续 CJK 碎片直接拼接；空行才分段。
 */
fun coalesceReasoning(src: String): String {
    if (src.isEmpty()) return src
    val lines = src.replace("\r\n", "\n").split('\n')
    val sb = StringBuilder(src.length)
    var pendingBreak = false
    for (raw in lines) {
        val t = raw.trim()
        if (t.isEmpty()) {
            pendingBreak = sb.isNotEmpty()
            continue
        }
        if (sb.isEmpty()) {
            sb.append(t)
            pendingBreak = false
            continue
        }
        if (pendingBreak && t.length > 8) {
            sb.append('\n')
            sb.append(t)
            pendingBreak = false
            continue
        }
        pendingBreak = false
        val last = sb.last()
        val first = t.first()
        val cjkJoin = isCjkChar(last) && isCjkChar(first)
        val shortFrag = t.length <= 4 || (t.length <= 8 && isCjkChar(first))
        if (cjkJoin || shortFrag) {
            if (!cjkJoin && !last.isWhitespace() && first.isLetterOrDigit()) sb.append(' ')
            sb.append(t)
        } else {
            if (!last.isWhitespace()) sb.append('\n')
            sb.append(t)
        }
    }
    return sb.toString().trim()
}

private fun isCjkChar(c: Char): Boolean =
    c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf' || c in '\u3000'..'\u303f'

package com.simpleaccount.app.ui.components

/** 写入消息正文的思考链围栏；展示时拆开，发给模型时剥掉。 */
const val COT_START = "<!--SA_COT-->"
const val COT_END = "<!--/SA_COT-->"

fun packCot(reply: String, cot: String?): String {
    val c = coalesceReasoning(cot.orEmpty()).trim()
    if (c.isEmpty()) return reply
    return "$COT_START\n${c.take(4000)}\n$COT_END\n$reply"
}

fun unpackCot(content: String): Pair<String, String?> {
    val i = content.indexOf(COT_START)
    val j = content.indexOf(COT_END)
    if (i < 0 || j < i) return content to null
    val cot = content.substring(i + COT_START.length, j).trim()
    val reply = (content.take(i) + content.substring(j + COT_END.length)).trim()
    return reply to cot.ifBlank { null }
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

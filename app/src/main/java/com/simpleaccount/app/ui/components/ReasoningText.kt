package com.simpleaccount.app.ui.components

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

package com.simpleaccount.app.data.importdata

/**
 * 健壮的 CSV 解析器：支持带引号字段、内嵌逗号/换行/引号。
 * RFC 4180 风格。
 */
object CsvParser {

    /**
     * 把 csv 文本解析为行矩阵。
     * @param text 原始文本
     * @return 每行为一个字段列表（已去除 BOM、去掉引号包裹）
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length

        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < n && text[i + 1] == '"') {
                            cur.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cur.append(c)
                    }
                    i++
                }
                c == '"' -> {
                    inQuotes = true
                    i++
                }
                c == ',' -> {
                    row.add(cur.toString())
                    cur.setLength(0)
                    i++
                }
                c == '\n' || c == '\r' -> {
                    // 处理 \r\n
                    if (c == '\r' && i + 1 < n && text[i + 1] == '\n') {
                        i++
                    }
                    row.add(cur.toString())
                    cur.setLength(0)
                    rows.add(row)
                    row = mutableListOf()
                    i++
                }
                else -> {
                    cur.append(c)
                    i++
                }
            }
        }
        // 最后一行（可能无换行结尾）
        if (cur.isNotEmpty() || row.isNotEmpty()) {
            row.add(cur.toString())
            rows.add(row)
        }
        // 移除 BOM
        if (rows.isNotEmpty() && rows[0].isNotEmpty()) {
            val firstRow = rows[0].toMutableList()
            firstRow[0] = firstRow[0].removePrefix("\uFEFF")
            rows[0] = firstRow
        }
        return rows
    }
}

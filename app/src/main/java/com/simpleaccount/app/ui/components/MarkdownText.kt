package com.simpleaccount.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染（无三方依赖），用于 AI 回复：
 * 支持 #/##/### 标题、**加粗**、`代码`、- 列表、1. 列表、> 引用、--- 分隔线、| 表格 |。
 * 让回复像 DeepSeek/豆包一样有层级和表格，而不是满屏 ** 和 |。
 */

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val items: List<String>) : MdBlock()
    data class Ordered(val items: List<String>) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    object Hr : MdBlock()
}

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val t = line.trim()
        when {
            t.isEmpty() -> i++

            t.startsWith("|") -> {
                // 表格：连续的 | 行；第二行若是 |---| 分隔则作表头
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                val parsed = tableLines.map { l ->
                    l.trim('|').split('|').map { it.trim() }
                }
                val hasSep = parsed.size >= 2 && parsed[1].all { it.replace("-", "").replace(":", "").isBlank() }
                if (hasSep) {
                    blocks.add(MdBlock.Table(parsed.first(), parsed.drop(2)))
                } else {
                    blocks.add(MdBlock.Table(parsed.first(), parsed.drop(1)))
                }
            }

            t.startsWith("#") -> {
                val level = t.takeWhile { it == '#' }.length.coerceAtMost(4)
                blocks.add(MdBlock.Heading(level, t.dropWhile { it == '#' }.trim()))
                i++
            }

            t == "---" || t == "***" -> {
                blocks.add(MdBlock.Hr)
                i++
            }

            t.startsWith("- ") || t.startsWith("· ") || t.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val lt = lines[i].trim()
                    if (lt.startsWith("- ") || lt.startsWith("· ") || lt.startsWith("* ")) {
                        items.add(lt.drop(2).trim())
                        i++
                    } else break
                }
                blocks.add(MdBlock.Bullet(items))
            }

            Regex("^\\d+[.、)] ").containsMatchIn(t) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val lt = lines[i].trim()
                    if (Regex("^\\d+[.、)] ").containsMatchIn(lt)) {
                        items.add(lt.replace(Regex("^\\d+[.、)] "), "").trim())
                        i++
                    } else break
                }
                blocks.add(MdBlock.Ordered(items))
            }

            t.startsWith("> ") -> {
                blocks.add(MdBlock.Quote(t.drop(2).trim()))
                i++
            }

            else -> {
                // 段落：合并后续非空非格式行
                val sb = StringBuilder(t)
                i++
                while (i < lines.size) {
                    val nt = lines[i].trim()
                    if (nt.isEmpty() || nt.startsWith("|") || nt.startsWith("#") ||
                        nt.startsWith("- ") || nt.startsWith("> ") || nt == "---" ||
                        Regex("^\\d+[.、)] ").containsMatchIn(nt)
                    ) break
                    sb.append(' ').append(nt)
                    i++
                }
                blocks.add(MdBlock.Paragraph(sb.toString()))
            }
        }
    }
    return blocks
}

/** 行内样式：**加粗** 与 `代码` */
private fun inlineStyle(text: String, baseColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    return buildAnnotatedString {
        // 先按代码段切
        val codeParts = text.split("`")
        codeParts.forEachIndexed { ci, codePart ->
            if (ci % 2 == 1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.08f))) {
                    append(codePart)
                }
            } else {
                // 再按 ** 切加粗
                val boldParts = codePart.split("**")
                boldParts.forEachIndexed { bi, boldPart ->
                    if (bi % 2 == 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(boldPart) }
                    } else {
                        append(boldPart)
                    }
                }
            }
        }
    }
}

@Composable
private fun MdTable(header: List<String>, rows: List<List<String>>, baseColor: androidx.compose.ui.graphics.Color) {
    val line = baseColor.copy(alpha = 0.18f)
    val cols = header.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, line, RoundedCornerShape(10.dp))
    ) {
        MdTableRow(header, cols, isHeader = true, zebra = false, line = line, baseColor = baseColor)
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        rows.forEachIndexed { ri, row ->
            if (ri > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(line))
            MdTableRow(row, cols, isHeader = false, zebra = ri % 2 == 1, line = line, baseColor = baseColor)
        }
    }
}

@Composable
private fun MdTableRow(
    cells: List<String>,
    cols: Int,
    isHeader: Boolean,
    zebra: Boolean,
    line: androidx.compose.ui.graphics.Color,
    baseColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                when {
                    isHeader -> baseColor.copy(alpha = 0.10f)
                    zebra -> baseColor.copy(alpha = 0.04f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
    ) {
        for (c in 0 until cols) {
            if (c > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(line))
            }
            Text(
                inlineStyle(cells.getOrElse(c) { "" }, baseColor),
                fontSize = 12.5.sp,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                color = baseColor,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, baseColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = parseMarkdown(text)
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (idx > 0) Spacer(Modifier.height(6.dp))
                    Text(
                        inlineStyle(block.text, baseColor),
                        fontSize = when (block.level) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 14.5.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = baseColor,
                        lineHeight = when (block.level) {
                            1 -> 26.sp; 2 -> 23.sp; else -> 21.sp
                        }
                    )
                    Spacer(Modifier.height(3.dp))
                }

                is MdBlock.Paragraph -> {
                    if (idx > 0) Spacer(Modifier.height(4.dp))
                    Text(
                        inlineStyle(block.text, baseColor),
                        fontSize = 14.sp,
                        color = baseColor,
                        lineHeight = 22.sp
                    )
                }

                is MdBlock.Bullet -> block.items.forEach { item ->
                    Row(Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp)) {
                        Text("•  ", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(inlineStyle(item, baseColor), fontSize = 14.sp, color = baseColor, lineHeight = 21.sp)
                    }
                }

                is MdBlock.Ordered -> block.items.forEachIndexed { oi, item ->
                    Row(Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp)) {
                        Text("${oi + 1}. ", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(inlineStyle(item, baseColor), fontSize = 14.sp, color = baseColor, lineHeight = 21.sp)
                    }
                }

                is MdBlock.Quote -> {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Spacer(Modifier.width(3.dp).height(0.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(baseColor.copy(alpha = 0.06f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                inlineStyle(block.text, baseColor),
                                fontSize = 13.sp,
                                color = baseColor.copy(alpha = 0.85f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                is MdBlock.Hr -> {
                    Spacer(Modifier.height(5.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(baseColor.copy(alpha = 0.12f))
                    ) {}
                    Spacer(Modifier.height(5.dp))
                }

                is MdBlock.Table -> {
                    Spacer(Modifier.height(4.dp))
                    MdTable(block.header, block.rows, baseColor)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

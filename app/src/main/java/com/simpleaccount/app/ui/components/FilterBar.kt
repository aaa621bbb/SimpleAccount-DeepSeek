package com.simpleaccount.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Category

@Composable
fun SegmentedThree(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected == i) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected == i) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FilterChipButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("选择分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    CatCell(null, "全部", selected == null) { onPick(null) }
                }
                items(categories, key = { it.id }) { c ->
                    CatCell(c, c.name, selected == c.name) { onPick(c.name) }
                }
            }
        }
    }
}

@Composable
private fun CatCell(cat: Category?, label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryIconCircle(cat, size = 36)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}


/** 多选月份：点选即时回调；全量直显，无半屏半遮罩。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiMonthPickerSheet(
    months: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "月份（点选即时生效）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onConfirm(emptySet()) }) { Text("全部") }
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Text(
                "点选即筛选，无需再点确定。多选取并集。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            months.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val sel = m in selected
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    val next = if (sel) selected - m else selected + m
                                    onConfirm(next)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                m,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 多选分类：全量直显列表（一级 + 可展开二级），点选即时生效。
 * skipPartiallyExpanded，避免半屏遮罩与上拉乱跳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiCategoryPickerSheet(
    categories: List<Category>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    subByParent: Map<String, List<String>> = emptyMap(),
) {
    var openParents by remember { mutableStateOf(setOf<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "分类筛选",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onConfirm(emptySet()) }) { Text("全部") }
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Text(
                "点选即时生效。一级整类；右侧展开二级（键为 一级/二级）。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.Column(
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState())
            ) {
                categories.forEach { c ->
                    val parentSel = c.name in selected
                    val childKeys = subByParent[c.name].orEmpty().map { "${c.name}/$it" }
                    val anyChild = childKeys.any { it in selected }
                    val open = c.name in openParents
                    val hasSub = subByParent[c.name].orEmpty().isNotEmpty()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    parentSel -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    anyChild -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                            .clickable {
                                val next = if (parentSel) {
                                    selected - c.name - childKeys.toSet()
                                } else {
                                    (selected - childKeys.toSet()) + c.name
                                }
                                onConfirm(next)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseColorSafe(c.colorHex)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                com.simpleaccount.app.util.IconMapper.map(c.iconName.ifBlank { "more_horiz" }),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            c.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (parentSel || anyChild) FontWeight.Bold else FontWeight.Medium,
                        )
                        if (parentSel) {
                            Text("整类", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (hasSub) {
                            Text(
                                if (open) "收起" else "二级",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        openParents =
                                            if (open) openParents - c.name else openParents + c.name
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (open && hasSub) {
                        subByParent[c.name].orEmpty().forEach { sub ->
                            val key = "${c.name}/$sub"
                            val sel = key in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (sel) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable {
                                        val base = selected - c.name
                                        val next = if (sel) base - key else base + key
                                        onConfirm(next)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    sub,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun parseColorSafe(hex: String): androidx.compose.ui.graphics.Color {
    return runCatching {
        val h = hex.removePrefix("#")
        val v = h.toLong(16)
        val argb = if (h.length <= 6) (0xFF000000 or v) else v
        androidx.compose.ui.graphics.Color(argb.toInt())
    }.getOrElse { androidx.compose.ui.graphics.Color(0xFF7A9AE3) }
}

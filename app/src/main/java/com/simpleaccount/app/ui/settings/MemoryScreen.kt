package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.memory.MemoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    val store: MemoryStore,
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    navController: NavHostController,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    var days by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var global by remember { mutableStateOf("") }
    var soul by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showGlobal by remember { mutableStateOf(false) }
    var showSoul by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        days = viewModel.store.listDaily()
        global = viewModel.store.globalFile().takeIf { it.exists() }?.readText().orEmpty()
        soul = viewModel.store.soulFile().takeIf { it.exists() }?.readText().orEmpty()
    }

    Scaffold(topBar = { SettingsSubToolbar("管家记忆", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("按天分开，不是一团黑箱。每天一个文件。点任意一天可展开逐条查看；全局规则与人格单独保存。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard("每日", "${days.size} 天", Icons.Filled.CalendarMonth, Modifier.weight(1f)) { /* no-op */ }
                    StatCard("全局规则", if (global.lines().count { it.trim().startsWith("-") } > 0) "${global.lines().count { it.trim().startsWith("-") }} 条" else "空", Icons.Filled.AutoAwesome, Modifier.weight(1f)) { showGlobal = true }
                    StatCard("人格", "SOUL", Icons.Filled.Description, Modifier.weight(1f)) { showSoul = true }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 全局规则卡（可点）
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().clickable { showGlobal = true }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(end = 6.dp))
                            Text("全局规则", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.weight(1f))
                            Text("查看 >", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.height(6.dp))
                        val preview = global.lines().filter { it.trim().startsWith("-") }.take(3).joinToString("\n").ifBlank { "还没有全局规则。跟管家说「记住这个（全局）…」才会写入。" }
                        Text(preview, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, lineHeight = 16.sp, maxLines = 4)
                    }
                }
            }

            // 每日日志头
            item {
                Text("每日日志 · 点一条看当天全部", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Text("按日期倒序，已按日聚合。每条记忆可点开，下钻到逐条可读。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (days.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("还没有日记。跟管家说「记住…」就会写到今天。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(days, key = { it.first }) { (date, body) ->
                    val entries = parseDayEntries(body)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedDay = date to body }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(date, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("${entries.size} 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(Modifier.weight(1f))
                                Text("查看 >", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(6.dp))
                            if (entries.isEmpty()) {
                                Text(body.take(120).ifBlank { "（空）" }, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                            } else {
                                entries.take(2).forEach { e ->
                                    Row(Modifier.padding(vertical = 2.dp)) {
                                        Text("• ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(e.title + " — " + e.body.take(50), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (entries.size > 2) {
                                    Text("… 还有 ${entries.size - 2} 条，点开看全部", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("点此卡片展开", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // 当天详情 BottomSheet
    selectedDay?.let { (date, body) ->
        ModalBottomSheet(onDismissRequest = { selectedDay = null }, sheetState = sheetState) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
            ) {
                Text(date, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Text("该日共 ${parseDayEntries(body).size} 条记忆，逐条可读。可复制或长按分享。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                val entries = parseDayEntries(body)
                if (entries.isEmpty()) {
                    Text(body.ifBlank { "（空）" }, fontSize = 13.sp, lineHeight = 20.sp)
                } else {
                    entries.forEach { e ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(e.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(e.kind, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(e.body, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("文件：$date.md", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showGlobal) {
        ModalBottomSheet(onDismissRequest = { showGlobal = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("全局规则 (GLOBAL.md)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("用户明确说「记住这个（全局）」才会写入。可复用规则极简、去重。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(global.ifBlank { "（空）" }, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
    if (showSoul) {
        ModalBottomSheet(onDismissRequest = { showSoul = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("人格 (SOUL.md)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("智能会计管家的人格设定，应用维护，用户可改。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(soul.ifBlank { "（空）" }, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

private data class DayEntry(val time: String, val title: String, val body: String, val kind: String)

private fun parseDayEntries(body: String): List<DayEntry> {
    if (body.isBlank()) return emptyList()
    // 按 "## " 分割
    val parts = body.split(Regex("\n## ")).drop(1)
    if (parts.isEmpty()) {
        // 兼容旧格式或单条
        val lines = body.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        return listOf(DayEntry("--:--", lines.first().take(20), body, "note"))
    }
    return parts.mapNotNull { part ->
        val lines = part.lines()
        val header = lines.firstOrNull() ?: ""
        // header 形如 "14:32 标题"
        val time = Regex("(\\d{2}:\\d{2})").find(header)?.groupValues?.get(1) ?: "--:--"
        val title = header.replace(Regex("\\d{2}:\\d{2}\\s*"), "").trim().ifBlank { "无标题" }
        val kind = Regex("<!--\\s*(.*?)\\s*-->").find(part)?.groupValues?.get(1)?.split("·")?.lastOrNull()?.trim() ?: "note"
        val bodyText = lines.drop(1).joinToString("\n").trim()
        if (bodyText.isBlank() && title.isBlank()) null else DayEntry(time, title, bodyText.ifBlank { header }, kind)
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

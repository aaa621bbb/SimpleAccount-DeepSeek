package com.simpleaccount.app.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.settings.SettingsSubToolbar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    navController: NavHostController,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current
    var detailLine by remember { mutableStateOf<ParsedLog?>(null) }

    Scaffold(
        topBar = { SettingsSubToolbar("日志与排障", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 顶部统计 + 操作
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("运行日志", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "文件 ${state.fileCount} 个 · ${state.totalSizeText} · 缓冲 ${state.lines.size} 条",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "结构化分栏：时间戳 + 级别 + 正文，可按级别/关键词筛选，一键导出分享。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { viewModel.share(ctx) }) { Text("分享全部", fontSize = 12.sp) }
                            OutlinedButton(onClick = { viewModel.exportFiltered(ctx) }) { Text("导出筛选", fontSize = 12.sp) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.refresh() }, modifier = Modifier.weight(1f)) { Text("刷新", fontSize = 12.sp) }
                        OutlinedButton(onClick = { viewModel.clearBuffer() }, modifier = Modifier.weight(1f)) { Text("清空缓冲", fontSize = 12.sp) }
                    }
                }
            }

            // 级别筛选 + 搜索
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val levels = listOf(null to "全部", 'D' to "DEBUG", 'I' to "INFO", 'W' to "WARN", 'E' to "ERROR")
                levels.forEach { (lvl, label) ->
                    val selected = state.levelFilter == lvl
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setLevelFilter(lvl) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (lvl) {
                                'E' -> MaterialTheme.colorScheme.errorContainer
                                'W' -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text("${state.filteredContent.size} 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("关键词筛选（级别/内容）", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // 分栏 Tabs
            TabRow(selectedTabIndex = state.tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = state.tab == 0, onClick = { viewModel.setTab(0) }, text = { Text("实时缓冲", fontSize = 13.sp) })
                Tab(selected = state.tab == 1, onClick = { viewModel.setTab(1) }, text = { Text("历史文件", fontSize = 13.sp) })
            }
            HorizontalDivider()

            if (state.tab == 0) {
                // 实时缓冲的筛选列表
                if (state.filteredContent.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.lines.isEmpty()) "暂无日志。请先进行一些操作（导入账单、AI对话等），再回来查看。"
                            else "无匹配日志，换个级别或关键词试试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.filteredContent, key = { it.raw.hashCode().toString() + it.time }) { log ->
                            LogRow(log, onClick = { detailLine = log })
                            HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            } else {
                // 历史文件列表或文件内容
                if (state.selectedFile == null) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                "历史日志文件（按修改时间倒序）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (state.files.isEmpty()) {
                            item {
                                Text(
                                    "（暂无历史日志文件）",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        } else {
                            items(state.files) { file ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.openFile(file) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${com.simpleaccount.app.util.AppLog.formatSize(file.length())} · ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text("查看", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "提示：点文件查看其内容，同样支持级别/关键词筛选与导出。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    // 选中文件的内容（已筛选）
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                state.selectedFile?.name ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${state.filteredContent.size} 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.closeFile() }) { Text("返回", fontSize = 12.sp) }
                        }
                        HorizontalDivider()
                        if (state.filteredContent.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("该文件无匹配日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(state.filteredContent) { log ->
                                    LogRow(log, onClick = { detailLine = log })
                                    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailLine?.let { log ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detailLine = null },
            title = { Text("日志详情", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(log.level)
                        Spacer(Modifier.width(8.dp))
                        Text(log.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(log.message, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text("原始：${log.raw}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { detailLine = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun LogRow(log: ParsedLog, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        LevelBadge(log.level)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    log.time.ifBlank { "--:--" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    log.message.take(120),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Char) {
    val (bg, fg, label) = when (level) {
        'D' -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "D")
        'I' -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "I")
        'W' -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "W")
        'E' -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "E")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, level.toString())
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

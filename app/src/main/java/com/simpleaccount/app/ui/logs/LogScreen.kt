package com.simpleaccount.app.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.settings.SettingsSubToolbar

private data class LogRow(val level: Char, val time: String, val message: String, val raw: String)

private fun parseLog(line: String): LogRow {
    val m = Regex("^\\[([DIWE])]\\s+(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+(.*)$").find(line)
    return if (m != null) LogRow(m.groupValues[1][0], m.groupValues[2], m.groupValues[3], line)
    else LogRow('I', "", line, line)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    navController: NavHostController,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var level by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = { SettingsSubToolbar("日志与排障", onBack = { navController.popBackStack() }) }
    ) { padding ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "文件 ${state.fileCount} · ${state.totalSizeText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { viewModel.refresh() }) { Text("刷新") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.share(ctx) }) { Text("导出") }
            }
            Text(
                "时间戳精确到毫秒。按级别筛选后导出当前缓冲，发给开发即可对上一次操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ALL", "D", "I", "W", "E").forEach { lv ->
                    FilterChip(
                        selected = level == lv,
                        onClick = { level = lv },
                        label = { Text(if (lv == "ALL") "全部" else lv) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("筛选关键字") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            HorizontalDivider()

            if (state.selectedFile == null) {
                val rows = remember(state.lines, level, query) {
                    state.lines.map(::parseLog)
                        .filter { level == "ALL" || it.level.toString() == level }
                        .filter { query.isBlank() || it.raw.contains(query, ignoreCase = true) }
                        .asReversed()
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "历史文件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    if (state.files.isEmpty()) {
                        item {
                            Text("（暂无文件）", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
                        }
                    } else {
                        items(state.files) { file ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openFile(file) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    file.name + " · " + com.simpleaccount.app.util.AppLog.formatSize(file.length()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("打开", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        Text(
                            "内存缓冲 · ${rows.size} 条",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(rows.size) { i ->
                        val row = rows[i]
                        LogLine(row)
                    }
                    if (state.lines.isEmpty()) {
                        item {
                            Text(
                                "暂无日志。导入账单或问管家后再回来。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.selectedFile?.name ?: "", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.closeFile() }) { Text("返回") }
                    }
                    val fileRows = remember(state.fileContent, level, query) {
                        state.fileContent.lineSequence().map(::parseLog)
                            .filter { level == "ALL" || it.level.toString() == level }
                            .filter { query.isBlank() || it.raw.contains(query, ignoreCase = true) }
                            .toList()
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(fileRows.size) { i -> LogLine(fileRows[i]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(row: LogRow) {
    val color = when (row.level) {
        'E' -> MaterialTheme.colorScheme.error
        'W' -> Color(0xFFC47A22)
        'D' -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            row.level.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            if (row.time.isNotBlank()) {
                Text(row.time, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
            Text(row.message, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
        }
    }
}

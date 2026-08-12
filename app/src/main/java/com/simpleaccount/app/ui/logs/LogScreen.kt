package com.simpleaccount.app.ui.logs

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.settings.SettingsSubToolbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    navController: NavHostController,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { SettingsSubToolbar("日志与排障", onBack = { navController.popBackStack() }) }
    ) { padding ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 操作栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "日志文件 ${state.fileCount} 个 · ${state.totalSizeKb}KB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { viewModel.refresh() }) {
                    Text("刷新")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.share(ctx) }) {
                    Text("分享")
                }
            }

            Text(
                "说明：日志记录在 App 私有目录，无需 USB 调试即可查看。出现问题时点「分享」把日志发给我即可排查。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider()

            if (state.selectedFile == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    // 历史日志文件列表（点击可查看完整内容）
                    item {
                        Text(
                            "历史日志文件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
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
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    file.name + " · " + (file.length() / 1024) + "KB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "查看",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "最近日志（内存缓冲）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(state.lines.takeLast(300).reversed()) { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                    if (state.lines.isEmpty()) {
                        item {
                            Text(
                                "暂无日志。请先进行一些操作（导入账单、AI对话等），再回来查看。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            } else {
                // 选中了某个日志文件 —— 展示其内容
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.selectedFile?.name ?: "",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.closeFile() }) {
                            Text("返回")
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.fileContent.lineSequence().toList()) { line ->
                            Text(
                                line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

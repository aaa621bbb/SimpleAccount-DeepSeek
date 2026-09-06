package com.simpleaccount.app.ui.importbill

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    navController: NavHostController,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFrom(uri)
        }
    }

    var showFailures by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单导入") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state.phase) {
                ImportPhase.IDLE -> {
                    Text("支持导入微信/支付宝账单", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "格式：xlsx / csv / zip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            openLauncher.launch(arrayOf("application/zip", "text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("选择账单文件")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "支持：微信支付、支付宝；\n自动识别表头与收支、自动分类、自动去重。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                ImportPhase.PARSING, ImportPhase.IMPORTING -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(if (state.phase == ImportPhase.PARSING) "解析中…" else "导入中…")
                    Spacer(Modifier.height(8.dp))
                    Text(state.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                ImportPhase.DONE -> {
                    Text("导入完成", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "成功导入 ${state.inserted} 条", fontSize = 16.sp
                    )
                    if (state.skipped > 0) {
                        Text(
                            "跳过 ${state.skipped} 条", fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 跳过原因构成（解释"为什么跳过"）
                        if (state.skipReasons.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                state.skipReasons.entries
                                    .sortedByDescending { it.value }
                                    .forEach { (reason, count) ->
                                        Text(
                                            "· $reason：$count 条",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                            }
                        }
                    }
                    // R1：失败明细入口
                    if (state.failed > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "失败 ${state.failed} 条", fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        androidx.compose.material3.TextButton(onClick = { showFailures = !showFailures }) {
                            Text(if (showFailures) "收起失败明细 ▲" else "查看失败明细 ▼")
                        }
                        if (showFailures) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                state.failures.forEachIndexed { idx, f ->
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(
                                                "第 ${idx + 1} 条：${f.content}",
                                                fontSize = 12.sp,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "原因：${f.reason}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                if (state.failures.isEmpty()) {
                                    Text(
                                        "（本次失败明细为空）",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("返回")
                    }
                }

                ImportPhase.ERROR -> {
                    Text("导入失败", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text(state.error ?: "未知错误")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.reset() }) { Text("重试") }
                }
            }
        }
    }
}

package com.simpleaccount.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun DataManageScreen(
    navController: NavHostController,
    viewModel: DataManageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDoneMessage by remember { mutableStateOf<String?>(null) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showCountDialog by remember { mutableStateOf(false) }

    val importPriority by viewModel.importPriorityFlow.collectAsState()
    val recentCount by viewModel.recentCountFlow.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.buildExportJson()
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray())
                        os.flush()
                    }
                    showDoneMessage = "导出成功"
                } catch (e: IOException) {
                    showDoneMessage = "导出失败：${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SettingsSubToolbar("数据管理", onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 数据冲突优先级
            ListItem(
                headlineContent = { Text("数据冲突优先级") },
                supportingContent = { Text("导入账单与 手动/截图/AI 记录 是同一笔时，保留哪边") },
                leadingContent = { Icon(Icons.Filled.CompareArrows, contentDescription = null) },
                trailingContent = {
                    Text(
                        if (importPriority == "manual") "手动·截图·AI为准" else "导入账单为准",
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { showPriorityDialog = true }
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            // 首页最近记录条数
            ListItem(
                headlineContent = { Text("首页最近记录条数") },
                leadingContent = { Icon(Icons.Filled.FormatListNumbered, contentDescription = null) },
                trailingContent = {
                    Text("$recentCount 条", color = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { showCountDialog = true }
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ListItem(
                headlineContent = { Text("导出备份（JSON）") },
                leadingContent = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { exportLauncher.launch(viewModel.defaultFileName()) }
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ListItem(
                headlineContent = { Text("一键清理存储") },
                supportingContent = { Text("清理导入日志、失败记录、孤立商家、旧消息上限，并整理数据库（VACUUM）") },
                leadingContent = { Icon(Icons.Filled.CleaningServices, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable {
                    if (!state.busy) {
                        scope.launch {
                            val summary = viewModel.cleanupStorage()
                            showDoneMessage = summary
                        }
                    }
                }
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ListItem(
                headlineContent = { Text("清空所有数据") },
                supportingContent = { Text("清空流水、分类、商家、导入记录及 AI 设置（含 API Key）") },
                leadingContent = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { showClearConfirm = true }
            )
        }
    }

    if (showPriorityDialog) {
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text("数据冲突优先级") },
            text = { Text("当导入的账单与 手动记/截图记/AI记 的某一笔是同一笔（同日同商家同金额）时，保留哪一边？\n\n· 导入账单为准：账单信息更全（商家/单号/时间），覆盖手记的那笔\n· 手动·截图·AI为准：导入时跳过该笔，保留你手记的内容") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setImportPriority("import"); showPriorityDialog = false
                }) { Text("导入账单为准") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setImportPriority("manual"); showPriorityDialog = false
                }) { Text("手动·截图·AI为准") }
            }
        )
    }

    if (showCountDialog) {
        var countText by remember { mutableStateOf(recentCount.toString()) }
        AlertDialog(
            onDismissRequest = { showCountDialog = false },
            title = { Text("首页最近记录条数") },
            text = {
                Column {
                    Text("5 - 100 条", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = countText.toIntOrNull()?.coerceIn(5, 100) ?: 20
                    viewModel.setRecentCount(n)
                    showCountDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showCountDialog = false }) { Text("取消") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空所有数据") },
            text = { Text("确定清空所有数据吗？此操作不可恢复。将清空流水、分类、商家、导入记录及所有设置（含 AI API Key）。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        viewModel.clearAllData()
                        showDoneMessage = "已清空所有数据"
                    }
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    showDoneMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { showDoneMessage = null },
            title = { Text("完成") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { showDoneMessage = null }) { Text("好") }
            }
        )
    }
}

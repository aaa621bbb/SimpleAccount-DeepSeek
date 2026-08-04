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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileUpload
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
            ListItem(
                headlineContent = { Text("导出备份（JSON）") },
                leadingContent = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { exportLauncher.launch(viewModel.defaultFileName()) }
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

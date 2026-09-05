package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsEntry(val title: String, val icon: ImageVector, val route: String)

private val entries = listOf(
    SettingsEntry("AI 辅助设置", Icons.Filled.SmartToy, Routes.AI_SETTINGS),
    SettingsEntry("账本管理", Icons.Filled.Storage, Routes.LEDGER_MANAGE),
    SettingsEntry("无感记账（自动记账）", Icons.Filled.NotificationsActive, Routes.AUTO_RECORD),
    SettingsEntry("分类管理", Icons.Filled.Category, Routes.CATEGORY_MANAGE),
    SettingsEntry("商家归类管理", Icons.Filled.Store, Routes.MERCHANT_MANAGE),
    SettingsEntry("数据管理（导出/清空）", Icons.Filled.Storage, Routes.DATA_MANAGE),
    SettingsEntry("日志与排障", Icons.Filled.BugReport, Routes.LOGS),
    SettingsEntry("关于", Icons.Filled.Info, Routes.ABOUT),
)

/** 外观模式的显示名 */
fun themeModeLabel(mode: String): String = when (mode) {
    SettingsRepository.THEME_LIGHT -> "浅色"
    SettingsRepository.THEME_DARK -> "深色"
    else -> "跟随系统"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _themeMode = MutableStateFlow(settingsRepository.themeMode())
    val themeMode = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            // 外观（深色/浅色/跟随系统），点开弹窗选择
            item {
                ListItem(
                    headlineContent = { Text("外观") },
                    supportingContent = { Text(themeModeLabel(themeMode)) },
                    leadingContent = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                    trailingContent = { Text(themeModeLabel(themeMode), color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
            items(entries.size) { i ->
                val e = entries[i]
                ListItem(
                    headlineContent = { Text(e.title) },
                    leadingContent = { Icon(e.icon, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    },
                    modifier = Modifier.clickable { navController.navigate(e.route) }
                )
                if (i < entries.size - 1) HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("外观") },
            text = {
                Column {
                    listOf(
                        SettingsRepository.THEME_SYSTEM,
                        SettingsRepository.THEME_LIGHT,
                        SettingsRepository.THEME_DARK,
                    ).forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(mode); showThemeDialog = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode); showThemeDialog = false }
                            )
                            Text(themeModeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 通用子页面 Toolbar */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
fun SettingsSubToolbar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

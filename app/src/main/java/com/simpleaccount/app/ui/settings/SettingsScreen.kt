package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    SettingsEntry("外观与配色", Icons.Filled.Palette, Routes.APPEARANCE),
    SettingsEntry("统计页图表", Icons.Filled.BarChart, Routes.STATS_LAYOUT),
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

    private val _paletteId = MutableStateFlow(settingsRepository.colorPalette())
    val paletteId = _paletteId.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setPalette(id: String) {
        _paletteId.value = id
        viewModelScope.launch { settingsRepository.setColorPalette(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
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
}

/** 通用子页面 Toolbar */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubToolbar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    )
}

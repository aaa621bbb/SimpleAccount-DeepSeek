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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.navigation.Routes

data class SettingsEntry(val title: String, val icon: ImageVector, val route: String)

private val entries = listOf(
    SettingsEntry("AI 辅助设置", Icons.Filled.SmartToy, Routes.AI_SETTINGS),
    SettingsEntry("分类管理", Icons.Filled.Category, Routes.CATEGORY_MANAGE),
    SettingsEntry("商家归类管理", Icons.Filled.Store, Routes.MERCHANT_MANAGE),
    SettingsEntry("数据管理（导出/清空）", Icons.Filled.Storage, Routes.DATA_MANAGE),
    SettingsEntry("关于", Icons.Filled.Info, Routes.ABOUT),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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

package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.ui.components.CategoryIconCircle
import com.simpleaccount.app.ui.components.parseColor
import com.simpleaccount.app.util.IconMapper
import kotlinx.coroutines.launch



/** 可选颜色库 */
private val colorLibrary = listOf(
    "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FF9F43", "#A29BFE",
    "#00B894", "#FDCB6E", "#2ECC71", "#E67E22", "#6C5CE7", "#00CEC9", "#2D8CF0", "#BDC3C7"
)

@Composable
fun CategoryManageScreen(
    navController: NavHostController,
    viewModel: CategoryManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var confirmDeleteEmpty by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = { SettingsSubToolbar("分类管理", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增分类")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = if (state.type == Category.TYPE_EXPENSE) 0 else 1) {
                Tab(
                    selected = state.type == Category.TYPE_EXPENSE,
                    onClick = { viewModel.setType(Category.TYPE_EXPENSE) },
                    text = { Text("支出") }
                )
                Tab(
                    selected = state.type == Category.TYPE_INCOME,
                    onClick = { viewModel.setType(Category.TYPE_INCOME) },
                    text = { Text("收入") }
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.categories, key = { it.id }) { cat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = cat }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryIconCircle(cat, size = 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (cat.isPreset) "预置 · 点按可改图标颜色" else "点按可改名称、图标、颜色",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!cat.isPreset) {
                            IconButton(onClick = {
                                scope.launch {
                                    if (viewModel.isCategoryEmpty(cat.name)) {
                                        confirmDeleteEmpty = cat
                                    } else {
                                        pendingDelete = cat
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryEditorDialog(
            type = state.type,
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, icon, color ->
                showAddDialog = false
                scope.launch { viewModel.add(name, icon, color) }
            }
        )
    }
    editing?.let { cat ->
        CategoryEditorDialog(
            type = cat.type,
            initial = cat,
            onDismiss = { editing = null },
            onConfirm = { name, icon, color ->
                val target = cat
                editing = null
                scope.launch { viewModel.updateStyle(target, icon, color, name) }
            }
        )
    }

    // 删除二次确认（含迁移提示）
    val delTarget = pendingDelete ?: confirmDeleteEmpty
    if (delTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null; confirmDeleteEmpty = null },
            title = { Text("删除分类") },
            text = { Text("删除「${delTarget.name}」分类？${if (pendingDelete != null) "\n该分类下的记录将转移到「其它」。" else ""}") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.delete(delTarget)
                    }
                    pendingDelete = null; confirmDeleteEmpty = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null; confirmDeleteEmpty = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    type: String,
    initial: Category?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    val icons = com.simpleaccount.app.util.IconMapper.allChoices(type)
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.iconName ?: icons.firstOrNull()?.name ?: "more_horiz") }
    var color by remember { mutableStateOf(initial?.colorHex ?: "#BDC3C7") }
    val nameLocked = initial?.isPreset == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增分类" else "修改「${initial.name}」") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (!nameLocked) name = it },
                    label = { Text(if (nameLocked) "分类名称（预置不可改名）" else "分类名称") },
                    enabled = !nameLocked,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("选择图标（支出/收入各 30+，互不重复）", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
                    icons.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { ic ->
                                val selected = ic.name == icon
                                Box(
                                    Modifier
                                        .padding(4.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { icon = ic.name },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        IconMapper.map(ic.name),
                                        contentDescription = ic.label,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("选择颜色", style = MaterialTheme.typography.titleSmall)
                Row {
                    colorLibrary.forEach { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseColor(c))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) Color.Black else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon, color) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

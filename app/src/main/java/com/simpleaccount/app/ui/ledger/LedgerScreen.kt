package com.simpleaccount.app.ui.ledger

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.components.TransactionRow
import com.simpleaccount.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    navController: NavHostController,
) {
    val state by viewModel.uiState.collectAsState()
    val filter = state.filter
    var showMonthMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    // 按月份分组（yyyy-MM），保持顺序
    val grouped = remember(state.rows) {
        state.rows.groupBy { it.transaction.date.take(7) }
            .toSortedMap(compareByDescending { it })
            .toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账本") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索备注/分类/商家") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空搜索")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { showMonthMenu = true }) {
                        Text(if (filter.month != null) "月份：${filter.month}" else "月份：全部")
                    }
                    DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false }) {
                        DropdownMenuItem(text = { Text("全部") }, onClick = { viewModel.setMonth(null); showMonthMenu = false })
                        state.months.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { viewModel.setMonth(m); showMonthMenu = false })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    TextButton(onClick = { showCategoryMenu = true }) {
                        Text(if (filter.category != null) "分类：${filter.category}" else "分类：全部")
                    }
                    DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                        DropdownMenuItem(text = { Text("全部") }, onClick = { viewModel.setCategory(null); showCategoryMenu = false })
                        state.categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { viewModel.setCategory(c.name); showCategoryMenu = false })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (filter.month != null || filter.category != null || filter.query.isNotBlank()) {
                    TextButton(onClick = viewModel::clearFilters) { Text("清除筛选") }
                }
            }

            if (grouped.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "暂无记录",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (month, rows) ->
                        item(key = "h_$month") {
                            Text(
                                month,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        rows.forEach { row ->
                            item(key = row.transaction.id) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        TransactionRow(
                                            transaction = row.transaction,
                                            category = row.category,
                                            onClick = { navController.navigate(Routes.edit(row.transaction.id)) }
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = row.transaction.id }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("确定删除这条记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(pendingDelete!!); pendingDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

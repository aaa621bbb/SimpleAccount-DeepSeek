package com.simpleaccount.app.ui.ledger

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    // 分组由 ViewModel 在后台线程算好，UI 直接用（不再 remember 里现算导致切页卡顿）
    val grouped = state.groups.map { it.month to it.rows }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账本") },
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            var showMonth by remember { mutableStateOf(false) }
            var showCat by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.simpleaccount.app.ui.components.SegmentedThree(
                    options = listOf("全部", "支出", "收入"),
                    selected = when (filter.type) {
                        com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE -> 1
                        com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME -> 2
                        else -> 0
                    },
                    onSelect = {
                        viewModel.setType(
                            when (it) {
                                1 -> com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE
                                2 -> com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME
                                else -> null
                            }
                        )
                    }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                com.simpleaccount.app.ui.components.FilterChipButton(
                    label = filter.month ?: "全部月份",
                    onClick = { showMonth = true }
                )
                Spacer(Modifier.width(8.dp))
                com.simpleaccount.app.ui.components.FilterChipButton(
                    label = filter.category ?: "全部分类",
                    onClick = { showCat = true }
                )
            }
            if (showMonth) {
                com.simpleaccount.app.ui.components.MonthPickerSheet(
                    months = state.months,
                    selected = filter.month,
                    onDismiss = { showMonth = false },
                    onPick = { viewModel.setMonth(it); showMonth = false }
                )
            }
            if (showCat) {
                val cats = when (filter.type) {
                    com.simpleaccount.app.data.entity.Transaction.TYPE_EXPENSE ->
                        state.categories.filter { it.type == com.simpleaccount.app.data.entity.Category.TYPE_EXPENSE }
                    com.simpleaccount.app.data.entity.Transaction.TYPE_INCOME ->
                        state.categories.filter { it.type == com.simpleaccount.app.data.entity.Category.TYPE_INCOME }
                    else -> state.categories
                }
                com.simpleaccount.app.ui.components.CategoryPickerSheet(
                    categories = cats,
                    selected = filter.category,
                    onDismiss = { showCat = false },
                    onPick = { viewModel.setCategory(it); showCat = false }
                )
            }

            // 记录数 + 排序切换 + 清除筛选
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "共 ${state.rows.size} 条记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.toggleSort() }) {
                    Text(
                        if (state.sortByAmount) "按金额" else "按时间",
                        fontSize = 13.sp
                    )
                }
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
                // 性能关键：行必须逐条懒加载（keyed item），
                // 上一版"每月一张卡整月全渲染"在 1800+ 条时一次性组合导致严重卡顿
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    grouped.forEach { (month, rows) ->
                        item(key = "h_$month") {
                            // 月份横幅
                            Row(
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "$month · ${rows.size} 笔",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        items(rows, key = { it.transaction.id }) { row ->
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
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
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

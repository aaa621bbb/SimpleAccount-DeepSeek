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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
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
import com.simpleaccount.app.ui.components.RowUi
import com.simpleaccount.app.ui.components.TransactionRow
import com.simpleaccount.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    navController: NavHostController,
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    // 分组由 ViewModel 在后台线程算好，UI 直接用（不再 remember 里现算导致切页卡顿）
    val grouped = state.groups

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
                    .com.simpleaccount.app.ui.theme.skinControl(
                        com.simpleaccount.app.ui.theme.LocalVisualStyle.current,
                        raised = false,
                    )
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
                    label = filter.monthsLabel(),
                    onClick = { showMonth = true }
                )
                Spacer(Modifier.width(8.dp))
                com.simpleaccount.app.ui.components.FilterChipButton(
                    label = filter.categoriesLabel(),
                    onClick = { showCat = true }
                )
            }
            if (showMonth) {
                com.simpleaccount.app.ui.components.MultiMonthPickerSheet(
                    months = state.months,
                    selected = filter.months,
                    onDismiss = { showMonth = false },
                    onConfirm = { viewModel.setMonths(it) }
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
                com.simpleaccount.app.ui.components.MultiCategoryPickerSheet(
                    categories = cats,
                    selected = filter.categories,
                    onDismiss = { showCat = false },
                    onConfirm = { viewModel.setCategories(it) }
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
                if (filter.active) {
                    TextButton(onClick = viewModel::clearFilters) { Text("清除筛选") }
                }
            }

            // 按金额排序 + 未选具体月份 = 全局金额排行，不再按月分段
            // （否则每段内部才按金额排，看起来还是"月份里的排序"）
            val flatAmountView = state.sortByAmount && filter.months.isEmpty()

            if (grouped.isEmpty()) {
                com.simpleaccount.app.ui.components.EmptyState(
                    text = "暂无记录",
                    caption = if (filter.active) "月份和分类是交叉筛选。试试少选几个，或点「清除筛选」。" else "记一笔之后会出现在这里。",
                )
            } else {
                // 性能关键：行必须逐条懒加载（keyed item），
                // 上一版"每月一张卡整月全渲染"在 1800+ 条时一次性组合导致严重卡顿
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    if (flatAmountView) {
                        items(state.rows, key = { it.transaction.id }) { row ->
                            LedgerRowLine(
                                row = row,
                                onDelete = { pendingDelete = row.transaction.id },
                                onOpen = { navController.navigate(Routes.edit(row.transaction.id)) }
                            )
                        }
                    } else {
                        grouped.forEach { g ->
                            val month = g.month
                            val rows = g.rows
                            item(key = "h_$month") {
                                Row(
                                    Modifier
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        "$month · ${rows.size} 笔 · 支 ¥${com.simpleaccount.app.util.MoneyUtil.fenToYuan(g.expenseFen)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            items(rows, key = { it.transaction.id }) { row ->
                                LedgerRowLine(
                                    row = row,
                                    onDelete = { pendingDelete = row.transaction.id },
                                    onOpen = { navController.navigate(Routes.edit(row.transaction.id)) }
                                )
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

/** 账本行 + 删除按钮（金额扁平视图与月份分组视图共用） */
@Composable
private fun LedgerRowLine(
    row: RowUi,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    showDay: Boolean = false,
) {
    val t = com.simpleaccount.app.ui.theme.LocalTokens.current
    Column {
        if (showDay) {
            Text(
                row.transaction.date,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TransactionRow(
                    transaction = row.transaction,
                    category = row.category,
                    onClick = onOpen,
                    showDate = !showDay,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            thickness = t.hairline,
            modifier = Modifier.padding(start = 72.dp)
        )
    }
}

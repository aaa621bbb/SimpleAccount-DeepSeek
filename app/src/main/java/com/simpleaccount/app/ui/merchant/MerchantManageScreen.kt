package com.simpleaccount.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.ui.components.CategoryIconCircle
import com.simpleaccount.app.ui.settings.SettingsSubToolbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantManageScreen(
    navController: NavHostController,
    viewModel: MerchantManageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMerchant by remember { mutableStateOf<Merchant?>(null) }
    var catQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            SettingsSubToolbar("商家归类管理", onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 状态筛选 chips（横向滚动）
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                viewModel.allStatusOptions.forEach { (label, value) ->
                    FilterChip(
                        selected = state.statusFilter == value,
                        onClick = { viewModel.setStatus(value) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // 搜索商家
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索商家") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 列表
            if (state.merchants.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "暂无商家信息",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "候选分类已就绪：${state.categories.size} 个（内置 + 自建）",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 列表头：展示候选集统计，证明已合并
                if (state.categories.isNotEmpty()) {
                    Text(
                        "候选分类 ${state.categories.size} · 点击商家即可重归类（内置+自建已合并）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.merchants, key = { it.id }) { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedMerchant = m; catQuery = "" }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m.merchant,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (m.category.isNotEmpty()) m.category else "待归类",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (m.status == Merchant.STATUS_PENDING)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    // 分类选择 BottomSheet — 候选集 = 内置 + 自定义完整并集，分组展示
    selectedMerchant?.let { merchant ->
        ModalBottomSheet(
            onDismissRequest = { selectedMerchant = null },
            sheetState = sheetState
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "为「${merchant.merchant}」选择分类",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "已合并 ${state.categories.size} 个分类（内置 ${state.categories.count { it.isPreset }} + 自建 ${state.categories.count { !it.isPreset }}），自建分类可直接引用",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                // 分类内搜索
                OutlinedTextField(
                    value = catQuery,
                    onValueChange = { catQuery = it },
                    placeholder = { Text("在分类中搜索") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val filtered = if (catQuery.isBlank()) state.categories
                else state.categories.filter { it.name.contains(catQuery.trim(), ignoreCase = true) }

                // 按类型分组：先支出后收入，每组内按 sortOrder + isPreset优先
                val expense = filtered.filter { it.type == Category.TYPE_EXPENSE }
                    .sortedWith(compareBy({ !it.isPreset }, { it.sortOrder }, { it.name }))
                val income = filtered.filter { it.type == Category.TYPE_INCOME }
                    .sortedWith(compareBy({ !it.isPreset }, { it.sortOrder }, { it.name }))
                val other = filtered.filter { it.type !in listOf(Category.TYPE_EXPENSE, Category.TYPE_INCOME) }

                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    if (expense.isNotEmpty()) {
                        item(key = "h_expense") {
                            SectionHeader("支出", expense.size, "含自建")
                        }
                        items(expense, key = { "e_${it.id}" }) { cat ->
                            CategoryRow(cat) {
                                scope.launch { viewModel.assignCategory(merchant, cat.name) }
                                selectedMerchant = null
                            }
                        }
                    }
                    if (income.isNotEmpty()) {
                        item(key = "h_income") {
                            SectionHeader("收入", income.size, "含自建")
                        }
                        items(income, key = { "i_${it.id}" }) { cat ->
                            CategoryRow(cat) {
                                scope.launch { viewModel.assignCategory(merchant, cat.name) }
                                selectedMerchant = null
                            }
                        }
                    }
                    if (other.isNotEmpty()) {
                        item(key = "h_other") { SectionHeader("其它", other.size, "") }
                        items(other, key = { "o_${it.id}" }) { cat ->
                            CategoryRow(cat) {
                                scope.launch { viewModel.assignCategory(merchant, cat.name) }
                                selectedMerchant = null
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的分类，换个关键词试试",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, badge: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        if (badge.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(badge, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryRow(cat: Category, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconCircle(cat, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (!cat.isPreset) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text("自建", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Text(
                if (cat.isPreset) "系统预置" else "我的自定义",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (cat.type == Category.TYPE_INCOME) "收入" else "支出",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

package com.simpleaccount.app.ui.merchant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
    val sheetState = rememberModalBottomSheetState()
    var selectedMerchant by remember { mutableStateOf<Merchant?>(null) }

    Scaffold(
        topBar = {
            SettingsSubToolbar("商家归类管理", onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索商家") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.merchants.isEmpty()) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "暂无商家信息",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.merchants, key = { it.id }) { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedMerchant = m }
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

    selectedMerchant?.let { merchant ->
        ModalBottomSheet(
            onDismissRequest = { selectedMerchant = null },
            sheetState = sheetState
        ) {
            var catQuery by remember { mutableStateOf("") }
            val grouped = remember(state.categories, catQuery) {
                val q = catQuery.trim()
                val all = if (q.isEmpty()) state.categories
                else state.categories.filter { it.name.contains(q, ignoreCase = true) }
                listOf(
                    "支出 · 预置" to all.filter { it.type == Category.TYPE_EXPENSE && it.isPreset },
                    "支出 · 自建" to all.filter { it.type == Category.TYPE_EXPENSE && !it.isPreset },
                    "收入 · 预置" to all.filter { it.type == Category.TYPE_INCOME && it.isPreset },
                    "收入 · 自建" to all.filter { it.type == Category.TYPE_INCOME && !it.isPreset },
                ).filter { it.second.isNotEmpty() }
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "为「${merchant.merchant}」选择分类",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "候选 = 预置 ∪ 自建，共 ${state.categories.size} 项。自建分类会出现在「自建」分组。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = catQuery,
                    onValueChange = { catQuery = it },
                    placeholder = { Text("筛选分类名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    grouped.forEach { (title, cats) ->
                        item(key = "h_$title") {
                            Text(
                                title + "（${cats.size}）",
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(cats, key = { title + it.name }) { cat ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            viewModel.assignCategory(merchant, cat.name)
                                        }
                                        selectedMerchant = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconCircle(cat, size = 36)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                                    if (!cat.isPreset) {
                                        Text("自建", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (grouped.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的分类。到「分类管理」新建后会立刻出现在这里。",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

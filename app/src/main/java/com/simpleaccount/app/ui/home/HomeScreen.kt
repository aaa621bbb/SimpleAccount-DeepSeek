package com.simpleaccount.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.components.TransactionRow
import com.simpleaccount.app.ui.navigation.Routes
import com.simpleaccount.app.util.MoneyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navController: NavHostController,
) {
    val state by viewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部工具条（不显示 App 名，仅导入入口）
            TopAppBar(
                title = { },
                navigationIcon = { Box(Modifier.width(48.dp)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.IMPORT) }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入账单")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
            ) {
                // 三张横向卡片
                item { SummaryCards(state) }
                item {
                    Text(
                        "最近记录",
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (state.recent.isEmpty()) {
                    item {
                        Text(
                            "暂无记录",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    items(state.recent) { row ->
                        TransactionRow(
                            transaction = row.transaction,
                            category = row.category,
                            onClick = {
                                navController.navigate(Routes.edit(row.transaction.id))
                            }
                        )
                    }
                }
            }
        }

        // FAB 记一笔
        FloatingActionButton(
            onClick = { navController.navigate(Routes.ADD) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "记一笔")
        }
    }
}

@Composable
private fun SummaryCards(state: HomeUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard("支出", "¥" + MoneyUtil.fenToYuan(state.expense), Color(0xFFFF6B6B))
        SummaryCard("收入", "¥" + MoneyUtil.fenToYuan(state.income), Color(0xFF2ECC71))
        SummaryCard("结余", "¥" + MoneyUtil.fenToYuan(state.balance), Color(0xFF2D8CF0))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

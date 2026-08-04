package com.simpleaccount.app.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.components.LineTrendView
import com.simpleaccount.app.ui.components.PieChartView
import com.simpleaccount.app.ui.components.parseColor
import com.simpleaccount.app.util.MoneyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showMonthMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
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
            // 月份选择 + 收支切换（紧凑）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { showMonthMenu = true }) {
                        Text(if (state.month == "all") "全部月份" else state.month)
                    }
                    DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("全部") },
                            onClick = { viewModel.setMonth("all"); showMonthMenu = false }
                        )
                        state.months.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { viewModel.setMonth(m); showMonthMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = state.type == Transaction.TYPE_EXPENSE,
                    onClick = { viewModel.setType(Transaction.TYPE_EXPENSE) },
                    label = { Text("支出") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = state.type == Transaction.TYPE_INCOME,
                    onClick = { viewModel.setType(Transaction.TYPE_INCOME) },
                    label = { Text("收入") }
                )
            }

            // 上半：饼图（固定矮高度，不滚动）
            PieChartView(
                slices = state.slices,
                centerLabel = if (state.type == Transaction.TYPE_EXPENSE) "总支出" else "总收入",
                centerValue = "¥" + MoneyUtil.fenToYuan(state.total)
            )

            // 图例（压缩行距）
            if (state.slices.isEmpty()) {
                Text(
                    "本月暂无数据",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                Column(
                    Modifier
                        .height(150.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    state.slices.forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(10.dp).background(parseColor(s.colorHex)))
                            Spacer(Modifier.width(8.dp))
                            Text(s.categoryName, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "¥" + MoneyUtil.fenToYuan(s.value),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // 趋势折线（压缩高度）
            Text(
                "近 12 个月趋势",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            LineTrendView(state.trend)
            Row(Modifier.padding(start = 24.dp, top = 0.dp)) {
                LegendDot(Color(0xFFFF6B6B), "支出")
                Spacer(Modifier.width(20.dp))
                LegendDot(Color(0xFF2ECC71), "收入")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

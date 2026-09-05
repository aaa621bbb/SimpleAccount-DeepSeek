package com.simpleaccount.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.components.LineTrendView
import com.simpleaccount.app.ui.components.PieChartView
import com.simpleaccount.app.ui.components.SoftCard
import com.simpleaccount.app.ui.components.parseColor
import com.simpleaccount.app.ui.theme.LocalAppPalette
import com.simpleaccount.app.util.MoneyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val pal = LocalAppPalette.current
    var categorySheet by remember { mutableStateOf<String?>(null) }
    var legendExpanded by remember { mutableStateOf(false) }
    var legendShowAll by remember { mutableStateOf(false) }
    val categorySheetState = androidx.compose.material3.rememberModalBottomSheetState()

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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.month == "all") "全部月份" else state.month,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "¥" + MoneyUtil.fenToYuan(state.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.type == Transaction.TYPE_EXPENSE) pal.expense else pal.income
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(3.dp)
                ) {
                    SegButton("支出", state.type == Transaction.TYPE_EXPENSE) {
                        viewModel.setType(Transaction.TYPE_EXPENSE)
                    }
                    Spacer(Modifier.width(3.dp))
                    SegButton("收入", state.type == Transaction.TYPE_INCOME) {
                        viewModel.setType(Transaction.TYPE_INCOME)
                    }
                }
            }

            var showMonth by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                com.simpleaccount.app.ui.components.FilterChipButton(
                    label = if (state.month == "all") "全部月份" else state.month,
                    onClick = { showMonth = true }
                )
            }
            if (showMonth) {
                com.simpleaccount.app.ui.components.MonthPickerSheet(
                    months = state.months,
                    selected = state.month.takeIf { it != "all" },
                    allLabel = "全部月份",
                    onDismiss = { showMonth = false },
                    onPick = { m -> viewModel.setMonth(m ?: "all"); showMonth = false }
                )
            }

            layout.order.filter { it !in layout.hidden }.forEach { id ->
                when (id) {
                    StatsModules.PIE -> PieCard(
                        state = state,
                        previewCount = layout.pieLegendCount,
                        expanded = legendExpanded,
                        showAll = legendShowAll,
                        onExpand = { legendExpanded = true },
                        onShowAll = { legendShowAll = true },
                        onCollapse = { legendExpanded = false; legendShowAll = false },
                        onCategory = { categorySheet = it },
                    )
                    StatsModules.COMPARE -> if (state.total > 0) CompareCard(state)
                    StatsModules.MERCHANTS -> if (state.topMerchants.isNotEmpty()) MerchantsCard(state)
                    StatsModules.CALENDAR -> CalendarCard(viewModel, state)
                    StatsModules.TREND -> TrendCard(state, pal.expense, pal.income) { viewModel.setMonth(it) }
                    StatsModules.BARS -> if (state.trend.any { it.expense > 0 || it.income > 0 }) {
                        SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("近 12 个月柱", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, top = 14.dp))
                            Text("点一根看那个月。和上面的曲线不是同一件事：这里比高低，曲线比走势。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp))
                            com.simpleaccount.app.ui.components.BarChartView(state.trend, pal.expense, pal.income) { viewModel.setMonth(it) }
                        }
                    }
                    StatsModules.WEEKDAY -> if (state.weekdayTotals.any { it.second > 0 }) DimBarCard("星期分布", "按账单日期的星期几合计，不是猜测你周末更浪。", state.weekdayTotals, pal.expense)
                    StatsModules.HOURS -> if (state.hourBuckets.any { it.second > 0 }) DimBarCard("时段分布", "按账单上的时刻归入五个时段。没填时间的不算。", state.hourBuckets, pal.expense)
                }
            }
        }
    }

    categorySheet?.let { cat ->
        CategoryTxSheet(
            categoryName = cat,
            state = state,
            sheetState = categorySheetState,
            onDismiss = { categorySheet = null }
        )
    }
}

@Composable
private fun PieCard(
    state: StatsUiState,
    previewCount: Int,
    expanded: Boolean,
    showAll: Boolean,
    onExpand: () -> Unit,
    onShowAll: () -> Unit,
    onCollapse: () -> Unit,
    onCategory: (String) -> Unit,
) {
    SoftCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        PieChartView(
            slices = state.slices,
            centerLabel = if (state.type == Transaction.TYPE_EXPENSE) "总支出" else "总收入",
            centerValue = "¥" + MoneyUtil.fenToYuan(state.total)
        )
        if (state.slices.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "本月暂无数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(
                Modifier
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 8.dp)
            ) {
                if (!expanded) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onExpand) {
                            Text("查看分类占比", fontSize = 13.sp)
                        }
                    }
                } else {
                    val preview = previewCount.coerceIn(0, state.slices.size)
                    val shown = when {
                        showAll || preview == 0 -> state.slices
                        else -> state.slices.take(preview)
                    }
                    Column(Modifier.height((shown.size * 48).dp)) {
                        shown.forEach { s ->
                            PieLegendRow(s, state.total) { onCategory(s.categoryName) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!showAll && preview > 0 && state.slices.size > preview) {
                            TextButton(onClick = onShowAll) {
                                Text("展开全部 ${state.slices.size} 类", fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCollapse) {
                            Text("收起", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PieLegendRow(s: PieSlice, total: Long, onClick: () -> Unit) {
    val percent = if (total > 0) s.value.toFloat() / total else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(parseColor(s.colorHex)))
            Spacer(Modifier.width(10.dp))
            Text(s.categoryName, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "%.0f%%".format(percent * 100),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "¥" + MoneyUtil.fenToYuan(s.value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0.02f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(parseColor(s.colorHex))
            )
        }
    }
}

@Composable
private fun CompareCard(state: StatsUiState) {
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("对照", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("日均 ¥${MoneyUtil.fenToYuan(state.dailyAvgFen)}", fontSize = 13.sp)
            Text(
                "工作日日均 ¥${MoneyUtil.fenToYuan(state.weekdayAvgFen)} · 周末日均 ¥${MoneyUtil.fenToYuan(state.weekendAvgFen)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.lastMonthTotal > 0 && state.month != "all") {
                val diff = state.total - state.lastMonthTotal
                Text(
                    "上月同期 ¥${MoneyUtil.fenToYuan(state.lastMonthTotal)}，" +
                        if (diff >= 0) "多 ¥${MoneyUtil.fenToYuan(diff)}" else "少 ¥${MoneyUtil.fenToYuan(-diff)}",
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MerchantsCard(state: StatsUiState) {
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("商家排行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            state.topMerchants.forEachIndexed { i, (name, amt) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", modifier = Modifier.width(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("¥${MoneyUtil.fenToYuan(amt)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TrendCard(
    state: StatsUiState,
    expenseColor: Color,
    incomeColor: Color,
    onMonth: (String) -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("近 12 个月趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            LegendDot(expenseColor, "支出")
            Spacer(Modifier.width(12.dp))
            LegendDot(incomeColor, "收入")
        }
        Text(
            "点击曲线上的点可查看对应月份",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp)
        )
        LineTrendView(state.trend, expenseColor = expenseColor, incomeColor = incomeColor, onPointClick = onMonth)
    }
}

@Composable
private fun DimBarCard(title: String, caption: String, rows: List<Pair<String, Long>>, color: Color) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            rows.forEach { (name, amt) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.width(72.dp), fontSize = 12.sp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((amt.toFloat() / max).coerceIn(0.02f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("¥${MoneyUtil.fenToYuan(amt)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SegButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTxSheet(
    categoryName: String,
    state: StatsUiState,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            val txList0 = state.catTx[categoryName].orEmpty()
            var byAmount by remember { mutableStateOf(false) }
            val txList = if (byAmount) txList0.sortedByDescending { it.amount } else txList0
            val sum = txList0.sumOf { it.amount }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { byAmount = !byAmount }) {
                    Text(if (byAmount) "按金额" else "按时间", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.month == "all") "全部月份 · 共 ${txList.size} 笔 · ¥${MoneyUtil.fenToYuan(sum)}"
                else "${state.month} · 共 ${txList.size} 笔 · ¥${MoneyUtil.fenToYuan(sum)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (txList.isEmpty()) {
                Text("没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                LazyColumn {
                    items(txList.size) { i ->
                        val t = txList[i]
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    listOf(t.merchant, t.product).filter { it.isNotBlank() }
                                        .joinToString(" · ").ifBlank { t.category },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(t.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                (if (t.type == Transaction.TYPE_INCOME) "+" else "-") +
                                    "¥" + MoneyUtil.fenToYuan(t.amount),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (t.type == Transaction.TYPE_INCOME)
                                    LocalAppPalette.current.income
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

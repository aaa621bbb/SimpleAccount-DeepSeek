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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.components.LineTrendView
import com.simpleaccount.app.ui.components.PieChartView
import com.simpleaccount.app.ui.components.SoftCard
import com.simpleaccount.app.ui.components.parseColor
import com.simpleaccount.app.util.MoneyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.uiState.collectAsState()
    // 点击某分类 → 弹出该分类的账单明细
    var categorySheet by remember { mutableStateOf<String?>(null) }
    var showAllCats by remember { mutableStateOf(false) }
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
            // 紧凑工具行：月份说明 + 支出/收入切换（不再放大数字卡，饼图中心已有总额）
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
                    color = if (state.type == Transaction.TYPE_EXPENSE) com.simpleaccount.app.ui.theme.AppColors.Expense
                    else com.simpleaccount.app.ui.theme.AppColors.Income
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

            // ── 分类构成：环形图 + 占比条形（第一个卡片）──
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
                        Modifier
                            .fillMaxWidth()
                            .height(324.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "本月暂无数据",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // 图例固定 6 行高度：月份切换时分类数量变化不会把下面的日历顶得乱跳。
                    // 超过 6 类点「查看全部」弹层，不撑开卡片。
                    val shownSlices = state.slices.take(6)
                    Column(
                        Modifier
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Column(Modifier.height(288.dp)) {
                            shownSlices.forEach { s ->
                                val percent = if (state.total > 0) s.value.toFloat() / state.total else 0f
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { categorySheet = s.categoryName }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(parseColor(s.colorHex))
                                        )
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
                        }
                        // 按钮行始终占位，避免「有/没有更多分类」造成高度差
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.slices.size > 6) {
                                TextButton(onClick = { showAllCats = true }) {
                                    Text("查看全部 ${state.slices.size} 类", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (showAllCats) {
                        androidx.compose.material3.ModalBottomSheet(
                            onDismissRequest = { showAllCats = false }
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                                Text(
                                    "全部分类",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                state.slices.forEach { s ->
                                    val percent = if (state.total > 0) s.value.toFloat() / state.total else 0f
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showAllCats = false
                                                categorySheet = s.categoryName
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(10.dp).clip(CircleShape)
                                                .background(parseColor(s.colorHex))
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(s.categoryName, modifier = Modifier.weight(1f))
                                        Text(
                                            "%.0f%%  ¥".format(percent * 100) + MoneyUtil.fenToYuan(s.value),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.total > 0) {
                SoftCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
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
            if (state.topMerchants.isNotEmpty()) {
                SoftCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
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

            // ── 每日花销日历（第二个卡片）──
            CalendarCard(viewModel, state)

            // ── 趋势卡片（点击数据点 → 切到该月） ──
            SoftCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "近 12 个月趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    LegendDot(com.simpleaccount.app.ui.theme.AppColors.Expense, "支出")
                    Spacer(Modifier.width(12.dp))
                    LegendDot(com.simpleaccount.app.ui.theme.AppColors.Income, "收入")
                }
                Text(
                    "点击曲线上的点可查看对应月份",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp)
                )
                LineTrendView(state.trend, onPointClick = { viewModel.setMonth(it) })
            }
        }
    }

    // 点击分类 → 该分类账单明细
    categorySheet?.let { cat ->
        CategoryTxSheet(
            categoryName = cat,
            state = state,
            sheetState = categorySheetState,
            onDismiss = { categorySheet = null }
        )
    }
}

/** 分段按钮（选中主色底白字，未选中透明） */
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

/** 点击分类 → 弹出该分类在当前月份/口径下的全部账单 */
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
            // 排序：默认按时间（数据源已按时间倒序），可切按金额
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
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    listOf(t.merchant, t.product).filter { it.isNotBlank() }
                                        .joinToString(" · ").ifBlank { t.category },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    t.date,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                (if (t.type == Transaction.TYPE_INCOME) "+" else "-") +
                                    "¥" + MoneyUtil.fenToYuan(t.amount),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (t.type == Transaction.TYPE_INCOME) Color(0xFF2ECC71)
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

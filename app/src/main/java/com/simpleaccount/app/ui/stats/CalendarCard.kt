package com.simpleaccount.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.simpleaccount.app.ui.components.SoftCard
import com.simpleaccount.app.ui.components.parseColor
import com.simpleaccount.app.ui.theme.LocalAppPalette
import com.simpleaccount.app.util.MoneyUtil

/**
 * 每日花销日历：热力底色 + 固定 6 行（月份切换高度不变，日历不再上下跳）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarCard(viewModel: StatsViewModel, state: StatsUiState) {
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val pal = LocalAppPalette.current

    SoftCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "每日花销",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.calPrevMonth() }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                "${state.calYear}年${state.calMonthNum}月",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = { viewModel.calNextMonth() }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        w,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (i >= 5) pal.accent
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val ym = java.time.YearMonth.of(state.calYear, state.calMonthNum)
        val leading = ym.atDay(1).dayOfWeek.value - 1
        // 固定 6 周 × 7 格 = 42，月份切换日历高度不变
        val cells: List<Int?> = List(leading) { null } + (1..ym.lengthOfMonth()).toList()
        val padded = cells + List((42 - cells.size).coerceAtLeast(0)) { null }

        val maxShown = remember(state.calDayTotals, state.type) {
            state.calDayTotals.values.maxOfOrNull {
                if (state.type == Transaction.TYPE_EXPENSE) it.expense else it.income
            }?.coerceAtLeast(1L) ?: 1L
        }
        val heat = if (state.type == Transaction.TYPE_EXPENSE) pal.expense else pal.income
        val today = java.time.LocalDate.now()

        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                week.forEach { day ->
                    val isToday = day != null &&
                        today.year == state.calYear &&
                        today.monthValue == state.calMonthNum &&
                        today.dayOfMonth == day
                    val totals = day?.let { state.calDayTotals[it] }
                    val shown = if (state.type == Transaction.TYPE_EXPENSE) totals?.expense else totals?.income
                    val intensity = if (shown != null && shown > 0) {
                        (shown.toFloat() / maxShown).coerceIn(0.12f, 0.55f)
                    } else 0f
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    day == null -> Color.Transparent
                                    intensity > 0f -> heat.copy(alpha = intensity)
                                    isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                                }
                            )
                            .then(
                                if (isToday) Modifier.border(
                                    1.5.dp,
                                    pal.accent,
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .then(
                                if (day != null) Modifier.clickable { selectedDay = day }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day",
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (intensity > 0.35f) Color.White
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (shown != null && shown > 0) {
                                    Text(
                                        (if (state.type == Transaction.TYPE_EXPENSE) "-" else "+") +
                                            compactYuan(shown),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (intensity > 0.35f) Color.White.copy(alpha = 0.92f)
                                        else heat,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (selectedDay != null) {
        val day = selectedDay!!
        val txList = state.calDayTx[day] ?: emptyList()
        ModalBottomSheet(
            onDismissRequest = { selectedDay = null },
            sheetState = sheetState
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                val dateLabel = "${state.calYear}-${state.calMonthNum.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                var byAmount by remember { mutableStateOf(false) }
                val sortedList = if (byAmount) txList.sortedByDescending { it.amount } else txList
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$dateLabel 流水",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { byAmount = !byAmount }) {
                        Text(if (byAmount) "按金额" else "按时间", fontSize = 12.sp)
                    }
                }
                val totals = state.calDayTotals[day]
                if (totals != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支出 ¥${MoneyUtil.fenToYuan(totals.expense)} · 收入 ¥${MoneyUtil.fenToYuan(totals.income)} · 共 ${txList.size} 笔",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (txList.isEmpty()) {
                    Text(
                        "当天没有记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn {
                        items(sortedList.size) { i ->
                            val t = sortedList[i]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(state.categoryColorMap[t.category] ?: "#BDC3C7"))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.category, style = MaterialTheme.typography.bodyMedium)
                                    val detail = listOf(t.merchant, t.product)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (detail.isNotBlank()) {
                                        Text(
                                            detail,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    (if (t.type == Transaction.TYPE_INCOME) "+" else "-") +
                                        "¥" + MoneyUtil.fenToYuan(t.amount),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (t.type == Transaction.TYPE_INCOME) pal.income
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun compactYuan(fen: Long): String {
    val y = fen / 100.0
    return when {
        y >= 10000 -> "%.1f万".format(y / 10000)
        y >= 1000 -> "%.0f".format(y)
        y >= 100 -> "%.0f".format(y)
        else -> MoneyUtil.fenToYuan(fen)
    }
}



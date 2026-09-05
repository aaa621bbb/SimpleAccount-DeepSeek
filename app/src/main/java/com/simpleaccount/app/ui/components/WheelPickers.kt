package com.simpleaccount.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private val ITEM_H = 44.dp

@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            state.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val i = state.firstVisibleItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            if (i != selectedIndex) onSelected(i)
        }
    }
    Box(modifier.height(ITEM_H * 5), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ITEM_H)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.height(ITEM_H * 5),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(2) { Spacer(Modifier.height(ITEM_H)) }
            items(items.size) { i ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ITEM_H)
                        .clickable { onSelected(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        items[i],
                        fontSize = if (selected) 18.sp else 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(2) { Spacer(Modifier.height(ITEM_H)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateWheelSheet(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    val today = LocalDate.now()
    val parts = initialDate.split("-").mapNotNull { it.toIntOrNull() }
    val y = remember { androidx.compose.runtime.mutableIntStateOf(parts.getOrElse(0) { today.year }) }
    val m = remember { androidx.compose.runtime.mutableIntStateOf(parts.getOrElse(1) { today.monthValue }) }
    val d = remember { androidx.compose.runtime.mutableIntStateOf(parts.getOrElse(2) { today.dayOfMonth }) }

    val years = (today.year - 8..today.year + 1).toList()
    val months = (1..12).toList()
    val daysInMonth by remember {
        derivedStateOf { YearMonth.of(y.intValue, m.intValue).lengthOfMonth() }
    }
    val days = (1..daysInMonth).toList()
    if (d.intValue > daysInMonth) d.intValue = daysInMonth

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选日期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    y.intValue = today.year; m.intValue = today.monthValue; d.intValue = today.dayOfMonth
                }) { Text("今天") }
                TextButton(onClick = { onConfirm(y.intValue, m.intValue, d.intValue) }) { Text("确定") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelColumn(
                    items = years.map { "${it}年" },
                    selectedIndex = years.indexOf(y.intValue).coerceAtLeast(0),
                    onSelected = { y.intValue = years[it] },
                    modifier = Modifier.weight(1.2f)
                )
                WheelColumn(
                    items = months.map { "${it}月" },
                    selectedIndex = m.intValue - 1,
                    onSelected = { m.intValue = months[it] },
                    modifier = Modifier.weight(1f)
                )
                WheelColumn(
                    items = days.map { "${it}日" },
                    selectedIndex = (d.intValue - 1).coerceIn(0, days.lastIndex),
                    onSelected = { d.intValue = days[it] },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val now = java.time.LocalTime.now()
    val parts = initial.split(":")
    var h = remember {
        androidx.compose.runtime.mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: now.hour)
    }
    var min = remember {
        androidx.compose.runtime.mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: now.minute)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("选时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    h.intValue = now.hour; min.intValue = now.minute
                }) { Text("现在") }
                TextButton(onClick = { onConfirm("%02d:%02d".format(h.intValue, min.intValue)) }) { Text("确定") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelColumn(
                    items = (0..23).map { "%02d 时".format(it) },
                    selectedIndex = h.intValue,
                    onSelected = { h.intValue = it },
                    modifier = Modifier.weight(1f)
                )
                WheelColumn(
                    items = (0..59).map { "%02d 分".format(it) },
                    selectedIndex = min.intValue,
                    onSelected = { min.intValue = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    months: List<String>,
    selected: String?,
    allLabel: String = "全部月份",
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text("选择月份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val chips = listOf<String?>(null) + months
            val rows = chips.chunked(3)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val sel = selected == m
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onPick(m) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                m ?: allLabel,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

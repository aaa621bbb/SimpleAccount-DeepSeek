package com.simpleaccount.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 自研日历：月份横向飞入，选中日格 spring 放大回缩，邻月格子淡出。
 * 全部用 graphicsLayer 的 scale / alpha / translation，不改 cell 的 layout 尺寸。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDateSheet(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    val today = LocalDate.now()
    val parts = initialDate.split("-").mapNotNull { it.toIntOrNull() }
    var selected by remember {
        mutableStateOf(
            runCatching {
                LocalDate.of(
                    parts.getOrElse(0) { today.year },
                    parts.getOrElse(1) { today.monthValue },
                    parts.getOrElse(2) { today.dayOfMonth },
                )
            }.getOrDefault(today),
        )
    }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selected)) }
    val reduce = LocalReduceMotion.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选日期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    selected = today
                    visibleMonth = YearMonth.from(today)
                }) { Text("今天") }
                TextButton(onClick = {
                    onConfirm(selected.year, selected.monthValue, selected.dayOfMonth)
                }) { Text("确定") }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Text(
                    "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(w, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = visibleMonth,
                transitionSpec = {
                    val forward = targetState > initialState
                    val ms = Motion.dur(reduce, Motion.MONTH_MS)
                    val fade = Motion.dur(reduce, Motion.FADE_MS)
                    val enter = slideInHorizontally(animationSpec = Motion.tweenOrSnap(reduce, ms)) {
                        if (forward) it else -it
                    } + fadeIn(animationSpec = Motion.tweenOrSnap(reduce, fade))
                    val exit = slideOutHorizontally(animationSpec = Motion.tweenOrSnap(reduce, ms)) {
                        if (forward) -it / 4 else it / 4
                    } + fadeOut(animationSpec = Motion.tweenOrSnap(reduce, fade))
                    enter togetherWith exit
                },
                label = "month-fly",
            ) { month ->
                MonthGrid(
                    month = month,
                    selected = selected,
                    today = today,
                    onPick = { selected = it },
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val first = month.atDay(1)
    val shift = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    val cells = remember(month) {
        val list = ArrayList<LocalDate?>(42)
        val prev = month.minusMonths(1)
        val prevLen = prev.lengthOfMonth()
        for (i in 0 until shift) list.add(prev.atDay(prevLen - shift + 1 + i))
        for (d in 1..daysInMonth) list.add(month.atDay(d))
        val next = month.plusMonths(1)
        var n = 1
        while (list.size < 42) {
            list.add(next.atDay(n))
            n++
        }
        list
    }
    Column {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                week.forEach { date ->
                    val inMonth = date != null && YearMonth.from(date) == month
                    val isSel = date == selected
                    val isToday = date == today
                    val targetScale = if (isSel) 1.12f else 1f
                    val scale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = Motion.springOrSnap(reduce, Motion.snapSpring),
                        label = "day-scale",
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (inMonth) 1f else 0.28f,
                        animationSpec = Motion.tweenOrSnap(reduce, Motion.FADE_MS),
                        label = "day-fade",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSel -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .clickable(enabled = date != null) { date?.let(onPick) }
                            .semantics {
                                contentDescription = date?.let {
                                    val name = it.month.getDisplayName(TextStyle.SHORT, Locale.CHINA)
                                    "${it.year}年$name${it.dayOfMonth}日" + if (isSel) "，已选中" else ""
                                } ?: ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date?.dayOfMonth?.toString() ?: "",
                            fontSize = 14.sp,
                            fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSel -> MaterialTheme.colorScheme.onPrimary
                                !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

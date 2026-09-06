package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.stats.StatsLayoutUi
import com.simpleaccount.app.ui.stats.StatsModules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class StatsLayoutViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val layout = combine(
        settings.statsOrderFlow,
        settings.statsHiddenFlow,
        settings.pieLegendCountFlow,
    ) { order, hidden, count ->
        StatsLayoutUi(
            order = StatsModules.parseOrder(order),
            hidden = StatsModules.parseHidden(hidden),
            pieLegendCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsLayoutUi())

    fun move(id: String, delta: Int) {
        val cur = layout.value.order.toMutableList()
        val i = cur.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in cur.indices) return
        val tmp = cur[i]
        cur[i] = cur[j]
        cur[j] = tmp
        viewModelScope.launch { settings.setStatsOrder(cur.joinToString(",")) }
    }

    /** 拖拽重排：from -> to */
    fun reorder(from: Int, to: Int) {
        if (from == to) return
        val cur = layout.value.order.toMutableList()
        if (from !in cur.indices || to !in cur.indices) return
        val item = cur.removeAt(from)
        cur.add(to, item)
        viewModelScope.launch { settings.setStatsOrder(cur.joinToString(",")) }
    }

    fun setOrder(order: List<String>) {
        viewModelScope.launch { settings.setStatsOrder(order.joinToString(",")) }
    }

    fun toggle(id: String, visible: Boolean) {
        val hidden = layout.value.hidden.toMutableSet()
        if (visible) hidden.remove(id) else hidden.add(id)
        viewModelScope.launch { settings.setStatsHidden(hidden.joinToString(",")) }
    }

    fun setPieCount(n: Int) {
        viewModelScope.launch { settings.setPieLegendCount(n) }
    }
}

@Composable
fun StatsLayoutScreen(
    navController: NavHostController,
    viewModel: StatsLayoutViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 本地可拖动顺序（与持久化解耦，拖中预览不立即写库，松手再提交）
    var localOrder by remember(layout.order) { mutableStateOf(layout.order) }
    // 保持本地与远端同步（非拖动时）
    LaunchedEffect(layout.order) {
        // 若当前没有拖动，同步远端
        localOrder = layout.order
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }
    var originalOrder by remember { mutableStateOf<List<String>>(emptyList()) }

    Scaffold(
        topBar = { SettingsSubToolbar("统计页图表", onBack = { navController.popBackStack() }) }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "header") {
                Text("显示与顺序", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "关掉的卡片不出现在统计页。长按把手直接拖动整序，支持大幅跨距移动；也可点箭头微调。拖入时高亮预览，超出边界松手自动回滚。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "提示：拖动把手(≡)可长按后上下拖动，拖动时卡片浮起并显示插入位置。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            itemsIndexed(localOrder, key = { _, id -> id }) { index, id ->
                val visible = id !in layout.hidden
                val isDragging = draggingId == id
                val isTarget = targetIndex == index && draggingId != null && draggingId != id

                // 目标占位预览：虚线边框
                if (isTarget) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            else if (!visible) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                        .border(
                            width = if (isDragging) 1.5.dp else 0.5.dp,
                            color = if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .graphicsLayer {
                            if (isDragging) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = visible, onCheckedChange = { viewModel.toggle(id, it) })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(StatsModules.title(id), fontWeight = if (visible) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                        if (!visible) Text("已隐藏", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 把手：长按拖动
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingId = id
                                        draggingIndex = index
                                        targetIndex = index
                                        originalOrder = localOrder.toList()
                                    },
                                    onDragEnd = {
                                        // 提交或回滚
                                        val from = draggingIndex
                                        val to = targetIndex
                                        if (from >= 0 && to >= 0 && from != to && from in localOrder.indices && to in localOrder.indices) {
                                            // 已在拖动中实时交换，提交持久化
                                            viewModel.setOrder(localOrder)
                                        } else if (from >= 0 && to !in localOrder.indices) {
                                            // 越界回滚
                                            localOrder = originalOrder
                                        }
                                        draggingId = null
                                        draggingIndex = -1
                                        targetIndex = -1
                                    },
                                    onDragCancel = {
                                        // 回滚
                                        localOrder = originalOrder
                                        draggingId = null
                                        draggingIndex = -1
                                        targetIndex = -1
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        // 根据垂直位移估算目标 index；每 56dp 约一项
                                        val delta = (dragAmount.y / 56f).roundToInt()
                                        // 动态更新 targetIndex 基于位移，但更稳的是直接根据拖动位移累积
                                        // 这里采用增量交换策略
                                        if (delta != 0) {
                                            val curIdx = localOrder.indexOf(id)
                                            if (curIdx >= 0) {
                                                val newIdx = (curIdx + delta).coerceIn(0, localOrder.lastIndex)
                                                if (newIdx != curIdx) {
                                                    val mutable = localOrder.toMutableList()
                                                    val item = mutable.removeAt(curIdx)
                                                    mutable.add(newIdx, item)
                                                    localOrder = mutable
                                                    targetIndex = newIdx
                                                    // 轻微震动提示换位
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        } else {
                                            // 未跨项时也刷新 targetIndex 用于预览线
                                            val curIdx = localOrder.indexOf(id)
                                            targetIndex = curIdx
                                        }
                                        // 边缘自动滚动
                                        scope.launch {
                                            val layoutInfo = listState.layoutInfo
                                            val viewportH = layoutInfo.viewportSize.height
                                            val first = layoutInfo.visibleItemsInfo.firstOrNull()
                                            val last = layoutInfo.visibleItemsInfo.lastOrNull()
                                            if (first != null && change.position.y < 120 && listState.firstVisibleItemIndex > 0) {
                                                listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
                                            }
                                            if (last != null && change.position.y > viewportH - 120) {
                                                listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = "长按拖动", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { viewModel.move(id, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = { viewModel.move(id, 1) }, enabled = index < localOrder.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
            }

            item(key = "footer_pie") {
                Spacer(Modifier.height(16.dp))
                Text("饼图百分比条数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "默认不显示百分比，点饼图下的「查看占比」才展开。设成 3 就只占 3 条的位置；再点「展开全部」在当前页看完，不会从底部弹出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0, 3, 5, 8).forEach { n ->
                        FilterChip(
                            selected = layout.pieLegendCount == n,
                            onClick = { viewModel.setPieCount(n) },
                            label = { Text(if (n == 0) "不预览" else "${n} 条", fontSize = 13.sp) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "当前顺序共 ${localOrder.size} 项，已隐藏 ${layout.hidden.size} 项。拖动后自动保存，重启仍有效。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(8.dp)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

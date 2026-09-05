package com.simpleaccount.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion
import kotlin.math.abs

/**
 * 两行横向流式品类。横滑优先于页面纵滚；选中高亮游标用 graphicsLayer 平移跟随，
 * 列有轻微视差。高度固定，避免把纵滚父级的 measure 搅乱。
 */
@Composable
fun CategoryCarousel(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val columns = remember(categories) { categories.chunked(2) }

    val selectedCol = columns.indexOfFirst { col -> col.any { it.name == selected?.name } }
    val selectedRow = if (selectedCol >= 0) columns[selectedCol].indexOfFirst { it.name == selected?.name } else 0
    val visible = listState.layoutInfo.visibleItemsInfo.find { it.index == selectedCol }
    val targetX = (visible?.offset ?: 0).toFloat()
    val targetW = (visible?.size ?: with(density) { 76.dp.roundToPx() }).toFloat()
    val rowH = with(density) { 64.dp.toPx() }
    val targetY = selectedRow * rowH

    val cursorX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = Motion.springOrSnap(reduce, Motion.cursorSpring),
        label = "cat-cursor-x",
    )
    val cursorY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = Motion.springOrSnap(reduce, Motion.cursorSpring),
        label = "cat-cursor-y",
    )
    val cursorW by animateFloatAsState(
        targetValue = targetW,
        animationSpec = Motion.springOrSnap(reduce, Motion.cursorSpring),
        label = "cat-cursor-w",
    )

    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (abs(available.x) > abs(available.y)) Offset(0f, available.y) else Offset.Zero
            }
        }
    }

    val firstOffset = listState.firstVisibleItemScrollOffset
    val parallax = if (reduce) 0f else -(firstOffset % 80) * 0.12f

    Box(
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .nestedScroll(nested)
            .semantics { contentDescription = "分类，两行横向滑动选择" },
    ) {
        if (selectedCol >= 0) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = cursorX
                        translationY = cursorY + 2f
                    }
                    .width(with(density) { cursorW.toDp() })
                    .height(62.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(columns, key = { _, col -> col.joinToString { it.name } }) { _, pair ->
                Column(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .width(76.dp)
                        .graphicsLayer { translationX = parallax },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    pair.forEach { cat ->
                        val sel = selected?.name == cat.name
                        val scale by animateFloatAsState(
                            targetValue = if (sel) 1.06f else 1f,
                            animationSpec = Motion.springOrSnap(reduce, Motion.pressSpring),
                            label = "cat-scale-${cat.name}",
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelect(cat) }
                                .padding(vertical = 6.dp)
                                .semantics {
                                    contentDescription = "分类 ${cat.name}"
                                    this.selected = sel
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CategoryIconCircle(cat, size = 32)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                cat.name,
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.height(62.dp))
                }
            }
        }
    }
}

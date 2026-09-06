package com.simpleaccount.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.theme.LocalAppPalette
import com.simpleaccount.app.util.IconMapper
import com.simpleaccount.app.util.MoneyUtil

/** 统一卡片：令牌圆角 + 轻投影（左上光源）+ 发丝描边。全 App 只用这一张。已下沉皮肤：玻璃/立体分别呈现通透与压差。 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = com.simpleaccount.app.ui.theme.LocalTokens.current
    val skin = com.simpleaccount.app.ui.theme.LocalUiSkin.current
    val isGlass = skin == com.simpleaccount.app.ui.theme.UiSkin.GLASS
    val isDepth = skin == com.simpleaccount.app.ui.theme.UiSkin.DEPTH
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(t.radiusXl),
        colors = CardDefaults.cardColors(
            containerColor = when (skin) {
                com.simpleaccount.app.ui.theme.UiSkin.GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = when (skin) {
                com.simpleaccount.app.ui.theme.UiSkin.GLASS -> 0.dp
                com.simpleaccount.app.ui.theme.UiSkin.DEPTH -> t.elevRaised + 4.dp
                else -> t.elevRaised
            }
        ),
        border = BorderStroke(
            t.hairline,
            when (skin) {
                com.simpleaccount.app.ui.theme.UiSkin.GLASS -> com.simpleaccount.app.ui.theme.UiTokens.glassBorderColor(
                    MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.White || MaterialTheme.colorScheme.surface.luminance() > 0.5f
                )
                com.simpleaccount.app.ui.theme.UiSkin.DEPTH -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(content = content)
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

/** 分类彩色圆底 + 图标 */
@Composable
fun CategoryIconCircle(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val color = parseColor(category?.colorHex ?: "#BDC3C7")
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = IconMapper.map(category?.iconName ?: "more_horiz"),
            contentDescription = category?.name,
            tint = color,
            modifier = Modifier.size((size * 0.52).dp)
        )
    }
}

/** 单条流水行：商家主标题、分类次级、金额强调，与首页卡片同一信息层级。皮肤下沉：玻璃为羽化描边，立体为压差阴影。 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    onClick: (() -> Unit)? = null,
    showDate: Boolean = true,
) {
    val t = com.simpleaccount.app.ui.theme.LocalTokens.current
    val skin = com.simpleaccount.app.ui.theme.LocalUiSkin.current
    val accent = parseColor(category?.colorHex ?: "#BDC3C7")
    val title = transaction.merchant.ifBlank { category?.name ?: "未分类" }
    val secondary = buildList {
        val catName = category?.name ?: transaction.category
        if (catName.isNotBlank() && catName != title) add(catName)
        if (transaction.product.isNotBlank()) add(transaction.product)
    }.joinToString(" · ")
    val rowBg = when (skin) {
        com.simpleaccount.app.ui.theme.UiSkin.GLASS -> Modifier.background(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(t.radiusMd)
        ).border(0.5.dp, com.simpleaccount.app.ui.theme.UiTokens.glassBorderColor(true), RoundedCornerShape(t.radiusMd))
        com.simpleaccount.app.ui.theme.UiSkin.DEPTH -> Modifier
            .clip(RoundedCornerShape(t.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
        else -> Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = t.space16, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        CategoryIconCircle(category, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (transaction.type == Transaction.TYPE_INCOME) "+¥" else "-¥") +
                        MoneyUtil.fenToYuan(transaction.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == Transaction.TYPE_INCOME)
                    LocalAppPalette.current.income else MaterialTheme.colorScheme.onSurface
            )
            if (showDate) {
                Text(
                    text = transaction.date.takeLast(5) +
                        (if (transaction.time.isNotBlank()) " " + transaction.time else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 解析 "#RRGGBB" 或 "#AARRGGBB" → Color */
fun parseColor(hex: String): Color {
    if (hex.isEmpty()) return Color(0xFFBDC3C7)
    val cleaned = hex.replace("#", "")
    val norm = when (cleaned.length) {
        6 -> "FF$cleaned"
        8 -> cleaned
        else -> return Color(0xFFBDC3C7)
    }
    return try {
        Color(norm.toLong(16))
    } catch (e: Exception) {
        Color(0xFFBDC3C7)
    }
}

@Composable
fun EmptyState(text: String, caption: String? = null, modifier: Modifier = Modifier) {
    val t = com.simpleaccount.app.ui.theme.LocalTokens.current
    Box(modifier.fillMaxWidth().padding(t.space32), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (!caption.isNullOrBlank()) {
                Spacer(Modifier.size(t.space8))
                Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PageEnter(content: @Composable () -> Unit) {
    val reduce = com.simpleaccount.app.ui.motion.LocalReduceMotion.current
    val appear = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (reduce) 0f else 12f) }
    val alpha = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(reduce) {
        if (reduce) {
            appear.snapTo(0f); alpha.snapTo(1f)
        } else {
            appear.animateTo(0f, com.simpleaccount.app.ui.motion.Motion.softSpring)
            alpha.animateTo(1f, com.simpleaccount.app.ui.motion.Motion.tweenOrSnap(false, com.simpleaccount.app.ui.motion.Motion.PAGE_MS))
        }
    }
    Box(Modifier.graphicsLayer { translationY = appear.value; this.alpha = alpha.value }) {
        content()
    }
}

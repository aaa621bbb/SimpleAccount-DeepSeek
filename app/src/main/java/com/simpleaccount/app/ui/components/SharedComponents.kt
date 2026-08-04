package com.simpleaccount.app.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.util.IconMapper
import com.simpleaccount.app.util.MoneyUtil

/** 分类彩色小圆底 + 图标 */
@Composable
fun CategoryIconCircle(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val bg = parseColor(category?.colorHex ?: "#BDC3C7")
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = IconMapper.map(category?.iconName ?: "more_horiz"),
            contentDescription = category?.name,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55).dp)
        )
    }
}

/** 单条流水行：圆形图标 + 分类·商家(左侧)，日期+金额(右侧) */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconCircle(category, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = category?.name ?: "未分类",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (transaction.merchant.isNotBlank()) {
                Text(
                    text = transaction.merchant,
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.type == Transaction.TYPE_INCOME)
                    Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = transaction.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

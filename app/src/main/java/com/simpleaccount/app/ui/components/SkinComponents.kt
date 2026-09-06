package com.simpleaccount.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.simpleaccount.app.ui.theme.LocalUiSkin
import com.simpleaccount.app.ui.theme.UiSkin
import com.simpleaccount.app.ui.theme.LocalTokens

/**
 * 皮肤下沉到每个元件：按钮、输入框、卡片、列表条的皮肤感知包装。
 * 调用方直接用这些组件即可自动跟随全局皮肤，无需手动传参。
 */

@Composable
fun SkinButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
) {
    val skin = LocalUiSkin.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(
        when (skin) {
            UiSkin.GLASS -> 20.dp
            UiSkin.DEPTH -> 12.dp
            else -> 14.dp
        }
    )
    val container = when (skin) {
        UiSkin.GLASS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        UiSkin.DEPTH -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    val elevation = when (skin) {
        UiSkin.GLASS -> 0.dp
        UiSkin.DEPTH -> 8.dp
        else -> 2.dp
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .shadow(elevation, shape)
            .then(
                if (skin == UiSkin.GLASS) Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), shape) else Modifier
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = container),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text)
    }
}

@Composable
fun SkinCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val skin = LocalUiSkin.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.radiusLg)
    val bg = when (skin) {
        UiSkin.GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when (skin) {
        UiSkin.GLASS -> Color.White.copy(alpha = 0.5f)
        UiSkin.DEPTH -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val elevation = when (skin) {
        UiSkin.GLASS -> 0.dp
        UiSkin.DEPTH -> tokens.elevRaised + 4.dp
        else -> tokens.elevRaised
    }
    androidx.compose.material3.Card(
        modifier = modifier
            .shadow(elevation, shape)
            .border(0.5.dp, borderColor, shape),
        shape = shape,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = bg)
    ) {
        content()
    }
}

@Composable
fun SkinListItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val skin = LocalUiSkin.current
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(tokens.radiusMd)
    val bg = when (skin) {
        UiSkin.GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderCol = when (skin) {
        UiSkin.GLASS -> Color.White.copy(alpha = 0.4f)
        UiSkin.DEPTH -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, borderCol, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (leading != null) {
                Box(Modifier.padding(end = 10.dp)) { leading() }
            }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing()
        }
    }
}

@Composable
fun SkinOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val skin = LocalUiSkin.current
    val shape = when (skin) {
        UiSkin.GLASS -> RoundedCornerShape(16.dp)
        UiSkin.DEPTH -> RoundedCornerShape(10.dp)
        else -> RoundedCornerShape(12.dp)
    }
    val colors = when (skin) {
        UiSkin.GLASS -> OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
        )
        UiSkin.DEPTH -> OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
        else -> OutlinedTextFieldDefaults.colors()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = shape,
        colors = colors,
        modifier = modifier
    )
}

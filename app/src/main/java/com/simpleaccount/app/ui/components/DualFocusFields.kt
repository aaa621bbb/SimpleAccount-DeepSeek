package com.simpleaccount.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion
import com.simpleaccount.app.ui.theme.LocalTokens

/**
 * 商家 / 商品半等高并排双栏。聚焦时 graphicsLayer scale-in，不改 layout 高度。
 * 商家栏对已建档商户做前缀优先、其次包含的联想，不走模糊合并。
 */
@Composable
fun DualFocusFields(
    merchant: String,
    product: String,
    onMerchant: (String) -> Unit,
    onProduct: (String) -> Unit,
    merchantHints: List<String> = emptyList(),
    onPickMerchant: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space12),
        ) {
            FocusScaleField(
                value = merchant,
                onValueChange = onMerchant,
                label = "商家",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Next,
            )
            FocusScaleField(
                value = product,
                onValueChange = onProduct,
                label = "商品",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Next,
            )
        }
        if (merchantHints.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = t.space4),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = t.elevRaised,
                shadowElevation = t.elevRaised,
            ) {
                Column {
                    merchantHints.forEach { name ->
                        Text(
                            name,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickMerchant(name) }
                                .padding(horizontal = t.space16, vertical = t.space12),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FocusScaleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
) {
    val reduce = LocalReduceMotion.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = Motion.springOrSnap(reduce, Motion.softSpring),
        label = "field-scale-$label",
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .com.simpleaccount.app.ui.theme.skinControl(
                com.simpleaccount.app.ui.theme.LocalVisualStyle.current,
                raised = focused,
            )
            .onFocusChanged { focused = it.isFocused },
    )
}

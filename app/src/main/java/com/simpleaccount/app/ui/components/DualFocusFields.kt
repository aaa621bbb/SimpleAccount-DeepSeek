package com.simpleaccount.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion

/**
 * 商家 / 商品半等高并排双栏。聚焦时 graphicsLayer scale-in，不改 layout 高度，
 * 避免 IME 弹出时整页错位跳屏。
 */
@Composable
fun DualFocusFields(
    merchant: String,
    product: String,
    onMerchant: (String) -> Unit,
    onProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            .onFocusChanged { focused = it.isFocused },
    )
}

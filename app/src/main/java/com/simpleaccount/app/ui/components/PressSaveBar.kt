package com.simpleaccount.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion
import com.simpleaccount.app.ui.motion.rememberHapticView

/**
 * 常驻首屏底栏保存。按下 scale 形变 + 系统涟漪；成功微震 + 勾选 spring 舒展。
 * 形变只走 graphicsLayer，底栏高度不变，避免把内容顶跳。
 */
@Composable
fun PressSaveBar(
    enabled: Boolean,
    success: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "保存",
) {
    val reduce = LocalReduceMotion.current
    val haptic = rememberHapticView()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            success -> 1f
            pressed -> 0.96f
            else -> 1f
        },
        animationSpec = Motion.springOrSnap(reduce, Motion.pressSpring),
        label = "save-press",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (success) 1f else 0.4f,
        animationSpec = Motion.springOrSnap(reduce, Motion.checkSpring),
        label = "save-check",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (success) 1f else 0f,
        animationSpec = Motion.tweenOrSnap(reduce, Motion.FADE_MS),
        label = "save-check-a",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (success) 0f else 1f,
        animationSpec = Motion.tweenOrSnap(reduce, Motion.FADE_MS),
        label = "save-label-a",
    )

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Button(
            onClick = {
                if (!reduce) haptic.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            enabled = enabled && !success,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .semantics { contentDescription = if (success) "已保存" else label },
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label,
                    modifier = Modifier.graphicsLayer { alpha = labelAlpha },
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkAlpha
                        },
                )
            }
        }
    }
}

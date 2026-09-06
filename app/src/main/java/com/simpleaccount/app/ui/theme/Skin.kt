package com.simpleaccount.app.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 全屏底：玻璃用光斑透底，景深用暗角塑形。不改业务 layout。 */
@Composable
fun AppSkinBackdrop(content: @Composable () -> Unit) {
    val style = LocalVisualStyle.current
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        when (style) {
            VisualStyle.GLASS -> Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(scheme.primary.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.18f, size.height * 0.12f),
                                radius = size.minDimension * 0.85f,
                            )
                        )
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(scheme.tertiary.copy(alpha = 0.14f), Color.Transparent),
                                center = Offset(size.width * 0.86f, size.height * 0.78f),
                                radius = size.minDimension * 0.7f,
                            )
                        )
                    }
            )
            VisualStyle.DEPTH -> Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.28f),
                                )
                            )
                        )
                    }
            )
            VisualStyle.DEFAULT -> {}
        }
        content()
    }
}

fun Modifier.skinPanel(style: VisualStyle, radiusPx: Float, glassFill: Color, depthShadow: Color, highlight: Color): Modifier {
    return when (style) {
        VisualStyle.GLASS -> {
            val blur = if (Build.VERSION.SDK_INT >= 31) {
                graphicsLayer {
                    renderEffect = RenderEffect
                        .createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                    clip = true
                }
            } else this
            blur
                .background(glassFill)
                .drawWithContent {
                    drawContent()
                    val cr = CornerRadius(radiusPx, radiusPx)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.04f)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height * 0.45f),
                        ),
                        cornerRadius = cr,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        cornerRadius = cr,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
        }
        VisualStyle.DEPTH -> drawBehind {
            val cr = CornerRadius(radiusPx, radiusPx)
            drawRoundRect(
                color = depthShadow,
                topLeft = Offset(5.dp.toPx(), 8.dp.toPx()),
                size = Size(size.width, size.height),
                cornerRadius = cr,
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                size = Size(size.width, size.height),
                cornerRadius = cr,
            )
        }.drawWithContent {
            drawContent()
            val cr = CornerRadius(radiusPx, radiusPx)
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(highlight.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                cornerRadius = cr,
                style = Stroke(width = 1.6.dp.toPx()),
            )
        }
        VisualStyle.DEFAULT -> this
    }
}

fun Modifier.skinControl(style: VisualStyle, raised: Boolean): Modifier = when (style) {
    VisualStyle.GLASS -> border(
        width = 0.7.dp,
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(if (raised) 18.dp else 14.dp),
    )
    VisualStyle.DEPTH -> graphicsLayer {
        shadowElevation = if (raised) 14f else 6f
        translationY = if (raised) -1.5f else 0f
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        clip = false
    }
    VisualStyle.DEFAULT -> this
}

package com.mowazze3.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import com.mowazze3.ui.theme.Layout
import com.mowazze3.ui.theme.Sizes

/** Keyboard focus: a 3dp ring 2dp outside the element. Drawn outside the bounds so nothing shifts. */
@Composable
fun Modifier.focusRing(focused: Boolean, cornerRadius: Dp): Modifier {
    val color = MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        if (focused) {
            val ring = Sizes.focusRing.toPx()
            val offset = Sizes.focusRingOffset.toPx() + ring / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(-offset, -offset),
                size = Size(size.width + 2 * offset, size.height + 2 * offset),
                cornerRadius = CornerRadius(cornerRadius.toPx() + offset),
                style = Stroke(ring),
            )
        }
    }
}

/** Disabled option cards and empty states use a dashed border. Drawn on top so a background can't hide it. */
fun Modifier.dashedBorder(color: Color, width: Dp, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Forms stay readable on tablets instead of stretching across the whole screen. */
fun Modifier.formWidth(): Modifier = widthIn(max = Layout.settingsMaxWidth).fillMaxWidth()

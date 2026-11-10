package com.mowazze3.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mowazze3.ui.theme.Radius
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space

private val HaloWidth = 3.dp

/**
 * HANDOFF section 4: 48dp tall, radius 10, 1.5dp outline, padding 14, label above 14/500, hint below 13/18.
 * Focus: 2dp primary with a 3dp primaryContainer halo. Error: 2dp error, and [error] replaces the hint.
 * Network names and passwords always read left to right.
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.medium)
    val borderColor = when {
        error != null -> cs.error
        focused -> cs.primary
        else -> cs.outline
    }
    val borderWidth = if (error != null || focused) Sizes.borderStrong else Sizes.border
    val halo = cs.primaryContainer

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.button)
                .drawBehind {
                    if (focused && error == null) {
                        val offset = HaloWidth.toPx() / 2
                        drawRoundRect(
                            color = halo,
                            topLeft = Offset(-offset, -offset),
                            size = Size(size.width + 2 * offset, size.height + 2 * offset),
                            cornerRadius = CornerRadius(Radius.medium.toPx() + offset),
                            style = Stroke(HaloWidth.toPx()),
                        )
                    }
                }
                .border(borderWidth, borderColor, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(cs.primary),
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = cs.onSurface, textDirection = TextDirection.Ltr),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = Space.m)
                    .semantics {
                        contentDescription = label
                        if (error != null) error(error)
                    },
            )
            if (trailing != null) Box { trailing() }
        }
        val message = error ?: hint
        if (message != null) {
            Text(
                message,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = if (error != null) cs.error else cs.onSurfaceVariant,
            )
        }
    }
}

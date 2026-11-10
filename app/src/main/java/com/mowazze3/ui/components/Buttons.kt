package com.mowazze3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mowazze3.R
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Radius
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space

enum class ButtonKind { Primary, Secondary, Text }

/**
 * HANDOFF section 4: 48dp tall, at least 80dp wide, 10dp corners, 24dp side padding, 16/500 text.
 * While [loading], a spinner replaces the label at the same width and taps are ignored.
 */
@Composable
fun MowazzeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val extra = MowazzeTheme.extra
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()

    val container: Color
    val content: Color
    when {
        !enabled -> {
            container = extra.disabledContainer
            content = extra.onDisabled
        }
        kind == ButtonKind.Primary -> {
            container = if (pressed) extra.primaryHover else cs.primary
            content = cs.onPrimary
        }
        else -> {
            container = if (pressed) cs.primaryContainer else Color.Transparent
            content = cs.primary
        }
    }
    val busy = stringResource(R.string.state_loading)

    Surface(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = if (kind == ButtonKind.Secondary && enabled) BorderStroke(Sizes.borderStrong, cs.primary) else null,
        interactionSource = interaction,
        modifier = modifier
            .widthIn(min = 80.dp)
            .heightIn(min = Sizes.button)
            .focusRing(focused, Radius.medium)
            .semantics { if (loading) stateDescription = busy },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.m)) {
            Text(
                text,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(if (loading) 0f else 1f),
            )
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.5.dp)
        }
    }
}

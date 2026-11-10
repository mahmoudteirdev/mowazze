package com.mowazze3.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mowazze3.R
import com.mowazze3.ui.theme.*

/**
 * Reference components from the handoff. The semantics are the important part: they are what the
 * accessibility checklist (step 11) expects. Visual details get finished in the phase that uses each one.
 */

/** Power button. 88dp circle (96 from 600dp), 3dp border. [starting] ignores taps and shows a spinner. A soft pulse while on, unless reduced motion is set. */
@Composable
fun PowerButton(on: Boolean, starting: Boolean, onClick: () -> Unit, actionLabel: String, modifier: Modifier = Modifier, size: Dp = Sizes.powerButton) {
    val cs = MaterialTheme.colorScheme
    val container = if (on) cs.primary else cs.background
    val content = if (on) cs.onPrimary else cs.onBackground
    val border = if (on) cs.primary else cs.outline
    val busy = stringResource(R.string.action_starting)
    val pulse = rememberInfiniteTransition(label = "power pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "power pulse scale",
    )
    val pulsing = on && !rememberReducedMotion()
    Surface(
        onClick = { if (!starting) onClick() },
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = BorderStroke(Sizes.borderPower, border),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val scale = if (pulsing) pulse.value else 1f
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = actionLabel; if (starting) stateDescription = busy },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (starting) CircularProgressIndicator(Modifier.size(36.dp), color = content, strokeWidth = 4.dp)
            else Text(actionLabel, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private const val PULSE_MILLIS = 2_400

/** True when "Remove animations" is on. The system setting for reduced motion. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** 56x44 touch area around a 52x30 track (Material Switch is 52x32; wrap it so the hit area is >= 44). */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Space.l, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)   // row handles the toggle
    }
}

private val OptionCorner = 12.dp

/** Level option. Selected: 2.5dp primary border + primaryContainer. Disabled: dashed border and explicit colors, no transparency. */
@Composable
fun LevelOption(selected: Boolean, enabled: Boolean, title: String, subtitle: String, onSelect: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val extra = MowazzeTheme.extra
    val bg = when { !enabled -> extra.disabledContainer; selected -> cs.primaryContainer; else -> cs.background }
    val stroke = when {
        !enabled -> null
        selected -> BorderStroke(Sizes.borderSelected, cs.primary)
        else -> BorderStroke(Sizes.border, cs.outline)
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        color = bg, shape = RoundedCornerShape(OptionCorner), border = stroke,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .focusRing(focused, OptionCorner)
            .selectable(
                selected = selected, enabled = enabled, role = Role.RadioButton,
                interactionSource = interaction, indication = LocalIndication.current, onClick = onSelect,
            )
            .then(if (enabled) Modifier else Modifier.dashedBorder(cs.outline, Sizes.border, OptionCorner)),
    ) {
        Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            RadioButton(
                selected = selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(disabledUnselectedColor = cs.onSurfaceVariant),
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
                // disabled text stays onSurfaceVariant (6.3:1): the reason it is unavailable is information
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

/**
 * Radius 8, padding 4/10, 12/700. Text only: it never relies on color alone.
 * [onSelectedCard] flips the fill, because a selected card already has the chip's primaryContainer background.
 */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, onSelectedCard: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (onSelectedCard) cs.background else cs.primaryContainer,
        contentColor = cs.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = Space.xs),
        )
    }
}

/** Wrap a list of LevelOption in this so TalkBack announces "n of 3" and radio behaviour. */
@Composable
fun LevelGroup(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

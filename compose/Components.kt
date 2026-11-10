package com.mowazze3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mowazze3.ui.theme.*

/**
 * REFERENCE sketches of the trickiest components (not compiled, no project to build against).
 * The semantics are the important part: they are what the accessibility checklist (step 11) expects.
 */

/** Power button. 88dp circle, 3dp border. [starting] ignores taps and shows a spinner. */
@Composable
fun PowerButton(on: Boolean, starting: Boolean, onClick: () -> Unit, actionLabel: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val container = if (on) cs.primary else cs.background
    val content = if (on) cs.onPrimary else cs.onBackground
    val border = if (on) cs.primary else cs.outline
    Surface(
        onClick = { if (!starting) onClick() },
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = BorderStroke(Sizes.borderPower, border),
        modifier = modifier
            .size(Sizes.powerButton)
            .semantics { contentDescription = actionLabel; if (starting) stateDescription = "busy" /* use a string resource */ },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (starting) CircularProgressIndicator(Modifier.size(36.dp), color = content, strokeWidth = 4.dp)
            else Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
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

/** Level option. Selected: 2.5dp primary border + primaryContainer. Disabled: dashed border in the web design. */
@Composable
fun LevelOption(selected: Boolean, enabled: Boolean, title: String, subtitle: String, onSelect: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val extra = MowazzeTheme.extra
    val bg = when { !enabled -> extra.disabledContainer; selected -> cs.primaryContainer; else -> cs.background }
    val stroke = if (selected) BorderStroke(Sizes.borderSelected, cs.primary) else BorderStroke(Sizes.border, if (enabled) cs.outline else cs.outlineVariant)
    Surface(
        color = bg, shape = MaterialTheme.shapes.medium, border = stroke,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
                // disabled text stays onSurfaceVariant (6.3:1): the reason it is unavailable is information
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

/** Wrap a list of LevelOption in this so TalkBack announces "n of 3" and radio behaviour. */
@Composable
fun LevelGroup(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

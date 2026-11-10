package com.mowazze3.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space

/**
 * Top radius 20, 44x5 handle, padding 12/16/24, scrolls if tall. ModalBottomSheet provides the focus
 * trap and the ways to close: tapping outside, back, and dragging down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MowazzeSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
        scrimColor = MowazzeTheme.extra.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = Space.m)
                    .size(Sizes.sheetHandleW, Sizes.sheetHandleH)
                    .background(cs.outline, RoundedCornerShape(3.dp)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Space.l, end = Space.l, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** One choice inside a sheet. Disabled options use explicit colors, never transparency. */
@Composable
fun SheetOption(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val titleColor = if (enabled) cs.onSurface else cs.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = titleColor, modifier = Modifier.size(Sizes.icon))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (badge != null) InfoChip(badge)
    }
}

package com.mowazze3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space
import kotlinx.coroutines.delay

class SnackbarMessage(val text: String, val actionLabel: String? = null, val onAction: () -> Unit = {})

/** One message at a time; a new one replaces the current one. */
class SnackbarController {
    var current by mutableStateOf<SnackbarMessage?>(null)
        private set

    fun show(message: SnackbarMessage) {
        current = message
    }

    fun show(text: String) = show(SnackbarMessage(text))

    fun dismiss(message: SnackbarMessage) {
        if (current === message) current = null
    }
}

val LocalSnackbar = staticCompositionLocalOf<SnackbarController> { error("No SnackbarController provided") }

private const val PLAIN_MILLIS = 3_600L
private const val WITH_ACTION_MILLIS = 7_000L

/** Radius 12, inverseSurface, at least 48dp tall, shown 3.6s (7s with an action). */
@Composable
fun MowazzeSnackbarHost(controller: SnackbarController, modifier: Modifier = Modifier) {
    val message = controller.current ?: return
    LaunchedEffect(message) {
        delay(if (message.actionLabel == null) PLAIN_MILLIS else WITH_ACTION_MILLIS)
        controller.dismiss(message)
    }
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.inverseSurface,
        contentColor = cs.inverseOnSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.l)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier
                .heightIn(min = Sizes.button)
                .padding(horizontal = Space.l, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val label = message.actionLabel
            if (label != null) {
                TextButton(
                    onClick = {
                        message.onAction()
                        controller.dismiss(message)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.inversePrimary),
                    modifier = Modifier.heightIn(min = Sizes.minTouch),
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

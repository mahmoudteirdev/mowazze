package com.mowazze3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mowazze3.R
import com.mowazze3.domain.Client
import com.mowazze3.domain.ClientKind
import com.mowazze3.domain.formatBytes
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Radius
import com.mowazze3.ui.theme.Sizes

private val RowShape = RoundedCornerShape(14.dp)

private val Client.icon
    get() = when (kind) {
        ClientKind.LAPTOP -> R.drawable.ic_device_laptop
        ClientKind.PHONE -> R.drawable.ic_device_phone
        ClientKind.TABLET -> R.drawable.ic_device_tablet
    }

/**
 * At least 72dp tall, radius 14, padding 10/14, 40dp icon. The name is one line and cut off if long;
 * the address and usage read left to right. With [onClick] the whole row is a button.
 */
@Composable
fun DeviceRow(
    client: Client,
    showUsage: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val background = if (client.blocked) MowazzeTheme.extra.disabledContainer else cs.surfaceVariant
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .heightIn(min = Sizes.deviceRowMin)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = cs.background, contentColor = cs.onSurface, shape = RoundedCornerShape(Radius.medium), modifier = Modifier.size(Sizes.deviceAvatar)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(client.icon), contentDescription = null, modifier = Modifier.size(Sizes.icon))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    client.name,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    client.ip,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, textDirection = TextDirection.Ltr),
                    color = cs.onSurfaceVariant,
                )
            }
            if (client.blocked) {
                // A text badge, not just a color. Blocked devices don't count toward usage.
                Surface(color = cs.errorContainer, contentColor = cs.onErrorContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        stringResource(R.string.device_blocked_chip),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else if (showUsage) {
                Text(
                    formatBytes(client.usageBytesToday),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, textDirection = TextDirection.Ltr),
                )
            }
            trailing?.invoke()
        }
    }
    val border = if (client.blocked) null else BorderStroke(Sizes.border, cs.outlineVariant)
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (client.blocked) Modifier.dashedBorder(cs.outline, Sizes.border, 14.dp) else Modifier)
    if (onClick != null) {
        Surface(onClick = onClick, color = background, contentColor = cs.onSurface, shape = RowShape, border = border, modifier = rowModifier) { content() }
    } else {
        Surface(color = background, contentColor = cs.onSurface, shape = RowShape, border = border, modifier = rowModifier) { content() }
    }
}

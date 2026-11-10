package com.mowazze3.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mowazze3.R
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.qrPayload
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.MowazzeSnackbarHost
import com.mowazze3.ui.components.QrCode
import com.mowazze3.ui.components.SnackbarController
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space

/**
 * The QR big enough to scan from across a table, with the screen at full brightness while it's open.
 * Brightness is set on this dialog's own window, so it goes back to normal as soon as it closes.
 */
@Composable
fun QrFullScreen(network: NetworkConfig, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
            onDispose { }
        }
        val context = LocalContext.current
        val cs = MaterialTheme.colorScheme
        val snackbar = remember { SnackbarController() }
        val copiedMessage = stringResource(R.string.toast_pw_copied)
        var showPassword by rememberSaveable { mutableStateOf(false) }

        Surface(Modifier.fillMaxSize(), color = cs.background, contentColor = cs.onBackground) {
            Box(Modifier.safeDrawingPadding()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.l, vertical = Space.l),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(Sizes.button)) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close), modifier = Modifier.size(Sizes.icon))
                        }
                        Text(stringResource(R.string.qr_full_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    Text(stringResource(R.string.qr_full_body), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            color = MowazzeTheme.extra.qrBackground,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(Sizes.border, cs.outlineVariant),
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            val description = stringResource(R.string.qr_description)
                            QrCode(
                                network.qrPayload(),
                                Modifier
                                    .padding(Space.l)
                                    .fillMaxSize()
                                    .semantics { contentDescription = description },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_brightness), contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.qr_brightness), fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant)
                    }
                    Credential(stringResource(R.string.network_name), network.ssid)
                    if (network.password.isNotEmpty()) {
                        Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(vertical = Space.s),
                                ) {
                                    Text(stringResource(R.string.password), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                    Text(
                                        if (showPassword) network.password else "•".repeat(network.password.length),
                                        style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, textDirection = TextDirection.Ltr),
                                    )
                                }
                                IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(Sizes.button)) {
                                    Icon(
                                        painterResource(R.drawable.ic_visibility),
                                        contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                        modifier = Modifier.size(Sizes.icon),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        context.copySensitive(network.password)
                                        snackbar.show(copiedMessage)
                                    },
                                    modifier = Modifier.size(Sizes.button),
                                ) {
                                    Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.copy_password), modifier = Modifier.size(Sizes.icon))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.heightIn(min = Space.s))
                    MowazzeButton(stringResource(R.string.done), onDismiss, Modifier.fillMaxWidth())
                }
                MowazzeSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun Credential(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text(value, style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, textDirection = TextDirection.Ltr))
        }
    }
}

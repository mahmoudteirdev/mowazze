package com.mowazze3.ui.home

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mowazze3.R
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.ui.components.ButtonKind
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.MowazzeSheet
import com.mowazze3.ui.components.MowazzeSnackbarHost
import com.mowazze3.ui.components.SnackbarController

/** The way to connect without scanning. */
@Composable
fun ManualConnectSheet(network: NetworkConfig, isNoRoot: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Lives inside the sheet: a message in the main app would sit behind the scrim.
    val snackbar = remember { SnackbarController() }
    val copiedMessage = stringResource(R.string.toast_pw_copied)
    val proxyCopiedMessage = stringResource(R.string.toast_proxy_copied)

    MowazzeSheet(stringResource(R.string.manual_title), onDismiss) {
        Text(
            stringResource(R.string.manual_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CredentialRow(R.string.network_name, network.ssid)
        CredentialRow(R.string.password, network.password)
        if (isNoRoot || network.ssid.contains("DIRECT-", ignoreCase = true)) {
            CredentialRow(R.string.proxy_settings, "192.168.49.1:8245")
            CredentialRow(R.string.proxy_pac, "http://192.168.49.1:8245/proxy.pac")
            Text(
                stringResource(R.string.proxy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MowazzeButton(
                stringResource(R.string.copy_proxy),
                {
                    context.copyToClipboard("Proxy", "192.168.49.1:8245")
                    snackbar.show(proxyCopiedMessage)
                },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary,
            )
        }
        MowazzeButton(
            stringResource(R.string.copy_password),
            {
                context.copySensitive(network.password)
                snackbar.show(copiedMessage)
            },
            Modifier.fillMaxWidth(),
        )
        MowazzeButton(stringResource(R.string.close), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            MowazzeSnackbarHost(snackbar)
        }
    }
}

@Composable
private fun CredentialRow(@StringRes label: Int, value: String) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceVariant, contentColor = cs.onSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, textDirection = TextDirection.Ltr))
            }
        }
    }
}

internal fun Context.copySensitive(text: String) {
    val clip = ClipData.newPlainText(getString(R.string.password), text)
    if (Build.VERSION.SDK_INT >= 33) {
        // Keeps Android's clipboard preview from showing it on screen.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

fun Context.copyToClipboard(label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

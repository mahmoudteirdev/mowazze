package com.mowazze3.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mowazze3.MowazzeApp
import com.mowazze3.R
import com.mowazze3.data.root.RootNetworkManager
import com.mowazze3.domain.AppSettings
import com.mowazze3.ui.components.ButtonKind
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.MowazzeSheet
import com.mowazze3.ui.components.SwitchRow
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun RootToolsSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MowazzeApp
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var isRooted by remember { mutableStateOf<Boolean?>(null) }
    var currentMacAddress by remember { mutableStateOf<String?>(null) }
    var inputMac by rememberSaveable { mutableStateOf("") }
    var isCheckingRoot by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val rootOk = RootNetworkManager.isRootAvailable()
        isRooted = rootOk
        val mac = RootNetworkManager.getMac("wlan0")
        currentMacAddress = mac
        if (inputMac.isEmpty()) {
            inputMac = mac.orEmpty()
        }
        isCheckingRoot = false
    }

    MowazzeSheet(
        title = stringResource(R.string.root_tools_title),
        onDismiss = onDismiss,
    ) {
        if (isCheckingRoot) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.l),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), color = cs.primary, strokeWidth = 3.dp)
            }
        } else if (isRooted == false) {
            Surface(
                color = MowazzeTheme.extra.disabledContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(Sizes.border, cs.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        stringResource(R.string.root_not_detected_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.error,
                    )
                    Text(
                        stringResource(R.string.root_not_detected_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        // Section 1: TTL Bypass
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(
                stringResource(R.string.ttl_bypass_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.ttl_bypass_desc),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            val ttlOptions = listOf(
                null to stringResource(R.string.ttl_default),
                64 to stringResource(R.string.ttl_64),
                65 to stringResource(R.string.ttl_65),
                128 to stringResource(R.string.ttl_128),
            )

            ttlOptions.forEach { (ttl, label) ->
                val isSelected = settings.ttlValue == ttl
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            scope.launch {
                                app.settingsStore.setTtlValue(ttl)
                                RootNetworkManager.applyTtl(ttl)
                                snackbar.show(context.getString(R.string.toast_ttl_applied))
                            }
                        }
                        .padding(horizontal = Space.s, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Icon(
                        painter = painterResource(if (isSelected) R.drawable.ic_check else R.drawable.ic_speed),
                        contentDescription = null,
                        tint = if (isSelected) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.icon),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) cs.primary else cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = Space.s), color = cs.outline.copy(alpha = 0.5f))

        // Section 2: VPN Tethering
        SwitchRow(
            title = stringResource(R.string.vpn_tethering_title),
            subtitle = stringResource(R.string.vpn_tethering_desc),
            checked = settings.vpnTethering,
            onChange = { enabled ->
                scope.launch {
                    app.settingsStore.setVpnTethering(enabled)
                    RootNetworkManager.applyVpnTethering(enabled)
                    snackbar.show(
                        context.getString(
                            if (enabled) R.string.toast_vpn_tethering_enabled else R.string.toast_vpn_tethering_disabled,
                        ),
                    )
                }
            },
        )

        HorizontalDivider(Modifier.padding(vertical = Space.s), color = cs.outline.copy(alpha = 0.5f))

        // Section 3: AdBlock DNS
        SwitchRow(
            title = stringResource(R.string.adblock_title),
            subtitle = stringResource(R.string.adblock_desc),
            checked = settings.adBlockDns,
            onChange = { enabled ->
                scope.launch {
                    app.settingsStore.setAdBlockDns(enabled)
                    RootNetworkManager.applyAdBlock(enabled)
                    snackbar.show(
                        context.getString(
                            if (enabled) R.string.toast_adblock_enabled else R.string.toast_adblock_disabled,
                        ),
                    )
                }
            },
        )

        HorizontalDivider(Modifier.padding(vertical = Space.s), color = cs.outline.copy(alpha = 0.5f))

        // Section 4: MAC Address Changer
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(
                stringResource(R.string.mac_changer_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.mac_changer_desc),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            currentMacAddress?.let { mac ->
                Text(
                    stringResource(R.string.current_mac, mac),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = cs.primary,
                )
            }

            OutlinedTextField(
                value = inputMac,
                onValueChange = { inputMac = it.trim() },
                label = { Text(stringResource(R.string.new_mac_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                MowazzeButton(
                    text = stringResource(R.string.generate_random_mac),
                    onClick = {
                        inputMac = RootNetworkManager.generateRandomMac()
                    },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Secondary,
                )
                MowazzeButton(
                    text = stringResource(R.string.apply_mac),
                    onClick = {
                        scope.launch {
                            val success = RootNetworkManager.changeMac("wlan0", inputMac)
                            if (success) {
                                app.settingsStore.setCustomMac(inputMac)
                                currentMacAddress = RootNetworkManager.getMac("wlan0") ?: inputMac
                                snackbar.show(context.getString(R.string.toast_mac_changed))
                            } else {
                                snackbar.show(context.getString(R.string.toast_mac_invalid))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (settings.customMac != null) {
                MowazzeButton(
                    text = stringResource(R.string.restore_original_mac),
                    onClick = {
                        scope.launch {
                            val success = RootNetworkManager.restoreOriginalMac("wlan0")
                            if (success) {
                                app.settingsStore.setCustomMac(null)
                                currentMacAddress = RootNetworkManager.getMac("wlan0")
                                inputMac = currentMacAddress.orEmpty()
                                snackbar.show(context.getString(R.string.toast_mac_restored))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Text,
                )
            }
        }

        MowazzeButton(
            text = stringResource(R.string.done),
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.m),
            kind = ButtonKind.Secondary,
        )
    }
}

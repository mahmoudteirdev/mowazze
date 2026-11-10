package com.mowazze3.ui.settings

import androidx.compose.ui.platform.LocalResources
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import com.mowazze3.AppLanguage
import com.mowazze3.AppLocale
import com.mowazze3.ui.components.LevelGroup
import com.mowazze3.ui.components.LevelOption
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowazze3.MowazzeApp
import com.mowazze3.R
import com.mowazze3.data.SaveResult
import com.mowazze3.domain.AppSettings
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.NetworkError
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.formatBytes
import com.mowazze3.domain.generatePassword
import com.mowazze3.ui.components.ButtonKind
import com.mowazze3.ui.components.LabeledField
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.MowazzeSheet
import com.mowazze3.ui.components.SwitchRow
import com.mowazze3.ui.components.formWidth
import com.mowazze3.ui.theme.Layout
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space
import kotlinx.coroutines.launch

/** [onDirtyChange] tells the shell whether leaving now would discard edits (HANDOFF section 5). */
@Composable
fun SettingsScreen(onChangeLevel: () -> Unit, onDirtyChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as MowazzeApp
    val settings by app.settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var showQuotaSheet by rememberSaveable { mutableStateOf(false) }
    var showRootToolsSheet by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(app) {
        app.shareRepository.refreshNetwork()
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.marginCompact, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.formWidth(), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            LanguageSection()
            when (app.shareRepository.networkControl) {
                NetworkControl.READ_ONLY -> SystemNetworkSection(settings.network?.ssid)
                NetworkControl.READ_WRITE -> settings.network?.let { NetworkSection(it, canApply = true, onDirtyChange) }
                // Blank fields to fill in, because the details can only come from the person.
                NetworkControl.NONE -> NetworkSection(settings.network ?: NetworkConfig("", ""), canApply = false, onDirtyChange)
            }
            PermissionsSection(settings.level, onChangeLevel, onOpenRootTools = { showRootToolsSheet = true })
            QuotaSection(settings.dataQuotaBytes, onOpenQuota = { showQuotaSheet = true })
            NotificationsSection(settings.notifyNewDevice, settings.alertQuota80)
            AboutSection()
        }
    }

    if (showQuotaSheet) {
        val snackbar = LocalSnackbar.current
        QuotaSheet(
            currentQuota = settings.dataQuotaBytes,
            onSelect = { bytes ->
                app.shareRepository.setDataQuota(bytes)
                snackbar.show(resources.getString(R.string.toast_quota_saved))
                showQuotaSheet = false
            },
            onDismiss = { showQuotaSheet = false },
        )
    }

    if (showRootToolsSheet) {
        RootToolsSheet(
            settings = settings,
            onDismiss = { showRootToolsSheet = false },
        )
    }
}

/** Device language by default. Picking one restarts the screen in it; sharing keeps running. */
@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val current = remember { AppLocale.language(context) }
    val currentLabel = when (current) {
        AppLanguage.SYSTEM -> stringResource(R.string.language_system_current, stringResource(R.string.language_current_name))
        AppLanguage.ARABIC -> stringResource(R.string.language_ar)
        AppLanguage.ENGLISH -> stringResource(R.string.language_en)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionTitle(R.string.language_section)
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.medium,
            color = cs.background,
            border = BorderStroke(Sizes.border, cs.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = Space.l, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.language_title), style = MaterialTheme.typography.bodyLarge)
                    Text(currentLabel, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (open) {
        MowazzeSheet(stringResource(R.string.language_title), onDismiss = { open = false }) {
            val pick = { language: AppLanguage ->
                open = false
                if (language != current) {
                    AppLocale.setLanguage(context, language)
                    activity?.recreate()
                }
            }
            LevelGroup {
                LevelOption(
                    selected = current == AppLanguage.SYSTEM, enabled = true,
                    title = stringResource(R.string.language_system), subtitle = stringResource(R.string.language_system_desc),
                    onSelect = { pick(AppLanguage.SYSTEM) },
                )
                // Each language's other name sits under it, so it can be found from inside the wrong one.
                LevelOption(
                    selected = current == AppLanguage.ARABIC, enabled = true,
                    title = stringResource(R.string.language_ar), subtitle = "Arabic",
                    onSelect = { pick(AppLanguage.ARABIC) },
                )
                LevelOption(
                    selected = current == AppLanguage.ENGLISH, enabled = true,
                    title = stringResource(R.string.language_en), subtitle = "الإنجليزية",
                    onSelect = { pick(AppLanguage.ENGLISH) },
                )
            }
            Text(stringResource(R.string.language_note), fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** The system owns the name and password here, so they're shown and not edited. */
@Composable
private fun SystemNetworkSection(ssid: String?) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionTitle(R.string.sec_network)
        if (ssid != null) {
            Column(Modifier.semantics(mergeDescendants = true) { }) {
                Text(stringResource(R.string.network_name), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(ssid, style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, textDirection = TextDirection.Ltr))
            }
        }
        Text(stringResource(R.string.network_from_system), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        MowazzeButton(stringResource(R.string.open_hotspot_settings), { context.openHotspotSettings() }, kind = ButtonKind.Secondary)
    }
}

@Composable
private fun NetworkSection(saved: NetworkConfig, canApply: Boolean, onDirtyChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MowazzeApp
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.toast_saved)
    val reconnectMessage = stringResource(R.string.toast_saved_active)

    // Keyed on the saved values, so a save or a discard brings the fields back in line with them.
    var ssid by rememberSaveable(saved.ssid) { mutableStateOf(saved.ssid) }
    var password by rememberSaveable(saved.password) { mutableStateOf(saved.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var errors by remember(ssid, password) { mutableStateOf(emptySet<NetworkError>()) }

    val dirty = ssid != saved.ssid || password != saved.password
    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionTitle(R.string.sec_network)
        if (!canApply) {
            Text(
                stringResource(R.string.network_describe),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MowazzeButton(
                stringResource(R.string.open_hotspot_settings),
                { context.openHotspotSettings() },
                kind = ButtonKind.Secondary,
            )
        }
        LabeledField(
            label = stringResource(R.string.network_name),
            value = ssid,
            onValueChange = { ssid = it },
            hint = stringResource(R.string.ssid_hint),
            error = when {
                NetworkError.SSID_EMPTY in errors -> stringResource(R.string.ssid_error)
                NetworkError.SSID_TOO_LONG in errors -> stringResource(R.string.ssid_error_long)
                else -> null
            },
        )
        LabeledField(
            label = stringResource(R.string.password),
            value = password,
            onValueChange = { password = it },
            hint = stringResource(R.string.pw_hint),
            error = when {
                NetworkError.PASSWORD_TOO_SHORT in errors -> stringResource(R.string.pw_error)
                NetworkError.PASSWORD_TOO_LONG in errors -> stringResource(R.string.pw_error_long)
                NetworkError.PASSWORD_NOT_ASCII in errors -> stringResource(R.string.pw_error_ascii)
                else -> null
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailing = {
                IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(Sizes.button)) {
                    Icon(
                        painterResource(R.drawable.ic_visibility),
                        contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
            },
        )
        if (canApply) {
            MowazzeButton(
                stringResource(R.string.generate_password),
                {
                    password = generatePassword()
                    showPassword = true
                },
                kind = ButtonKind.Text,
            )
        }
        MowazzeButton(
            stringResource(R.string.save_changes),
            {
                scope.launch {
                    when (val result = app.shareRepository.saveNetwork(NetworkConfig(ssid, password))) {
                        is SaveResult.Invalid -> errors = result.errors
                        SaveResult.Saved -> snackbar.show(savedMessage)
                        SaveResult.SavedDevicesMustReconnect -> snackbar.show(reconnectMessage)
                    }
                }
            },
            Modifier.fillMaxWidth(),
            enabled = dirty,
        )
    }
}

/** Screen names vary between makers, so this tries the known ones and falls back to the general screen. */
private fun Context.openHotspotSettings() {
    val candidates = listOf(
        "com.android.settings.TetherSettings",
        "com.android.settings.Settings\$TetherSettingsActivity",
        "com.android.settings.Settings\$WifiTetherSettingsActivity",
    ).map { Intent().setComponent(ComponentName("com.android.settings", it)) }
    val target = candidates.firstOrNull { it.resolveActivityInfo(packageManager, 0)?.exported == true }
    try {
        startActivity(target ?: Intent(Settings.ACTION_WIRELESS_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    } catch (_: SecurityException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@Composable
private fun PermissionsSection(
    level: PermissionLevel?,
    onChangeLevel: () -> Unit,
    onOpenRootTools: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionTitle(R.string.sec_permissions)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { },
            ) {
                Text(stringResource(R.string.current_level), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(
                    stringResource(
                        when (level) {
                            PermissionLevel.ROOT -> R.string.level_root
                            PermissionLevel.SHIZUKU -> R.string.level_shizuku
                            else -> R.string.level_noroot
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (level == PermissionLevel.SHIZUKU) R.string.level_desc_shizuku else R.string.level_desc_noroot),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            MowazzeButton(stringResource(R.string.change_level), onChangeLevel, kind = ButtonKind.Secondary)
        }
        // Active Root & Advanced Network Tools Sheet
        Surface(
            onClick = onOpenRootTools,
            color = cs.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(Sizes.border, cs.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .heightIn(min = Sizes.minTouch)
                    .padding(horizontal = Space.l, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(Sizes.icon),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { },
                ) {
                    Text(stringResource(R.string.root_tools_title), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    Text(stringResource(R.string.root_tools_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
    }
}

@Composable
private fun QuotaSection(dataQuotaBytes: Long?, onOpenQuota: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val quotaLabel = if (dataQuotaBytes != null && dataQuotaBytes > 0L) {
        formatBytes(dataQuotaBytes)
    } else {
        stringResource(R.string.quota_unlimited)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionTitle(R.string.quota_title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenQuota)
                .padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { },
            ) {
                Text(stringResource(R.string.quota_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.quota_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text(
                text = quotaLabel,
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
            )
        }
    }
}

@Composable
private fun QuotaSheet(
    currentQuota: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        1L * 1024 * 1024 * 1024 to stringResource(R.string.quota_1gb),
        2L * 1024 * 1024 * 1024 to stringResource(R.string.quota_2gb),
        5L * 1024 * 1024 * 1024 to stringResource(R.string.quota_5gb),
        10L * 1024 * 1024 * 1024 to stringResource(R.string.quota_10gb),
        null to stringResource(R.string.quota_unlimited),
    )
    MowazzeSheet(stringResource(R.string.quota_title), onDismiss) {
        options.forEach { (bytes, label) ->
            val isSelected = currentQuota == bytes
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable {
                        onSelect(bytes)
                    }
                    .padding(vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Icon(
                    painter = painterResource(if (isSelected) R.drawable.ic_check else R.drawable.ic_speed),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MowazzeButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
    }
}

@Composable
private fun NotificationsSection(notifyNewDevice: Boolean, alertQuota80: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as MowazzeApp
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var systemAllows by remember { mutableStateOf(true) }
    val alertOnMessage = stringResource(R.string.toast_alert_on)
    val alertOffMessage = stringResource(R.string.toast_alert_off)

    LifecycleResumeEffect(context) {
        systemAllows = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionTitle(R.string.sec_notifications)
        SwitchRow(
            title = stringResource(R.string.notif_new_device),
            subtitle = stringResource(R.string.notif_new_device_desc),
            checked = notifyNewDevice,
            onChange = { enabled -> scope.launch { app.settingsStore.setNotifyNewDevice(enabled) } },
        )
        SwitchRow(
            title = stringResource(R.string.notif_quota),
            subtitle = stringResource(R.string.notif_quota_desc),
            checked = alertQuota80,
            onChange = { enabled ->
                app.shareRepository.setAlertQuota80(enabled)
                snackbar.show(if (enabled) alertOnMessage else alertOffMessage)
            },
        )
        if (!systemAllows) {
            Text(
                stringResource(R.string.notif_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MowazzeButton(
                stringResource(R.string.open_settings),
                {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                kind = ButtonKind.Text,
            )
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SectionTitle(R.string.about)
        Text(
            stringResource(R.string.version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.font_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

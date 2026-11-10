package com.mowazze3.ui.devices

import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowazze3.MowazzeApp
import com.mowazze3.R
import com.mowazze3.domain.AppSettings
import com.mowazze3.domain.Client
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.ShareState
import com.mowazze3.domain.capabilities
import com.mowazze3.domain.canUpgradeForControl
import com.mowazze3.domain.connectedCount
import com.mowazze3.domain.formatBytes
import com.mowazze3.ui.components.ButtonKind
import com.mowazze3.ui.components.DeviceRow
import com.mowazze3.ui.components.EmptyState
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.MowazzeSheet
import com.mowazze3.ui.components.SheetOption
import com.mowazze3.ui.components.SnackbarMessage
import com.mowazze3.ui.theme.Layout
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space
import com.mowazze3.ui.theme.statNumberStyle

private const val DEMO_SPEED_LIMIT = "2 Mb/s"

private enum class NeedAction { BLOCK, LIMIT }

@Composable
fun DevicesScreen(onGoHome: () -> Unit, onOpenSettings: () -> Unit, onChangeLevel: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as MowazzeApp
    val repository = app.shareRepository
    val state by repository.state.collectAsStateWithLifecycle()
    val usageToday by repository.usageToday.collectAsStateWithLifecycle()
    val settings by app.settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val level = settings.level ?: PermissionLevel.NO_ROOT

    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var needAction by rememberSaveable { mutableStateOf<NeedAction?>(null) }
    var speedLimitFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showMaxDevicesSheet by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(repository) {
        repository.refreshDay()
        onPauseOrDispose { }
    }

    val current = state
    val clients = (current as? ShareState.On)?.clients.orEmpty()
    val snackbar = LocalSnackbar.current
    val selectedClient = clients.firstOrNull { it.id == optionsFor }

    // The device left while its options were open: close them and say why.
    LaunchedEffect(optionsFor, clients) {
        val id = optionsFor ?: return@LaunchedEffect
        if (clients.none { it.id == id }) {
            val leftName = selectedClient?.name.orEmpty()
            optionsFor = null
            needAction = null
            speedLimitFor = null
            if (current is ShareState.On && leftName.isNotEmpty()) {
                snackbar.show(resources.getString(R.string.toast_left, leftName))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.marginCompact, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Text(
            stringResource(R.string.devices_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (repository.tracksUsage) UsageCard(usageToday, settings.dataQuotaBytes)
        when {
            current !is ShareState.On -> Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                EmptyState(stringResource(R.string.empty_devices_title), stringResource(R.string.empty_devices_off))
                MowazzeButton(stringResource(R.string.go_home), onGoHome, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }
            clients.isEmpty() -> EmptyState(stringResource(R.string.empty_devices_title), stringResource(R.string.empty_devices_hint))
            else -> Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    stringResource(R.string.connected_now, current.connectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                clients.forEach { client ->
                    DeviceRow(
                        client,
                        showUsage = level.capabilities().perClientUsage,
                        trailing = {
                            IconButton(
                                onClick = {
                                    optionsFor = client.id
                                },
                                modifier = Modifier.size(Sizes.button),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.device_options, client.name),
                                    modifier = Modifier.size(Sizes.icon),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (selectedClient != null && needAction == null && speedLimitFor == null && !showMaxDevicesSheet) {
        DeviceOptionsSheet(
            client = selectedClient,
            level = level,
            onBlock = {
                val client = selectedClient
                optionsFor = null
                repository.blockClient(client.id)
                snackbar.show(
                    SnackbarMessage(
                        text = resources.getString(R.string.toast_blocked, client.name),
                        actionLabel = resources.getString(R.string.undo),
                        onAction = {
                            repository.unblockClient(client.id)
                            snackbar.show(resources.getString(R.string.toast_undone))
                        },
                    ),
                )
            },
            onUnblock = {
                val client = selectedClient
                optionsFor = null
                repository.unblockClient(client.id)
                snackbar.show(resources.getString(R.string.toast_unblocked, client.name))
            },
            onLimitSpeed = {
                speedLimitFor = selectedClient.id
            },
            onNeedPermission = { action ->
                needAction = action
            },
            onDismiss = { optionsFor = null },
        )
    }

    needAction?.let { action ->
        val clientName = selectedClient?.name.orEmpty()
        NeedPermissionSheet(
            action = action,
            deviceName = clientName,
            onEnableShizuku = {
                needAction = null
                optionsFor = null
                onChangeLevel()
            },
            onChangePassword = {
                needAction = null
                optionsFor = null
                onOpenSettings()
            },
            onMaxDevices = {
                needAction = null
                showMaxDevicesSheet = true
            },
            onDismiss = {
                needAction = null
                optionsFor = null
            },
        )
    }

    speedLimitFor?.let { clientId ->
        val client = clients.firstOrNull { it.id == clientId }
        val clientName = client?.name.orEmpty()
        SpeedLimitSheet(
            deviceName = clientName,
            currentLimit = client?.speedLimit,
            onSelect = { limit ->
                repository.setClientSpeedLimit(clientId, limit)
                val limitLabel = limit ?: resources.getString(R.string.speed_limit_unlimited)
                snackbar.show(resources.getString(R.string.toast_limited, clientName, limitLabel))
                speedLimitFor = null
                optionsFor = null
            },
            onDismiss = {
                speedLimitFor = null
                optionsFor = null
            },
        )
    }

    if (showMaxDevicesSheet) {
        MaxDevicesSheet(
            currentMax = settings.maxDevices,
            onSelect = { max ->
                repository.setMaxDevices(max)
                snackbar.show(resources.getString(R.string.toast_max_devices_saved))
                showMaxDevicesSheet = false
                optionsFor = null
            },
            onDismiss = {
                showMaxDevicesSheet = false
                optionsFor = null
            },
        )
    }
}

/** Usage and progress bar when quota is configured. */
@Composable
private fun UsageCard(usageToday: Long, dataQuotaBytes: Long?) {
    val cs = MaterialTheme.colorScheme
    val family = MaterialTheme.typography.titleMedium.fontFamily ?: return
    Surface(
        color = cs.surfaceVariant,
        contentColor = cs.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(18.dp)
                .semantics(mergeDescendants = true) { },
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(stringResource(R.string.usage_today), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (dataQuotaBytes != null && dataQuotaBytes > 0L) {
                val ratio = (usageToday.toFloat() / dataQuotaBytes).coerceIn(0f, 1f)
                Text(
                    stringResource(R.string.usage_of, formatBytes(usageToday), formatBytes(dataQuotaBytes)),
                    style = statNumberStyle(family).copy(textDirection = TextDirection.Ltr),
                )
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.progressHeight)
                        .clip(RoundedCornerShape(6.dp)),
                    color = if (ratio >= 0.8f) cs.error else cs.primary,
                    trackColor = cs.surface,
                )
            } else {
                Text(formatBytes(usageToday), style = statNumberStyle(family).copy(textDirection = TextDirection.Ltr))
            }
        }
    }
}

@Composable
private fun DeviceOptionsSheet(
    client: Client,
    level: PermissionLevel,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onLimitSpeed: () -> Unit,
    onNeedPermission: (NeedAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val capabilities = level.capabilities()
    val isNoRoot = level == PermissionLevel.NO_ROOT
    MowazzeSheet(stringResource(R.string.device_options, client.name), onDismiss) {
        if (client.blocked) {
            SheetOption(
                icon = R.drawable.ic_check,
                title = stringResource(R.string.unblock),
                description = stringResource(R.string.unblock_desc),
                onClick = onUnblock,
            )
        } else {
            SheetOption(
                icon = R.drawable.ic_block,
                title = stringResource(R.string.block_device),
                description = stringResource(if (!isNoRoot || capabilities.blockClient) R.string.block_desc else R.string.not_available_level),
                enabled = true,
                badge = if (isNoRoot) stringResource(R.string.needs_shizuku) else null,
                onClick = {
                    if (isNoRoot) onNeedPermission(NeedAction.BLOCK) else onBlock()
                },
            )
        }
        SheetOption(
            icon = R.drawable.ic_speed,
            title = stringResource(R.string.limit_speed),
            description = if (client.speedLimit != null) {
                stringResource(R.string.limit_desc, client.speedLimit)
            } else if (!isNoRoot || capabilities.limitSpeed) {
                stringResource(R.string.limit_desc, DEMO_SPEED_LIMIT)
            } else {
                stringResource(R.string.not_available_level)
            },
            enabled = true,
            badge = if (isNoRoot) stringResource(R.string.needs_shizuku) else null,
            onClick = {
                if (isNoRoot) onNeedPermission(NeedAction.LIMIT) else onLimitSpeed()
            },
        )
        MowazzeButton(stringResource(R.string.close), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
    }
}

@Composable
private fun NeedPermissionSheet(
    action: NeedAction,
    deviceName: String,
    onEnableShizuku: () -> Unit,
    onChangePassword: () -> Unit,
    onMaxDevices: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (action) {
        NeedAction.BLOCK -> stringResource(R.string.need_title_block, deviceName)
        NeedAction.LIMIT -> stringResource(R.string.need_title_limit, deviceName)
    }
    MowazzeSheet(title, onDismiss) {
        Text(
            stringResource(R.string.need_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SheetOption(
            icon = R.drawable.ic_bolt,
            title = stringResource(R.string.need_enable_shizuku),
            description = stringResource(R.string.need_enable_desc),
            onClick = onEnableShizuku,
        )
        SheetOption(
            icon = R.drawable.ic_lock,
            title = stringResource(R.string.need_change_pw),
            description = stringResource(R.string.need_change_pw_desc),
            onClick = onChangePassword,
        )
        SheetOption(
            icon = R.drawable.ic_person_add,
            title = stringResource(R.string.need_max_devices),
            description = stringResource(R.string.need_max_desc),
            enabled = true,
            onClick = onMaxDevices,
        )
        MowazzeButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
    }
}

@Composable
private fun SpeedLimitSheet(
    deviceName: String,
    currentLimit: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "1 Mb/s" to "1 Mb/s",
        "2 Mb/s" to "2 Mb/s",
        "5 Mb/s" to "5 Mb/s",
        "10 Mb/s" to "10 Mb/s",
        null to stringResource(R.string.speed_limit_unlimited),
    )
    MowazzeSheet(stringResource(R.string.limit_speed_for, deviceName), onDismiss) {
        options.forEach { (limit, label) ->
            val isSelected = currentLimit == limit
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable {
                        onSelect(limit)
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
private fun MaxDevicesSheet(
    currentMax: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        1 to stringResource(R.string.max_devices_1),
        2 to stringResource(R.string.max_devices_2),
        3 to stringResource(R.string.max_devices_3),
        5 to stringResource(R.string.max_devices_5),
        8 to stringResource(R.string.max_devices_8),
        10 to stringResource(R.string.max_devices_10),
        null to stringResource(R.string.max_devices_unlimited),
    )
    MowazzeSheet(stringResource(R.string.need_max_devices), onDismiss) {
        options.forEach { (count, label) ->
            val isSelected = currentMax == count
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable {
                        onSelect(count)
                    }
                    .padding(vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Icon(
                    painter = painterResource(if (isSelected) R.drawable.ic_check else R.drawable.ic_person_add),
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

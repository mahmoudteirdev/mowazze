package com.mowazze3.ui.home

import com.mowazze3.domain.offersWifiNotSharedFix
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowazze3.MowazzeApp
import com.mowazze3.R
import com.mowazze3.domain.AppSettings
import com.mowazze3.domain.FailReason
import com.mowazze3.domain.HomeStats
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.ShareState
import com.mowazze3.domain.capabilities
import com.mowazze3.domain.qrPayload
import com.mowazze3.ui.components.ButtonKind
import com.mowazze3.ui.components.DeviceRow
import com.mowazze3.ui.components.EmptyState
import com.mowazze3.ui.components.InfoChip
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.PowerButton
import com.mowazze3.ui.components.QrCode
import com.mowazze3.ui.theme.Breakpoints
import com.mowazze3.ui.theme.Layout
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Radius
import com.mowazze3.ui.theme.Sizes
import com.mowazze3.ui.theme.Space
import com.mowazze3.ui.theme.statNumberStyle

private const val MAX_DEVICES_ON_HOME = 3

@Composable
fun HomeScreen(onOpenDevices: () -> Unit, onOpenSettings: () -> Unit, onChangeLevel: () -> Unit) {
    val app = LocalContext.current.applicationContext as MowazzeApp
    val repository = app.shareRepository
    val state by repository.state.collectAsStateWithLifecycle()
    val usageToday by repository.usageToday.collectAsStateWithLifecycle()
    val settings by app.settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The QR shows before sharing starts, so the network is needed from the start.
    LifecycleResumeEffect(repository) {
        repository.refreshDay()
        repository.refreshNetwork()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Sharing works either way; a refusal only means no system notifications.
        repository.start()
    }
    val start = {
        val askFirst = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (askFirst) notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else repository.start()
    }

    val current = state
    if (current is ShareState.Failed) {
        StartFailedContent(
            reason = current.reason,
            onRetry = start,
            onDismiss = repository::stop,
            onChangeLevel = onChangeLevel,
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large = maxWidth >= Breakpoints.medium
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (large) Layout.marginMedium else Layout.marginCompact, vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            TopRow(settings.level)
            StatusCard(
                state = current,
                network = settings.network,
                large = large,
                onPower = { if (current is ShareState.On) repository.stop() else start() },
                onManual = { showManual = true },
                onOpenQr = { showQr = true },
                onOpenSettings = onOpenSettings,
                canTypeNetwork = repository.networkControl == NetworkControl.NONE,
            )
            if (settings.level == PermissionLevel.NO_ROOT) {
                val snackbar = LocalSnackbar.current
                val proxyCopiedMsg = stringResource(R.string.toast_proxy_copied)
                ProxyInstructionsCard(
                    onCopyProxy = {
                        context.copyToClipboard("Proxy", "192.168.49.1:8245")
                        snackbar.show(proxyCopiedMsg)
                    },
                )
            }
            val phoneOnWifi = remember(current) { context.isOnWifi() }
            if (offersWifiNotSharedFix(settings.level, current, phoneOnWifi)) {
                WifiNotSharedCard(
                    onUseWifiDirect = {
                        repository.stop()
                        scope.launch {
                            app.settingsStore.setLevel(PermissionLevel.NO_ROOT)
                            start()
                        }
                    },
                    onOpenWifi = { context.openWifiPanel() },
                )
            } else if (current is ShareState.On && !current.hasInternet) {
                NoInternetBanner()
            }
            StatsRow(HomeStats.of(current, usageToday, repository.tracksUsage))
            DevicesSection(current, settings.level?.capabilities()?.perClientUsage == true, onOpenDevices)
        }
    }

    val network = settings.network
    if (showQr && network != null) QrFullScreen(network, onDismiss = { showQr = false })
    if (showManual && network != null) {
        ManualConnectSheet(
            network = network,
            isNoRoot = settings.level == PermissionLevel.NO_ROOT,
            onDismiss = { showManual = false },
        )
    }
}

@Composable
private fun TopRow(level: PermissionLevel?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (level != null) {
            val name = when (level) {
                PermissionLevel.ROOT -> R.string.level_root
                PermissionLevel.SHIZUKU -> R.string.level_shizuku
                PermissionLevel.NO_ROOT -> R.string.level_noroot
            }
            InfoChip(stringResource(R.string.level_chip, stringResource(name)))
        }
    }
}

private class StatusCopy(@param:StringRes val title: Int, @param:StringRes val hint: Int)

private fun statusCopy(state: ShareState) = when {
    state == ShareState.Starting -> StatusCopy(R.string.share_starting, R.string.hint_starting)
    state is ShareState.On && !state.hasInternet -> StatusCopy(R.string.share_on_no_net, R.string.hint_no_net)
    state is ShareState.On -> StatusCopy(R.string.share_on, R.string.hint_on)
    else -> StatusCopy(R.string.share_off, R.string.hint_off)
}

/** Radius 16, padding 18, gap 16, 1.5dp border that turns primary while sharing. */
@Composable
private fun StatusCard(
    state: ShareState,
    network: NetworkConfig?,
    large: Boolean,
    onPower: () -> Unit,
    onManual: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenSettings: () -> Unit,
    canTypeNetwork: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val on = state is ShareState.On
    val copy = statusCopy(state)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceVariant,
        contentColor = cs.onSurface,
        border = BorderStroke(Sizes.border, if (on) cs.primary else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                PowerButton(
                    on = on,
                    starting = state == ShareState.Starting,
                    onClick = onPower,
                    actionLabel = stringResource(
                        when {
                            on -> R.string.action_stop
                            state == ShareState.Starting -> R.string.action_starting
                            else -> R.string.action_start
                        },
                    ),
                    size = if (large) Sizes.powerButtonLarge else Sizes.powerButton,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(stringResource(copy.title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(copy.hint), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            if (network == null) {
                // No QR at all until the details are real: a made-up one wouldn't connect to anything.
                if (canTypeNetwork) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(stringResource(R.string.network_missing), style = MaterialTheme.typography.bodyMedium)
                        MowazzeButton(stringResource(R.string.network_enter), onOpenSettings, kind = ButtonKind.Secondary)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    TappableQr(network, dimmed = !on, size = if (large) Sizes.qrLarge else Sizes.qr, onClick = onOpenQr)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(stringResource(R.string.qr_tap_hint), fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant)
                        MowazzeButton(stringResource(R.string.connect_manual), onManual, kind = ButtonKind.Secondary)
                    }
                }
            }
        }
    }
}

/** The QR is a button: a tap opens it full screen. The corner badge says so without words. */
@Composable
private fun TappableQr(network: NetworkConfig, dimmed: Boolean, size: Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val description = stringResource(R.string.qr_description)
    val enlarge = stringResource(R.string.qr_enlarge)
    Box(Modifier.size(size)) {
        Surface(
            onClick = onClick,
            color = MowazzeTheme.extra.qrBackground,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    onClick(label = enlarge) { onClick(); true }
                },
        ) {
            QrCode(
                network.qrPayload(),
                Modifier
                    .padding(6.dp)
                    .fillMaxSize()
                    .alpha(if (dimmed) 0.35f else 1f),
            )
        }
        Surface(
            color = cs.primary,
            contentColor = cs.onPrimary,
            shape = CircleShape,
            border = BorderStroke(2.dp, cs.surfaceVariant),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 8.dp)
                .size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_expand), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun Context.isOnWifi(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java)
    return manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}

private fun Context.openWifiPanel() {
    val intent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}

/** Design board WifiNoNet: the warning, then the two ways out, each saying what it costs. */
@Composable
private fun WifiNotSharedCard(onUseWifiDirect: () -> Unit, onOpenWifi: () -> Unit) {
    val extra = MowazzeTheme.extra
    val cs = MaterialTheme.colorScheme
    Surface(
        color = extra.warnContainer,
        contentColor = extra.onWarnContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(Sizes.borderStrong, extra.warn),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = extra.warn, modifier = Modifier.size(Sizes.icon))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        stringResource(R.string.wifi_not_shared_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.wifi_not_shared_body), fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
            listOf(
                Triple(R.string.wifi_direct_option, R.string.wifi_direct_desc, true),
                Triple(R.string.wifi_off_option, R.string.wifi_off_desc, false),
            ).forEach { (title, description, primary) ->
                Surface(color = cs.background, contentColor = cs.onSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(description), fontSize = 13.sp, lineHeight = 20.sp, color = cs.onSurfaceVariant)
                        MowazzeButton(
                            stringResource(if (primary) R.string.wifi_direct_switch else R.string.open_wifi_settings),
                            if (primary) onUseWifiDirect else onOpenWifi,
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Space.s),
                            kind = if (primary) ButtonKind.Primary else ButtonKind.Secondary,
                        )
                    }
                }
            }
        }
    }
}

/** Border 2 warn, radius 14. Icon and text together, so it doesn't rely on color. */
@Composable
private fun NoInternetBanner() {
    val extra = MowazzeTheme.extra
    Surface(
        color = extra.warnContainer,
        contentColor = extra.onWarnContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(Sizes.borderStrong, extra.warn),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = extra.warn, modifier = Modifier.size(Sizes.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.no_net_title), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
                Text(stringResource(R.string.no_net_body), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun StatsRow(stats: HomeStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        StatTile(stats.devices, R.string.stat_devices, Modifier.weight(1f))
        StatTile(stats.usage, R.string.stat_usage, Modifier.weight(1f))
        StatTile(stats.speed, R.string.stat_speed, Modifier.weight(1f))
    }
}

/** Radius 12, padding 12, value 20/28 semibold, label 12. Reads the value first, then the label. */
@Composable
private fun StatTile(value: String, @StringRes label: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val family = MaterialTheme.typography.titleMedium.fontFamily ?: return
    Surface(color = cs.surfaceVariant, contentColor = cs.onSurface, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(
            Modifier
                .padding(12.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            Text(value, style = statNumberStyle(family).copy(textDirection = TextDirection.Ltr))
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun DevicesSection(state: ShareState, showUsage: Boolean, onOpenDevices: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clients = (state as? ShareState.On)?.clients.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.connected_devices),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (clients.isNotEmpty()) {
                MowazzeButton(stringResource(R.string.view_all), onOpenDevices, kind = ButtonKind.Text)
            }
        }
        if (clients.isEmpty()) {
            EmptyState(stringResource(R.string.empty_devices_title), stringResource(R.string.empty_devices_hint))
        } else {
            clients.take(MAX_DEVICES_ON_HOME).forEach { DeviceRow(it, showUsage, onClick = onOpenDevices) }
        }
    }
}

/** Replaces the home content while [ShareState.Failed]. Only the Wi-Fi reason has its own designed text. */
@Composable
private fun StartFailedContent(reason: FailReason, onRetry: () -> Unit, onDismiss: () -> Unit, onChangeLevel: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.marginCompact, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ErrorCard(reason)
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (reason == FailReason.WIFI_CONNECTED) {
                MowazzeButton(
                    stringResource(R.string.open_wifi_settings),
                    { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    Modifier.fillMaxWidth(),
                )
                MowazzeButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            } else if (reason == FailReason.NEEDS_WRITE_SETTINGS) {
                MowazzeButton(
                    stringResource(R.string.grant_permission),
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                    Modifier.fillMaxWidth(),
                )
                MowazzeButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            } else {
                MowazzeButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
            }
            MowazzeButton(stringResource(R.string.change_level), onChangeLevel, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
        }
        OtherReasons(reason)
    }
}

private class FailCopy(@param:StringRes val reason: Int, @param:StringRes val body: Int)

private fun failCopy(reason: FailReason) = when (reason) {
    FailReason.WIFI_CONNECTED -> FailCopy(R.string.fail_reason_wifi, R.string.fail_body_wifi)
    FailReason.CARRIER_BLOCKED -> FailCopy(R.string.fail_reason_carrier, R.string.reason_carrier)
    FailReason.UNSUPPORTED -> FailCopy(R.string.fail_reason_unsupported, R.string.reason_unsupported)
    FailReason.SHIZUKU_EXPIRED -> FailCopy(R.string.fail_reason_shizuku, R.string.reason_shizuku_expired)
    FailReason.TIMEOUT -> FailCopy(R.string.fail_reason_timeout, R.string.fail_body_generic)
    FailReason.SYSTEM_STOPPED -> FailCopy(R.string.fail_reason_system, R.string.fail_body_generic)
    FailReason.NEEDS_WRITE_SETTINGS -> FailCopy(R.string.fail_reason_permission, R.string.fail_body_permission)
    FailReason.UNKNOWN -> FailCopy(R.string.fail_reason_unknown, R.string.fail_body_generic)
}

/** Border 2 error, errorContainer, radius 16, padding 18, 72dp icon. Announced right away. */
@Composable
private fun ErrorCard(reason: FailReason) {
    val cs = MaterialTheme.colorScheme
    val copy = failCopy(reason)
    Surface(
        color = cs.errorContainer,
        contentColor = cs.onErrorContainer,
        shape = RoundedCornerShape(Radius.large),
        border = BorderStroke(Sizes.borderStrong, cs.error),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Icon(painterResource(R.drawable.ic_alert_circle), contentDescription = null, tint = cs.error, modifier = Modifier.size(72.dp))
            Text(
                stringResource(R.string.fail_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Text(stringResource(copy.reason), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Text(stringResource(copy.body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OtherReasons(current: FailReason) {
    val cs = MaterialTheme.colorScheme
    val others = listOf(
        FailReason.CARRIER_BLOCKED to R.string.reason_carrier,
        FailReason.UNSUPPORTED to R.string.reason_unsupported,
        FailReason.SHIZUKU_EXPIRED to R.string.reason_shizuku_expired,
    ).filter { it.first != current }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            stringResource(R.string.other_reasons),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .heightIn(min = 24.dp)
                .semantics { heading() },
        )
        others.forEach { (_, text) ->
            Text("• " + stringResource(text), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProxyInstructionsCard(onCopyProxy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceVariant,
        contentColor = cs.onSurface,
        border = BorderStroke(Sizes.border, cs.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bolt),
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(Sizes.icon),
                )
                Text(
                    text = stringResource(R.string.proxy_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.proxy_card_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        stringResource(R.string.proxy_step_1),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurface,
                    )
                    Text(
                        stringResource(R.string.proxy_step_2),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurface,
                    )
                    Text(
                        stringResource(R.string.proxy_step_3),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.proxy_host) + ": 192.168.49.1",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.proxy_port) + ": 8245",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                MowazzeButton(
                    stringResource(R.string.copy_proxy),
                    onClick = onCopyProxy,
                    kind = ButtonKind.Secondary,
                )
            }
        }
    }
}

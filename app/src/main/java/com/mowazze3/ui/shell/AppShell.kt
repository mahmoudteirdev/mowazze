package com.mowazze3.ui.shell

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mowazze3.MowazzeApp
import com.mowazze3.R
import com.mowazze3.data.ShareEvent
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.MowazzeSnackbarHost
import com.mowazze3.ui.devices.DevicesScreen
import com.mowazze3.ui.home.HomeScreen
import com.mowazze3.ui.settings.SettingsScreen
import com.mowazze3.ui.theme.Breakpoints
import com.mowazze3.ui.theme.Layout
import com.mowazze3.ui.theme.Sizes
import kotlinx.coroutines.launch

private enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_nav_home),
    DEVICES(R.string.nav_devices, R.drawable.ic_nav_devices),
    SETTINGS(R.string.nav_settings, R.drawable.ic_nav_settings),
}

/**
 * Bottom bar under 600dp, side rail from 600dp. Each tab keeps its own state when you switch away.
 * Back from another tab returns to Home; back from Home leaves the app.
 */
@Composable
fun AppShell() {
    var current by rememberSaveable { mutableStateOf(Tab.HOME) }
    val tabStates = rememberSaveableStateHolder()
    var settingsDirty by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val unsavedMessage = stringResource(R.string.toast_unsaved)

    // Leaving Settings with unsaved network edits drops them and says so (HANDOFF section 5).
    val select: (Tab) -> Unit = { next ->
        if (current == Tab.SETTINGS && next != Tab.SETTINGS && settingsDirty) {
            tabStates.removeState(Tab.SETTINGS.name)
            settingsDirty = false
            snackbar.show(unsavedMessage)
        }
        current = next
    }
    BackHandler(enabled = current != Tab.HOME) { select(Tab.HOME) }

    val app = LocalContext.current.applicationContext as MowazzeApp
    val scope = rememberCoroutineScope()
    AnnounceSharingEvents(app)
    val changeLevel = {
        app.shareRepository.stop()
        scope.launch { app.settingsStore.clearLevel() }
        Unit
    }

    val homeContent: @Composable () -> Unit = {
        HomeScreen(
            onOpenDevices = { select(Tab.DEVICES) },
            onOpenSettings = { select(Tab.SETTINGS) },
            onChangeLevel = changeLevel,
        )
    }

    val devicesContent: @Composable () -> Unit = {
        DevicesScreen(
            onGoHome = { select(Tab.HOME) },
            onOpenSettings = { select(Tab.SETTINGS) },
            onChangeLevel = changeLevel,
        )
    }

    val settingsContent: @Composable () -> Unit = {
        SettingsScreen(onChangeLevel = changeLevel, onDirtyChange = { settingsDirty = it })
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints {
            val isExpanded = maxWidth >= Breakpoints.expanded
            val isMedium = maxWidth >= Breakpoints.medium

            if (isMedium) {
                Row(Modifier.fillMaxSize()) {
                    NavRail(current, select)
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
                    ) {
                        if (isExpanded && current != Tab.SETTINGS) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.width(Layout.homePaneWidth).fillMaxHeight()) {
                                    tabStates.SaveableStateProvider(Tab.HOME.name) {
                                        homeContent()
                                    }
                                }
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    tabStates.SaveableStateProvider(Tab.DEVICES.name) {
                                        devicesContent()
                                    }
                                }
                            }
                        } else {
                            tabStates.SaveableStateProvider(current.name) {
                                when (current) {
                                    Tab.HOME -> homeContent()
                                    Tab.DEVICES -> devicesContent()
                                    Tab.SETTINGS -> settingsContent()
                                }
                            }
                        }
                        MowazzeSnackbarHost(LocalSnackbar.current, Modifier.align(Alignment.BottomCenter))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    ) {
                        tabStates.SaveableStateProvider(current.name) {
                            when (current) {
                                Tab.HOME -> homeContent()
                                Tab.DEVICES -> devicesContent()
                                Tab.SETTINGS -> settingsContent()
                            }
                        }
                        MowazzeSnackbarHost(LocalSnackbar.current, Modifier.align(Alignment.BottomCenter))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    BottomBar(current, select)
                }
            }
        }
    }
}

/** Short in-app messages, on whichever tab is open. */
@Composable
private fun AnnounceSharingEvents(app: MowazzeApp) {
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current
    LaunchedEffect(app) {
        app.shareRepository.events.collect { event ->
            when (event) {
                ShareEvent.SharingStarted -> snackbar.show(resources.getString(R.string.toast_sharing_on))
                is ShareEvent.ClientJoined -> snackbar.show(resources.getString(R.string.toast_joined, event.client.name))
                is ShareEvent.ClientLeft -> Unit
                ShareEvent.QuotaAlert80 -> snackbar.show(resources.getString(R.string.alert_80))
            }
        }
    }
}

/** 3 items, 56dp tall, 3dp indicator on top, 22dp icon, 12sp label. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .selectableGroup(),
    ) {
        Tab.entries.forEach { tab ->
            BarItem(tab, selected = tab == current, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun RowScope.BarItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = Sizes.navBarItem)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 40.dp, height = 3.dp)
                    .background(color),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.icon))
            TabLabel(tab, selected, color)
        }
    }
}

/** 96dp wide; 80x64 items; 56x32 capsule behind the active icon. */
@Composable
private fun NavRail(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(Sizes.railWidth)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
            .padding(top = 8.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            RailItem(tab, selected = tab == current, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun RailItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = if (selected) cs.primary else cs.onSurfaceVariant
    Column(
        Modifier
            .width(Sizes.railItemW)
            .heightIn(min = Sizes.railItemH)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .then(if (selected) Modifier.background(cs.primaryContainer, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(Sizes.icon))
        }
        Spacer(Modifier.height(2.dp))
        TabLabel(tab, selected, color)
    }
}

@Composable
private fun TabLabel(tab: Tab, selected: Boolean, color: androidx.compose.ui.graphics.Color) {
    Text(
        stringResource(tab.label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

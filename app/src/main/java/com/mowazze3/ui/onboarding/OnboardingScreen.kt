package com.mowazze3.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mowazze3.R
import com.mowazze3.data.SHIZUKU_PACKAGE
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.ShizukuStatus
import com.mowazze3.ui.components.ButtonKind
import com.mowazze3.ui.components.InfoChip
import com.mowazze3.ui.components.LevelGroup
import com.mowazze3.ui.components.LevelOption
import com.mowazze3.ui.components.LocalSnackbar
import com.mowazze3.ui.components.MowazzeButton
import com.mowazze3.ui.components.formWidth
import com.mowazze3.ui.theme.Layout
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Space

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel()) {
    val model = viewModel.model
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val rootMessage = stringResource(R.string.toast_root_set)
    val linkedMessage = stringResource(R.string.toast_linked)
    val noRootMessage = stringResource(R.string.toast_noroot_set)

    LifecycleResumeEffect(model) {
        model.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(model) {
        model.resetIfFinished()
        model.finished.collect { level ->
            val msg = when (level) {
                PermissionLevel.ROOT -> rootMessage
                PermissionLevel.SHIZUKU -> linkedMessage
                PermissionLevel.NO_ROOT -> noRootMessage
            }
            snackbar.show(msg)
        }
    }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { model.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(onStart = model::startScan)
            OnboardingStep.SCANNING -> ScanningStep()
            OnboardingStep.LEVELS -> LevelsStep(state, model)
            OnboardingStep.LINK -> LinkStep(state, model)
        }
    }
}

/** Scrolling content with the main buttons pinned below it, so they stay visible at 200% text size. */
@Composable
private fun StepLayout(
    bottom: @Composable ColumnScope.() -> Unit,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Layout.marginCompact, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = if (centered) Alignment.Center else Alignment.TopCenter) {
            Column(
                Modifier
                    .formWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(Space.l),
                content = content,
            )
        }
        Column(
            Modifier
                .formWidth()
                .padding(top = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    StepLayout(
        centered = true,
        bottom = {
            MowazzeButton(stringResource(R.string.welcome_cta), onStart, Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.welcome_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
    ) {
        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(96.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScanningStep() {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Layout.marginCompact),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.l, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(Modifier.size(48.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.scan_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LevelsStep(state: OnboardingState, model: OnboardingModel) {
    val context = LocalContext.current
    val rootSelected = state.selected == PermissionLevel.ROOT
    val shizukuSelected = state.selected == PermissionLevel.SHIZUKU
    val oneStep = rootSelected || !shizukuSelected || state.shizuku == ShizukuStatus.READY

    StepLayout(
        bottom = {
            Text(
                stringResource(if (oneStep) R.string.step_one else R.string.step_two),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MowazzeButton(
                stringResource(
                    when {
                        rootSelected -> R.string.cta_start_root
                        shizukuSelected -> R.string.cta_start_shizuku
                        else -> R.string.cta_start_noroot
                    },
                ),
                model::confirmLevel,
                Modifier.fillMaxWidth(),
            )
            MowazzeButton(stringResource(R.string.back), { model.back() }, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                stringResource(R.string.levels_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.levels_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LevelGroup {
            LevelOption(
                selected = rootSelected,
                enabled = state.rootFound,
                title = stringResource(R.string.level_root),
                subtitle = stringResource(if (state.rootFound) R.string.level_root_available else R.string.level_root_unavailable),
                onSelect = { if (state.rootFound) model.select(PermissionLevel.ROOT) },
                trailing = if (state.bestLevel == PermissionLevel.ROOT && state.rootFound) {
                    { InfoChip(stringResource(R.string.level_best), onSelectedCard = rootSelected) }
                } else null,
            )
            LevelOption(
                selected = shizukuSelected,
                enabled = state.shizuku != ShizukuStatus.NOT_INSTALLED,
                title = stringResource(R.string.level_shizuku),
                subtitle = stringResource(
                    when (state.shizuku) {
                        ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_not_installed
                        ShizukuStatus.NOT_RUNNING -> R.string.shizuku_not_running
                        ShizukuStatus.NEEDS_PERMISSION, ShizukuStatus.READY -> R.string.level_shizuku_sub
                    },
                ),
                onSelect = { model.select(PermissionLevel.SHIZUKU) },
                trailing = {
                    if (state.shizuku == ShizukuStatus.NOT_INSTALLED) {
                        MowazzeButton(stringResource(R.string.install), { context.openShizukuInstall() }, kind = ButtonKind.Secondary)
                    } else if (state.bestLevel == PermissionLevel.SHIZUKU) {
                        InfoChip(stringResource(R.string.level_best), onSelectedCard = shizukuSelected)
                    }
                },
            )
            LevelOption(
                selected = state.selected == PermissionLevel.NO_ROOT,
                enabled = state.noRootSupported,
                title = stringResource(R.string.level_noroot),
                subtitle = stringResource(
                    if (state.noRootSupported) R.string.level_noroot_sub else R.string.level_noroot_unsupported,
                ),
                onSelect = { if (state.noRootSupported) model.select(PermissionLevel.NO_ROOT) },
                trailing = if (state.bestLevel == PermissionLevel.NO_ROOT && state.noRootSupported) {
                    { InfoChip(stringResource(R.string.level_best), onSelectedCard = state.selected == PermissionLevel.NO_ROOT) }
                } else {
                    null
                },
            )
        }
        when {
            rootSelected -> {
                InfoCard(
                    R.string.info_title_root,
                    listOf(
                        InfoLine(R.string.info_root_1),
                        InfoLine(R.string.info_root_2),
                        InfoLine(R.string.info_root_3),
                    ),
                )
            }
            shizukuSelected -> {
                InfoCard(
                    R.string.info_title_shizuku,
                    listOf(
                        InfoLine(R.string.info_shizuku_1),
                        InfoLine(R.string.info_shizuku_2),
                        InfoLine(R.string.info_shizuku_3, caveat = true),
                    ),
                )
            }
            else -> {
                InfoCard(
                    R.string.info_title_noroot,
                    listOf(
                        InfoLine(R.string.info_noroot_1),
                        InfoLine(R.string.info_noroot_2),
                        InfoLine(R.string.info_noroot_3, caveat = true),
                    ),
                )
            }
        }
    }
}

private class InfoLine(@param:StringRes val text: Int, val caveat: Boolean = false)

@Composable
private fun InfoCard(@StringRes title: Int, lines: List<InfoLine>) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceVariant, contentColor = cs.onSurface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    InfoIcon(if (line.caveat) R.drawable.ic_alert_circle else R.drawable.ic_check, line.caveat)
                    Text(stringResource(line.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoIcon(@DrawableRes icon: Int, caveat: Boolean) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = if (caveat) MaterialTheme.colorScheme.onSurfaceVariant else MowazzeTheme.extra.success,
        modifier = Modifier
            .padding(top = 1.dp)
            .size(20.dp),
    )
}

@Composable
private fun LinkStep(state: OnboardingState, model: OnboardingModel) {
    val context = LocalContext.current
    val action = state.linkAction

    StepLayout(
        bottom = {
            MowazzeButton(
                stringResource(
                    when (action) {
                        LinkAction.OPEN_DEVELOPER_SETTINGS -> R.string.link_s1
                        LinkAction.OPEN_SHIZUKU -> R.string.open_shizuku
                        LinkAction.GRANT -> R.string.link_grant
                    },
                ),
                {
                    when (action) {
                        LinkAction.OPEN_DEVELOPER_SETTINGS -> {
                            context.openDeveloperSettings()
                            model.onDeveloperSettingsOpened()
                        }
                        LinkAction.OPEN_SHIZUKU -> context.openShizuku()
                        LinkAction.GRANT -> model.grantShizuku()
                    }
                },
                Modifier.fillMaxWidth(),
                loading = state.requesting,
            )
            MowazzeButton(stringResource(R.string.cancel), { model.back() }, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                stringResource(R.string.link_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.link_step),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val step2 = if (android.os.Build.VERSION.SDK_INT >= 30) R.string.link_s2 else R.string.link_s2_adb
            listOf(R.string.link_s1, step2, R.string.link_s3).forEachIndexed { index, title ->
                LinkRow(index + 1, title, state.linkSteps[index])
            }
        }
        if (state.denied) {
            DeniedCard(
                canTryNoRoot = state.noRootSupported,
                onRetry = model::grantShizuku,
                onContinue = model::continueWithoutRoot,
            )
        } else if (action == LinkAction.GRANT) {
            Text(
                stringResource(R.string.link_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(number: Int, @StringRes title: Int, stepState: LinkStepState) {
    val cs = MaterialTheme.colorScheme
    val active = stepState == LinkStepState.YOURS || stepState == LinkStepState.ACTIVE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Surface(
            color = if (active) cs.primaryContainer else cs.surfaceVariant,
            contentColor = if (active) cs.onPrimaryContainer else cs.onSurfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (stepState) {
                    LinkStepState.DONE -> Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MowazzeTheme.extra.success,
                        modifier = Modifier.size(18.dp),
                    )
                    LinkStepState.ACTIVE -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    LinkStepState.YOURS, LinkStepState.WAITING -> Text("$number", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            stringResource(
                when (stepState) {
                    LinkStepState.DONE -> R.string.state_done
                    LinkStepState.ACTIVE -> R.string.state_active
                    LinkStepState.WAITING -> R.string.state_wait
                    LinkStepState.YOURS -> R.string.state_yours
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (active) cs.primary else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeniedCard(canTryNoRoot: Boolean, onRetry: () -> Unit, onContinue: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.errorContainer,
        contentColor = cs.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Icon(painterResource(R.drawable.ic_alert_circle), contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(if (canTryNoRoot) R.string.link_denied else R.string.link_denied_shizuku_only),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (canTryNoRoot) {
                MowazzeButton(stringResource(R.string.try_noroot), onContinue, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            } else {
                MowazzeButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }
        }
    }
}

private fun Context.openDeveloperSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        // Developer options are hidden on some devices. The next step still gives the person a way forward.
    }
}

private fun Context.openShizuku() {
    val intent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return
    startActivity(intent)
}

private fun Context.openShizukuInstall() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")))
    }
}

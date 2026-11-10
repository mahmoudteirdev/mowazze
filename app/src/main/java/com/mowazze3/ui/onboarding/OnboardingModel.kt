package com.mowazze3.ui.onboarding

import com.mowazze3.domain.DeviceProbe
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import com.mowazze3.domain.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, SCANNING, LEVELS, LINK }

enum class LinkStepState { DONE, ACTIVE, WAITING, YOURS }

enum class LinkAction { OPEN_DEVELOPER_SETTINGS, OPEN_SHIZUKU, GRANT }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val rootFound: Boolean = false,
    val shizuku: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val selected: PermissionLevel = PermissionLevel.NO_ROOT,
    val developerSettingsOpened: Boolean = false,
    val requesting: Boolean = false,
    val denied: Boolean = false,
    val noRootSupported: Boolean = true,
) {
    /** Which level gets the "best available" chip. The root flow isn't designed yet, so never root. */
    val bestLevel: PermissionLevel
        get() = when {
            shizuku != ShizukuStatus.NOT_INSTALLED -> PermissionLevel.SHIZUKU
            noRootSupported -> PermissionLevel.NO_ROOT
            else -> PermissionLevel.SHIZUKU
        }

    private val serverRunning get() = shizuku == ShizukuStatus.NEEDS_PERMISSION || shizuku == ShizukuStatus.READY

    val linkSteps: List<LinkStepState>
        get() = when {
            serverRunning -> listOf(
                LinkStepState.DONE,
                LinkStepState.DONE,
                if (requesting) LinkStepState.ACTIVE else LinkStepState.YOURS,
            )
            developerSettingsOpened -> listOf(LinkStepState.DONE, LinkStepState.YOURS, LinkStepState.WAITING)
            else -> listOf(LinkStepState.YOURS, LinkStepState.WAITING, LinkStepState.WAITING)
        }

    val linkAction: LinkAction
        get() = when {
            serverRunning -> LinkAction.GRANT
            developerSettingsOpened -> LinkAction.OPEN_SHIZUKU
            else -> LinkAction.OPEN_DEVELOPER_SETTINGS
        }
}

/**
 * First-run flow: welcome, device scan, level choice, then Shizuku linking if that level was picked.
 * Every method must be called on [scope]'s thread, the main thread in the app.
 */
class OnboardingModel(
    private val probe: DeviceProbe,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<PermissionLevel>(extraBufferCapacity = 1)

    /** Fires once per run, just before the level is saved and this flow leaves the screen. */
    val finished: SharedFlow<PermissionLevel> = _finished.asSharedFlow()

    private var scanJob: Job? = null
    private var grantJob: Job? = null
    private var finishing = false

    fun startScan() {
        if (_state.value.step != OnboardingStep.WELCOME) return
        _state.update { it.copy(step = OnboardingStep.SCANNING) }
        scanJob = scope.launch {
            delay(MIN_SCAN_MILLIS)
            val shizuku = probe.shizukuStatus()
            val noRoot = probe.isNoRootSupported()
            _state.update {
                val scanned = it.copy(
                    rootFound = probe.rootBinaryFound(),
                    shizuku = shizuku,
                    noRootSupported = noRoot,
                )
                scanned.copy(step = OnboardingStep.LEVELS, selected = scanned.bestLevel)
            }
        }
    }

    /** Returns false at the welcome screen, where back should leave the app. */
    fun back(): Boolean {
        when (_state.value.step) {
            OnboardingStep.WELCOME -> return false
            OnboardingStep.SCANNING -> {
                scanJob?.cancel()
                _state.update { it.copy(step = OnboardingStep.WELCOME) }
            }
            OnboardingStep.LEVELS -> _state.update { it.copy(step = OnboardingStep.WELCOME) }
            OnboardingStep.LINK -> {
                grantJob?.cancel()
                _state.update { it.copy(step = OnboardingStep.LEVELS, requesting = false, denied = false) }
            }
        }
        return true
    }

    fun select(level: PermissionLevel) {
        when (level) {
            PermissionLevel.ROOT -> return
            PermissionLevel.SHIZUKU -> if (_state.value.shizuku == ShizukuStatus.NOT_INSTALLED) return
            PermissionLevel.NO_ROOT -> if (!_state.value.noRootSupported) return
        }
        _state.update { it.copy(selected = level) }
    }

    fun confirmLevel() {
        val current = _state.value
        if (current.step != OnboardingStep.LEVELS) return
        when {
            current.selected != PermissionLevel.SHIZUKU -> finish(PermissionLevel.NO_ROOT)
            current.shizuku == ShizukuStatus.READY -> finish(PermissionLevel.SHIZUKU)
            else -> _state.update { it.copy(step = OnboardingStep.LINK, denied = false) }
        }
    }

    fun onDeveloperSettingsOpened() {
        _state.update { it.copy(developerSettingsOpened = true) }
    }

    fun grantShizuku() {
        val current = _state.value
        if (current.requesting || current.linkAction != LinkAction.GRANT) return
        _state.update { it.copy(requesting = true, denied = false) }
        grantJob = scope.launch {
            val allowed = probe.requestShizukuPermission()
            _state.update { it.copy(requesting = false, denied = !allowed, shizuku = probe.shizukuStatus()) }
            if (allowed) finish(PermissionLevel.SHIZUKU)
        }
    }

    fun continueWithoutRoot() {
        if (!_state.value.noRootSupported) return
        grantJob?.cancel()
        finish(PermissionLevel.NO_ROOT)
    }

    /** Call when returning to the app: the person may have installed or started Shizuku meanwhile. */
    fun refresh() {
        val current = _state.value
        if (current.step != OnboardingStep.LEVELS && current.step != OnboardingStep.LINK) return
        if (current.requesting) return
        val shizuku = probe.shizukuStatus()
        _state.update {
            val updated = it.copy(shizuku = shizuku)
            if (updated.selected == PermissionLevel.SHIZUKU && shizuku == ShizukuStatus.NOT_INSTALLED) {
                val fallback = if (updated.noRootSupported) PermissionLevel.NO_ROOT else PermissionLevel.SHIZUKU
                updated.copy(selected = fallback, step = OnboardingStep.LEVELS)
            } else {
                updated
            }
        }
        if (_state.value.step == OnboardingStep.LINK && shizuku == ShizukuStatus.READY) finish(PermissionLevel.SHIZUKU)
    }

    private fun finish(level: PermissionLevel) {
        if (finishing) return
        finishing = true
        scope.launch {
            _finished.emit(level)
            store.setLevel(level)
        }
    }

    /** "Change level" comes back to this same model, which is still holding the last finished run. */
    fun resetIfFinished() {
        if (!finishing) return
        finishing = false
        _state.value = OnboardingState()
    }

    private companion object {
        /** Long enough to read the screen, since the real checks finish instantly. */
        const val MIN_SCAN_MILLIS = 1_000L
    }
}

package com.mowazze3.ui.onboarding

import com.mowazze3.domain.AppSettings
import com.mowazze3.domain.DailyUsage
import com.mowazze3.domain.DeviceProbe
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import com.mowazze3.domain.ShizukuStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProbe(
    var status: ShizukuStatus = ShizukuStatus.READY,
    var root: Boolean = false,
    var noRoot: Boolean = true,
) : DeviceProbe {
    var answer = CompletableDeferred<Boolean>()
    var requests = 0
    var rootAnswer = CompletableDeferred<Boolean>()
    var rootRequests = 0

    override fun rootBinaryFound() = root
    override fun shizukuStatus() = status
    override fun isNoRootSupported() = noRoot
    override suspend fun requestShizukuPermission(): Boolean {
        requests++
        return answer.await()
    }
    override suspend fun requestRootPermission(): Boolean {
        rootRequests++
        return rootAnswer.await()
    }
}

private class MemoryStore : SettingsStore {
    override val settings = MutableStateFlow(AppSettings())
    var saveCount = 0
    override suspend fun setLevel(level: PermissionLevel) {
        saveCount++
        settings.update { it.copy(level = level) }
    }
    override suspend fun clearLevel() = settings.update { it.copy(level = null) }
    override suspend fun setNetwork(network: NetworkConfig) = settings.update { it.copy(network = network) }
    override suspend fun setNotifyNewDevice(enabled: Boolean) = settings.update { it.copy(notifyNewDevice = enabled) }
    override suspend fun loadUsage(): DailyUsage? = null
    override suspend fun saveUsage(usage: DailyUsage) = Unit
    override suspend fun setDataQuota(bytes: Long?) = settings.update { it.copy(dataQuotaBytes = bytes) }
    override suspend fun setAlertQuota80(enabled: Boolean) = settings.update { it.copy(alertQuota80 = enabled) }
    override suspend fun setMaxDevices(limit: Int?) = settings.update { it.copy(maxDevices = limit) }
    override suspend fun setBlockedClients(clients: Set<String>) = settings.update { it.copy(blockedClients = clients) }
    override suspend fun setSpeedLimits(limits: Map<String, String>) = settings.update { it.copy(speedLimits = limits) }
    override suspend fun setTtlValue(ttl: Int?) = settings.update { it.copy(ttlValue = ttl) }
    override suspend fun setVpnTethering(enabled: Boolean) = settings.update { it.copy(vpnTethering = enabled) }
    override suspend fun setAdBlockDns(enabled: Boolean) = settings.update { it.copy(adBlockDns = enabled) }
    override suspend fun setCustomMac(mac: String?) = settings.update { it.copy(customMac = mac) }
}

class OnboardingModelTest {
    private class Fixture(val probe: FakeProbe, val store: MemoryStore, val model: OnboardingModel) {
        val state get() = model.state.value
        val savedLevel get() = store.settings.value.level
    }

    private fun onboardingTest(probe: FakeProbe = FakeProbe(), body: suspend TestScope.(Fixture) -> Unit) = runTest {
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val store = MemoryStore()
        body(Fixture(probe, store, OnboardingModel(probe, store, scope)))
        scope.cancel()
    }

    private suspend fun TestScope.scanned(f: Fixture) {
        f.model.startScan()
        advanceUntilIdle()
    }

    private suspend fun TestScope.onLink(f: Fixture) {
        scanned(f)
        f.model.select(PermissionLevel.SHIZUKU)
        f.model.confirmLevel()
        runCurrent()
    }

    @Test
    fun `scanning shows for a moment and then the levels`() = onboardingTest { f ->
        assertEquals(OnboardingStep.WELCOME, f.state.step)
        f.model.startScan()
        assertEquals(OnboardingStep.SCANNING, f.state.step)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(OnboardingStep.SCANNING, f.state.step)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(OnboardingStep.LEVELS, f.state.step)
    }

    @Test
    fun `Shizuku is preselected as best when it is installed`() = onboardingTest(FakeProbe(ShizukuStatus.NOT_RUNNING)) { f ->
        scanned(f)
        assertEquals(PermissionLevel.SHIZUKU, f.state.selected)
        assertEquals(PermissionLevel.SHIZUKU, f.state.bestLevel)
    }

    @Test
    fun `without Shizuku the fallback is no root and Shizuku can't be picked`() =
        onboardingTest(FakeProbe(ShizukuStatus.NOT_INSTALLED)) { f ->
            scanned(f)
            assertEquals(PermissionLevel.NO_ROOT, f.state.selected)
            assertEquals(PermissionLevel.NO_ROOT, f.state.bestLevel)
            f.model.select(PermissionLevel.SHIZUKU)
            assertEquals(PermissionLevel.NO_ROOT, f.state.selected)
        }

    @Test
    fun `root can be picked on a rooted device and is best level`() = onboardingTest(FakeProbe(root = true)) { f ->
        scanned(f)
        assertTrue(f.state.rootFound)
        assertEquals(PermissionLevel.ROOT, f.state.bestLevel)
        assertEquals(PermissionLevel.ROOT, f.state.selected)
    }

    @Test
    fun `confirming root requests permission and finishes if granted`() = onboardingTest(FakeProbe(root = true)) { f ->
        scanned(f)
        f.probe.rootAnswer.complete(true)
        f.model.confirmLevel()
        advanceUntilIdle()
        assertEquals(PermissionLevel.ROOT, f.savedLevel)
    }

    @Test
    fun `no root finishes straight away and saves the level`() = onboardingTest { f ->
        scanned(f)
        f.model.select(PermissionLevel.NO_ROOT)
        f.model.confirmLevel()
        advanceUntilIdle()
        assertEquals(PermissionLevel.NO_ROOT, f.savedLevel)
    }

    @Test
    fun `Shizuku that is already allowed skips the linking`() = onboardingTest(FakeProbe(ShizukuStatus.READY)) { f ->
        scanned(f)
        f.model.confirmLevel()
        advanceUntilIdle()
        assertEquals(PermissionLevel.SHIZUKU, f.savedLevel)
    }

    @Test
    fun `linking walks through the steps as Shizuku comes up`() = onboardingTest(FakeProbe(ShizukuStatus.NOT_RUNNING)) { f ->
        onLink(f)
        assertEquals(OnboardingStep.LINK, f.state.step)
        assertEquals(LinkAction.OPEN_DEVELOPER_SETTINGS, f.state.linkAction)
        assertEquals(listOf(LinkStepState.YOURS, LinkStepState.WAITING, LinkStepState.WAITING), f.state.linkSteps)

        f.model.onDeveloperSettingsOpened()
        assertEquals(LinkAction.OPEN_SHIZUKU, f.state.linkAction)
        assertEquals(listOf(LinkStepState.DONE, LinkStepState.YOURS, LinkStepState.WAITING), f.state.linkSteps)

        f.probe.status = ShizukuStatus.NEEDS_PERMISSION
        f.model.refresh()
        assertEquals(LinkAction.GRANT, f.state.linkAction)
        assertEquals(listOf(LinkStepState.DONE, LinkStepState.DONE, LinkStepState.YOURS), f.state.linkSteps)
    }

    @Test
    fun `granting finishes with Shizuku`() = onboardingTest(FakeProbe(ShizukuStatus.NEEDS_PERMISSION)) { f ->
        onLink(f)
        f.model.grantShizuku()
        runCurrent()
        assertEquals(LinkStepState.ACTIVE, f.state.linkSteps[2])
        f.probe.status = ShizukuStatus.READY
        f.probe.answer.complete(true)
        advanceUntilIdle()
        assertEquals(PermissionLevel.SHIZUKU, f.savedLevel)
    }

    @Test
    fun `tapping grant twice asks once`() = onboardingTest(FakeProbe(ShizukuStatus.NEEDS_PERMISSION)) { f ->
        onLink(f)
        f.model.grantShizuku()
        runCurrent()
        f.model.grantShizuku()
        runCurrent()
        assertEquals(1, f.probe.requests)
    }

    @Test
    fun `refusing keeps step 3 waiting, shows the way out and doesn't save`() =
        onboardingTest(FakeProbe(ShizukuStatus.NEEDS_PERMISSION)) { f ->
            onLink(f)
            f.model.grantShizuku()
            f.probe.answer.complete(false)
            advanceUntilIdle()
            assertTrue(f.state.denied)
            assertEquals(LinkStepState.YOURS, f.state.linkSteps[2])
            assertNull(f.savedLevel)

            f.model.continueWithoutRoot()
            advanceUntilIdle()
            assertEquals(PermissionLevel.NO_ROOT, f.savedLevel)
        }

    @Test
    fun `refusing can be retried and the error clears`() = onboardingTest(FakeProbe(ShizukuStatus.NEEDS_PERMISSION)) { f ->
        onLink(f)
        f.model.grantShizuku()
        f.probe.answer.complete(false)
        advanceUntilIdle()

        f.probe.answer = CompletableDeferred()
        f.model.grantShizuku()
        runCurrent()
        assertFalse(f.state.denied)
        f.probe.status = ShizukuStatus.READY
        f.probe.answer.complete(true)
        advanceUntilIdle()
        assertEquals(PermissionLevel.SHIZUKU, f.savedLevel)
    }

    @Test
    fun `allowing inside the Shizuku app finishes on return`() = onboardingTest(FakeProbe(ShizukuStatus.NOT_RUNNING)) { f ->
        onLink(f)
        f.probe.status = ShizukuStatus.READY
        f.model.refresh()
        advanceUntilIdle()
        assertEquals(PermissionLevel.SHIZUKU, f.savedLevel)
    }

    @Test
    fun `installing Shizuku is picked up on return`() = onboardingTest(FakeProbe(ShizukuStatus.NOT_INSTALLED)) { f ->
        scanned(f)
        assertEquals(PermissionLevel.NO_ROOT, f.state.bestLevel)
        f.probe.status = ShizukuStatus.NOT_RUNNING
        f.model.refresh()
        assertEquals(PermissionLevel.SHIZUKU, f.state.bestLevel)
    }

    @Test
    fun `uninstalling Shizuku while it is selected falls back to no root`() =
        onboardingTest(FakeProbe(ShizukuStatus.NOT_RUNNING)) { f ->
            onLink(f)
            f.probe.status = ShizukuStatus.NOT_INSTALLED
            f.model.refresh()
            assertEquals(OnboardingStep.LEVELS, f.state.step)
            assertEquals(PermissionLevel.NO_ROOT, f.state.selected)
        }

    @Test
    fun `back goes link to levels to welcome, then lets the app exit`() = onboardingTest(FakeProbe(ShizukuStatus.NOT_RUNNING)) { f ->
        onLink(f)
        assertTrue(f.model.back())
        assertEquals(OnboardingStep.LEVELS, f.state.step)
        assertTrue(f.model.back())
        assertEquals(OnboardingStep.WELCOME, f.state.step)
        assertFalse(f.model.back())
    }

    @Test
    fun `back during the scan cancels it`() = onboardingTest { f ->
        f.model.startScan()
        assertTrue(f.model.back())
        advanceUntilIdle()
        assertEquals(OnboardingStep.WELCOME, f.state.step)
    }

    @Test
    fun `cancelling while the permission dialog is up ignores a late answer`() =
        onboardingTest(FakeProbe(ShizukuStatus.NEEDS_PERMISSION)) { f ->
            onLink(f)
            f.model.grantShizuku()
            runCurrent()
            f.model.back()
            f.probe.answer.complete(true)
            advanceUntilIdle()
            assertEquals(OnboardingStep.LEVELS, f.state.step)
            assertFalse(f.state.requesting)
            assertNull(f.savedLevel)
        }

    @Test
    fun `finishing twice saves once`() = onboardingTest { f ->
        scanned(f)
        f.model.select(PermissionLevel.NO_ROOT)
        f.model.confirmLevel()
        f.model.confirmLevel()
        advanceUntilIdle()
        assertEquals(PermissionLevel.NO_ROOT, f.savedLevel)
        assertEquals(1, f.store.saveCount)
    }

    @Test
    fun `changing level later starts the flow over`() = onboardingTest { f ->
        scanned(f)
        f.model.select(PermissionLevel.NO_ROOT)
        f.model.confirmLevel()
        advanceUntilIdle()

        f.store.clearLevel()
        f.model.resetIfFinished()
        assertEquals(OnboardingStep.WELCOME, f.state.step)

        scanned(f)
        f.model.select(PermissionLevel.NO_ROOT)
        f.model.confirmLevel()
        advanceUntilIdle()
        assertEquals(PermissionLevel.NO_ROOT, f.savedLevel)
        assertEquals(2, f.store.saveCount)
    }

    @Test
    fun `reset does nothing to a run in progress`() = onboardingTest { f ->
        scanned(f)
        f.model.resetIfFinished()
        assertEquals(OnboardingStep.LEVELS, f.state.step)
    }

    @Test
    fun `when noRoot is not supported it cannot be selected and best level is Shizuku`() =
        onboardingTest(FakeProbe(status = ShizukuStatus.NOT_INSTALLED, noRoot = false)) { f ->
            scanned(f)
            assertFalse(f.state.noRootSupported)
            assertEquals(PermissionLevel.SHIZUKU, f.state.bestLevel)
            f.model.select(PermissionLevel.NO_ROOT)
            assertEquals(PermissionLevel.SHIZUKU, f.state.selected)
            f.model.continueWithoutRoot()
            advanceUntilIdle()
            assertNull(f.savedLevel)
        }
}

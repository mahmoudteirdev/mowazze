package com.mowazze3.data

import com.mowazze3.domain.AppSettings
import com.mowazze3.domain.Client
import com.mowazze3.domain.DailyUsage
import com.mowazze3.domain.EngineEvent
import com.mowazze3.domain.FailReason
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.NetworkError
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.ShareState
import com.mowazze3.domain.StartResult
import com.mowazze3.domain.connectedCount
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val ZONE: ZoneId = ZoneId.of("Asia/Gaza")
private val NETWORK = NetworkConfig("net", "password1")
private val LAPTOP = Client("a", "لابتوب", "192.168.43.2", 0)
private val PHONE = Client("b", "تلفون", "192.168.43.3", 0)

private class TestEngine : ShareEngine {
    var result: StartResult = StartResult.Started
    var hangOnStart = false
    var running = false
    var startCount = 0
    var stopCount = 0
    val started = mutableListOf<NetworkConfig?>()
    override var networkControl = NetworkControl.READ_WRITE
    var systemNetwork: NetworkConfig? = null
    override suspend fun readNetwork() = systemNetwork
    private val flow = MutableSharedFlow<EngineEvent>(replay = 1)
    override val events: Flow<EngineEvent> = flow

    override suspend fun start(config: NetworkConfig?): StartResult {
        startCount++
        started += config
        if (hangOnStart) awaitCancellation()
        running = result == StartResult.Started
        return result
    }

    override suspend fun stop() {
        stopCount++
        running = false
        flow.resetReplayCache()
    }

    override suspend fun isRunning() = running

    fun emit(
        clients: List<Client> = emptyList(),
        hasInternet: Boolean = true,
        downMbps: Float? = 10f,
        sessionBytes: Long = 0,
    ) {
        check(flow.tryEmit(EngineEvent.Snapshot(clients, hasInternet, downMbps, sessionBytes))) { DROPPED }
    }

    fun emitStopped(reason: FailReason?) {
        check(flow.tryEmit(EngineEvent.Stopped(reason))) { DROPPED }
    }

    private companion object {
        const val DROPPED = "event dropped: call runCurrent() between emits"
    }
}

private class MemoryStore(network: NetworkConfig? = NETWORK) : SettingsStore {
    override val settings = MutableStateFlow(AppSettings(network = network))
    var usage: DailyUsage? = null

    override suspend fun setLevel(level: PermissionLevel) = settings.update { it.copy(level = level) }
    override suspend fun clearLevel() = settings.update { it.copy(level = null) }
    override suspend fun setNetwork(network: NetworkConfig) = settings.update { it.copy(network = network) }
    override suspend fun setNotifyNewDevice(enabled: Boolean) = settings.update { it.copy(notifyNewDevice = enabled) }
    override suspend fun loadUsage() = usage
    override suspend fun saveUsage(usage: DailyUsage) {
        this.usage = usage
    }
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

private class Fixture(
    val engine: TestEngine,
    val store: MemoryStore,
    val repository: ShareRepository,
    val events: List<ShareEvent>,
    private val setTime: (ZonedDateTime) -> Unit,
) {
    fun setTime(time: ZonedDateTime) = setTime.invoke(time)
}

class ShareRepositoryTest {
    private fun repositoryTest(
        store: MemoryStore = MemoryStore(),
        body: suspend TestScope.(Fixture) -> Unit,
    ) = runTest {
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val engine = TestEngine()
        var time = ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZONE)
        val repository = ShareRepository(engine, store, scope, now = { time })
        val events = mutableListOf<ShareEvent>()
        scope.launch { repository.events.collect { events += it } }
        runCurrent()
        body(Fixture(engine, store, repository, events) { time = it })
        scope.cancel()
    }

    private val Fixture.on get() = repository.state.value as ShareState.On

    @Test
    fun `starting moves through Starting to On with the saved name`() = repositoryTest { f ->
        assertEquals(ShareState.Off, f.repository.state.value)
        f.repository.start()
        assertEquals(ShareState.Starting, f.repository.state.value)
        advanceUntilIdle()
        assertEquals("net", f.on.ssid)
        assertEquals(NETWORK, f.engine.started.single())
    }

    @Test
    fun `tapping start while starting or on is ignored`() = repositoryTest { f ->
        f.engine.hangOnStart = true
        f.repository.start()
        runCurrent()
        f.repository.start()
        f.repository.start()
        runCurrent()
        assertEquals(1, f.engine.startCount)

        f.repository.stop()
        runCurrent()
        f.engine.hangOnStart = false
        f.repository.start()
        advanceUntilIdle()
        f.repository.start()
        advanceUntilIdle()
        assertEquals(2, f.engine.startCount)
    }

    @Test
    fun `a start that never finishes fails with TIMEOUT after 15 seconds`() = repositoryTest { f ->
        f.engine.hangOnStart = true
        f.repository.start()
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(ShareState.Starting, f.repository.state.value)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(ShareState.Failed(FailReason.TIMEOUT), f.repository.state.value)
        assertEquals("the half-started engine gets stopped", 1, f.engine.stopCount)
    }

    @Test
    fun `the engine's failure reason reaches the state and retry works`() = repositoryTest { f ->
        f.engine.result = StartResult.Failed(FailReason.WIFI_CONNECTED)
        f.repository.start()
        advanceUntilIdle()
        assertEquals(ShareState.Failed(FailReason.WIFI_CONNECTED), f.repository.state.value)

        f.engine.result = StartResult.Started
        f.repository.start()
        advanceUntilIdle()
        assertTrue(f.repository.state.value is ShareState.On)
    }

    @Test
    fun `an exception from the engine is a failure, not a crash`() = runTest {
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val engine = object : ShareEngine {
            override val networkControl = NetworkControl.READ_WRITE
            override val events: Flow<EngineEvent> = MutableSharedFlow()
            override suspend fun start(config: NetworkConfig?): StartResult = error("boom")
            override suspend fun stop() = Unit
            override suspend fun isRunning() = false
        }
        val repository = ShareRepository(engine, MemoryStore(), scope)
        repository.start()
        advanceUntilIdle()
        assertEquals(ShareState.Failed(FailReason.UNKNOWN), repository.state.value)
        scope.cancel()
    }

    @Test
    fun `a device list updates live and blocked devices are not counted`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(listOf(LAPTOP, PHONE.copy(blocked = true)))
        runCurrent()
        assertEquals(2, f.on.clients.size)
        assertEquals(1, f.on.connectedCount)
    }

    @Test
    fun `without internet sharing stays on and speed is empty`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(hasInternet = false, downMbps = 5f)
        runCurrent()
        assertEquals(false, f.on.hasInternet)
        assertNull(f.on.downMbps)

        f.engine.emit(hasInternet = true, downMbps = 5f)
        runCurrent()
        assertEquals(true, f.on.hasInternet)
        assertEquals(5f, f.on.downMbps)
    }

    @Test
    fun `stopping clears devices but keeps today's usage`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(listOf(LAPTOP), sessionBytes = 1_000)
        runCurrent()
        f.repository.stop()
        advanceUntilIdle()
        assertEquals(ShareState.Off, f.repository.state.value)
        assertEquals(1, f.engine.stopCount)
        assertEquals(1_000, f.repository.usageToday.value)
        assertEquals(1_000L, f.store.usage?.bytes)
    }

    @Test
    fun `usage adds up across sessions`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(sessionBytes = 300)
        runCurrent()
        f.engine.emit(sessionBytes = 500)
        runCurrent()
        f.repository.stop()
        advanceUntilIdle()

        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(sessionBytes = 200)
        runCurrent()
        assertEquals(700, f.repository.usageToday.value)
    }

    @Test
    fun `a counter that restarts mid-session is not lost`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(sessionBytes = 500)
        runCurrent()
        f.engine.emit(sessionBytes = 100)
        runCurrent()
        assertEquals(600, f.repository.usageToday.value)
    }

    @Test
    fun `usage resets at midnight while sharing`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(sessionBytes = 500)
        runCurrent()
        f.setTime(ZonedDateTime.of(2026, 11, 10, 0, 0, 1, 0, ZONE))
        f.engine.emit(sessionBytes = 650)
        runCurrent()
        assertEquals(150, f.repository.usageToday.value)
    }

    @Test
    fun `refreshDay shows zero on a new day without sharing`() = repositoryTest { f ->
        f.store.usage = DailyUsage(java.time.LocalDate.of(2026, 11, 8), ZONE, 999)
        f.repository.refreshDay()
        advanceUntilIdle()
        assertEquals(0, f.repository.usageToday.value)
    }

    @Test
    fun `saved usage from today is loaded`() = repositoryTest { f ->
        f.store.usage = DailyUsage(java.time.LocalDate.of(2026, 11, 9), ZONE, 999)
        f.repository.refreshDay()
        advanceUntilIdle()
        assertEquals(999, f.repository.usageToday.value)
    }

    @Test
    fun `an invalid network is refused and nothing changes`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        val result = f.repository.saveNetwork(NetworkConfig("", "short"))
        advanceUntilIdle()
        val errors = (result as SaveResult.Invalid).errors
        assertTrue(NetworkError.SSID_EMPTY in errors && NetworkError.PASSWORD_TOO_SHORT in errors)
        assertEquals(NETWORK, f.store.settings.value.network)
        assertEquals(1, f.engine.startCount)
    }

    @Test
    fun `saving while off only stores the network`() = repositoryTest { f ->
        val new = NetworkConfig("new", "password2")
        assertEquals(SaveResult.Saved, f.repository.saveNetwork(new))
        advanceUntilIdle()
        assertEquals(new, f.store.settings.value.network)
        assertEquals(0, f.engine.startCount)
        assertEquals(ShareState.Off, f.repository.state.value)
    }

    @Test
    fun `saving while on restarts with the new network`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(listOf(LAPTOP))
        runCurrent()

        val new = NetworkConfig("new", "password2")
        assertEquals(SaveResult.SavedDevicesMustReconnect, f.repository.saveNetwork(new))
        assertEquals(ShareState.Starting, f.repository.state.value)
        advanceUntilIdle()
        assertEquals("new", f.on.ssid)
        assertTrue("devices have to reconnect", f.on.clients.isEmpty())
        assertEquals(new, f.engine.started.last())
        assertEquals(1, f.engine.stopCount)
    }

    @Test
    fun `saving the same network doesn't restart`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        assertEquals(SaveResult.Saved, f.repository.saveNetwork(NETWORK))
        advanceUntilIdle()
        assertEquals(1, f.engine.startCount)
        assertEquals(0, f.engine.stopCount)
    }

    @Test
    fun `a first start creates and stores a valid network`() = repositoryTest(MemoryStore(network = null)) { f ->
        f.repository.start()
        advanceUntilIdle()
        val created = f.store.settings.value.network
        assertNotNull(created)
        assertEquals(created, f.engine.started.single())
    }

    @Test
    fun `an engine that can't apply a network never gets a made-up one`() = repositoryTest(MemoryStore(network = null)) { f ->
        f.engine.networkControl = NetworkControl.NONE
        assertNull(f.repository.currentNetwork())
        assertNull(f.store.settings.value.network)

        f.repository.start()
        advanceUntilIdle()
        assertTrue("sharing still works without the details", f.repository.state.value is ShareState.On)
        assertNull(f.engine.started.single())
        assertNull(f.store.settings.value.network)
    }

    @Test
    fun `saving details for such an engine doesn't restart or disconnect anyone`() = repositoryTest { f ->
        f.engine.networkControl = NetworkControl.NONE
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(listOf(LAPTOP))
        runCurrent()

        val typed = NetworkConfig("my-hotspot", "password2")
        assertEquals(SaveResult.Saved, f.repository.saveNetwork(typed))
        advanceUntilIdle()
        assertEquals(typed, f.store.settings.value.network)
        assertEquals(0, f.engine.stopCount)
        assertEquals(1, f.engine.startCount)
        assertEquals(listOf(LAPTOP), f.on.clients)
    }

    @Test
    fun `invalid details are still refused for such an engine`() = repositoryTest(MemoryStore(network = null)) { f ->
        f.engine.networkControl = NetworkControl.NONE
        assertTrue(f.repository.saveNetwork(NetworkConfig("", "short")) is SaveResult.Invalid)
        assertNull(f.store.settings.value.network)
    }

    @Test
    fun `details read from the system replace whatever was saved`() = repositoryTest { f ->
        f.engine.networkControl = NetworkControl.READ_ONLY
        val real = NetworkConfig("real-hotspot", "password9")
        f.engine.systemNetwork = real
        assertEquals(real, f.repository.currentNetwork())
        assertEquals(real, f.store.settings.value.network)
    }

    @Test
    fun `changing the hotspot in system settings is picked up on return`() = repositoryTest { f ->
        f.engine.networkControl = NetworkControl.READ_ONLY
        f.engine.systemNetwork = NetworkConfig("first", "password1")
        f.repository.refreshNetwork()
        advanceUntilIdle()
        val changed = NetworkConfig("second", "password2")
        f.engine.systemNetwork = changed
        f.repository.refreshNetwork()
        advanceUntilIdle()
        assertEquals(changed, f.store.settings.value.network)
    }

    @Test
    fun `when the system can't be read right now the last known details stay`() = repositoryTest { f ->
        f.engine.networkControl = NetworkControl.READ_ONLY
        f.engine.systemNetwork = null
        assertEquals(NETWORK, f.repository.currentNetwork())
        assertEquals(NETWORK, f.store.settings.value.network)
    }

    @Test
    fun `the engine stopping by itself updates the state`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emitStopped(FailReason.SHIZUKU_EXPIRED)
        runCurrent()
        assertEquals(ShareState.Failed(FailReason.SHIZUKU_EXPIRED), f.repository.state.value)
    }

    @Test
    fun `turning it off elsewhere returns to Off`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emitStopped(null)
        runCurrent()
        assertEquals(ShareState.Off, f.repository.state.value)
    }

    @Test
    fun `the system ending the service is reported, a normal stop is not`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.repository.stop()
        advanceUntilIdle()
        f.repository.onServiceDestroyed()
        assertEquals(ShareState.Off, f.repository.state.value)

        f.repository.start()
        advanceUntilIdle()
        f.repository.onServiceDestroyed()
        advanceUntilIdle()
        assertEquals(ShareState.Failed(FailReason.SYSTEM_STOPPED), f.repository.state.value)
    }

    @Test
    fun `joining and leaving produce events`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(listOf(LAPTOP))
        runCurrent()
        f.engine.emit(emptyList())
        runCurrent()
        assertEquals(
            listOf(ShareEvent.SharingStarted, ShareEvent.ClientJoined(LAPTOP), ShareEvent.ClientLeft(LAPTOP)),
            f.events,
        )
    }

    @Test
    fun `restarting after a save doesn't announce sharing, so its own message stays up`() = repositoryTest { f ->
        f.repository.start()
        advanceUntilIdle()
        assertEquals(listOf<ShareEvent>(ShareEvent.SharingStarted), f.events)

        f.repository.saveNetwork(NetworkConfig("new", "password2"))
        advanceUntilIdle()
        assertTrue(f.repository.state.value is ShareState.On)
        assertEquals(listOf<ShareEvent>(ShareEvent.SharingStarted), f.events)
    }

    @Test
    fun `picking up a running hotspot doesn't announce sharing`() = repositoryTest { f ->
        f.engine.running = true
        f.repository.restore()
        advanceUntilIdle()
        assertTrue(f.repository.state.value is ShareState.On)
        assertTrue(f.events.isEmpty())
    }

    @Test
    fun `join events respect the setting, leave events still fire`() = repositoryTest { f ->
        f.store.setNotifyNewDevice(false)
        f.repository.start()
        advanceUntilIdle()
        f.engine.emit(listOf(LAPTOP))
        runCurrent()
        f.engine.emit(emptyList())
        runCurrent()
        assertEquals(listOf(ShareEvent.SharingStarted, ShareEvent.ClientLeft(LAPTOP)), f.events)
    }

    @Test
    fun `restore picks up a running hotspot without restarting or double counting`() = repositoryTest { f ->
        f.engine.running = true
        f.repository.restore()
        advanceUntilIdle()
        assertTrue(f.repository.state.value is ShareState.On)
        assertEquals(0, f.engine.startCount)

        f.engine.emit(sessionBytes = 5_000)
        runCurrent()
        assertEquals("the first reading is only a baseline", 0, f.repository.usageToday.value)
        f.engine.emit(sessionBytes = 5_400)
        runCurrent()
        assertEquals(400, f.repository.usageToday.value)
    }

    @Test
    fun `restore stays off when nothing is running`() = repositoryTest { f ->
        f.repository.restore()
        advanceUntilIdle()
        assertEquals(ShareState.Off, f.repository.state.value)
    }
}

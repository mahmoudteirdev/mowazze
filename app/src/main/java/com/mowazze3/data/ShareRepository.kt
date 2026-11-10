package com.mowazze3.data

import com.mowazze3.data.root.RootNetworkManager
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
import com.mowazze3.domain.defaultNetwork
import com.mowazze3.domain.validate
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ShareEvent {
    /** The person turned sharing on. Not sent when saving restarts the network, which has its own message. */
    data object SharingStarted : ShareEvent
    data class ClientJoined(val client: Client) : ShareEvent
    data class ClientLeft(val client: Client) : ShareEvent
    data object QuotaAlert80 : ShareEvent
}

sealed interface SaveResult {
    data class Invalid(val errors: Set<NetworkError>) : SaveResult
    data object Saved : SaveResult

    /** Saved while sharing was on, so the network restarted and devices have to reconnect. */
    data object SavedDevicesMustReconnect : SaveResult
}

/**
 * The single source of truth for sharing (HANDOFF section 5): screens and the foreground service only
 * read from here. Every method must be called on [scope]'s thread, the main thread in the app.
 */
class ShareRepository(
    private val engine: ShareEngine,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
    private val startTimeoutMillis: Long = 15_000,
) {
    private val _state = MutableStateFlow<ShareState>(ShareState.Off)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private val _usageToday = MutableStateFlow(0L)
    val usageToday: StateFlow<Long> = _usageToday.asStateFlow()

    private val _events = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ShareEvent> = _events.asSharedFlow()

    private var sessionJob: Job? = null
    private var pendingStop: Job? = null

    private var clients: List<Client> = emptyList()
    private var usage: DailyUsage? = null
    private var lastPersisted: ZonedDateTime? = null

    /** Null means the next snapshot is the baseline, because the session began before this process did. */
    private var lastSessionBytes: Long? = 0
    private var alertedQuotaToday = false

    fun blockClient(clientId: String) {
        scope.launch {
            val settings = store.settings.first()
            val newBlocked = settings.blockedClients + clientId
            store.setBlockedClients(newBlocked)
            if (settings.level == PermissionLevel.ROOT) {
                RootNetworkManager.blockClient(clientId)
            }
            updateClientsWithSettings()
        }
    }

    fun unblockClient(clientId: String) {
        scope.launch {
            val settings = store.settings.first()
            val newBlocked = settings.blockedClients - clientId
            store.setBlockedClients(newBlocked)
            if (settings.level == PermissionLevel.ROOT) {
                RootNetworkManager.unblockClient(clientId)
            }
            updateClientsWithSettings()
        }
    }

    fun setClientSpeedLimit(clientId: String, limit: String?) {
        scope.launch {
            val settings = store.settings.first()
            val newLimits = if (limit != null) settings.speedLimits + (clientId to limit) else settings.speedLimits - clientId
            store.setSpeedLimits(newLimits)
            updateClientsWithSettings()
        }
    }

    fun setMaxDevices(limit: Int?) {
        scope.launch { store.setMaxDevices(limit) }
    }

    fun setDataQuota(bytes: Long?) {
        scope.launch { store.setDataQuota(bytes) }
    }

    fun setAlertQuota80(enabled: Boolean) {
        scope.launch { store.setAlertQuota80(enabled) }
    }

    private suspend fun updateClientsWithSettings() {
        val settings = store.settings.first()
        val updated = clients.map { client ->
            client.copy(
                blocked = client.id in settings.blockedClients,
                speedLimit = settings.speedLimits[client.id],
            )
        }
        clients = updated
        val current = _state.value as? ShareState.On ?: return
        _state.value = current.copy(clients = updated)
    }

    /** Taps while starting or already on are ignored. From [ShareState.Failed] this is the retry. */
    fun start() {
        when (_state.value) {
            ShareState.Starting, is ShareState.On -> return
            ShareState.Off, is ShareState.Failed -> Unit
        }
        _state.value = ShareState.Starting
        launchSession(announce = true)
    }

    fun stop() {
        val previous = _state.value
        if (previous == ShareState.Off) return
        sessionJob?.cancel()
        clients = emptyList()
        _state.value = ShareState.Off
        if (previous is ShareState.Failed) return
        pendingStop = scope.launch {
            stopEngineQuietly()
            persistUsage()
        }
    }

    /** If sharing is on, this restarts the network with the new name and password, without asking first. */
    suspend fun saveNetwork(network: NetworkConfig): SaveResult {
        val errors = network.validate()
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        if (store.settings.first().network == network) return SaveResult.Saved

        store.setNetwork(network)
        // These values only describe the hotspot here, so nothing restarts and nobody gets disconnected.
        if (engine.networkControl != NetworkControl.READ_WRITE) return SaveResult.Saved
        val previous = _state.value
        if (previous != ShareState.Starting && previous !is ShareState.On) return SaveResult.Saved

        sessionJob?.cancel()
        clients = emptyList()
        _state.value = ShareState.Starting
        pendingStop = scope.launch { stopEngineQuietly() }
        launchSession()
        return if (previous is ShareState.On) SaveResult.SavedDevicesMustReconnect else SaveResult.Saved
    }

    /** Picks sharing back up if it outlived the app's process. */
    fun restore() {
        scope.launch {
            currentUsage()
            if (_state.value == ShareState.Off && engine.isRunning()) {
                _state.value = ShareState.Starting
                launchSession(alreadyRunning = true)
            }
        }
    }

    /** The foreground service ended. Harmless after a normal stop; otherwise the system ended it. */
    fun onServiceDestroyed() {
        val previous = _state.value
        if (previous != ShareState.Starting && previous !is ShareState.On) return
        sessionJob?.cancel()
        clients = emptyList()
        _state.value = ShareState.Failed(FailReason.SYSTEM_STOPPED)
        pendingStop = scope.launch {
            stopEngineQuietly()
            persistUsage()
        }
    }

    /** Call when the app returns to the screen, so a new day shows zero even if nothing was shared. */
    fun refreshDay() {
        alertedQuotaToday = false
        scope.launch { currentUsage() }
    }

    private fun launchSession(alreadyRunning: Boolean = false, announce: Boolean = false) {
        val stopping = pendingStop
        sessionJob = scope.launch {
            stopping?.join()
            runSession(alreadyRunning, announce)
        }
    }

    private suspend fun runSession(alreadyRunning: Boolean, announce: Boolean) {
        val network = currentNetwork()
        if (!alreadyRunning && !startEngine(network)) return

        val settings = store.settings.first()
        if (settings.level == PermissionLevel.ROOT) {
            try {
                if (settings.ttlValue != null) RootNetworkManager.applyTtl(settings.ttlValue)
                if (settings.vpnTethering) RootNetworkManager.applyVpnTethering(true)
                if (settings.adBlockDns) RootNetworkManager.applyAdBlock(true)
                settings.blockedClients.forEach { RootNetworkManager.blockClient(it) }
            } catch (_: Exception) { }
        }

        lastSessionBytes = if (alreadyRunning) null else 0
        clients = emptyList()
        currentUsage()
        _state.value = ShareState.On(network?.ssid.orEmpty(), emptyList(), hasInternet = true, downMbps = null)
        if (announce) _events.tryEmit(ShareEvent.SharingStarted)

        try {
            engine.events.takeWhile(::handleEvent).collect()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            stopEngineQuietly()
            clients = emptyList()
            _state.value = ShareState.Failed(FailReason.UNKNOWN)
            persistUsage()
        }
    }

    /** Returns false after moving the state to Failed. */
    private suspend fun startEngine(network: NetworkConfig?): Boolean {
        val result = try {
            withTimeoutOrNull(startTimeoutMillis) { engine.start(network) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StartResult.Failed(FailReason.UNKNOWN)
        }
        return when (result) {
            StartResult.Started -> true
            is StartResult.Failed -> {
                _state.value = ShareState.Failed(result.reason)
                false
            }
            null -> {
                stopEngineQuietly()
                _state.value = ShareState.Failed(FailReason.TIMEOUT)
                false
            }
        }
    }

    /** Returns false when the session is over. */
    private suspend fun handleEvent(event: EngineEvent): Boolean = when (event) {
        is EngineEvent.Snapshot -> {
            applySnapshot(event)
            true
        }
        is EngineEvent.Stopped -> {
            clients = emptyList()
            _state.value = event.reason?.let(ShareState::Failed) ?: ShareState.Off
            persistUsage()
            false
        }
    }

    private suspend fun applySnapshot(snapshot: EngineEvent.Snapshot) {
        val settings = store.settings.first()
        val enriched = snapshot.clients.map { client ->
            client.copy(
                blocked = client.blocked || client.id in settings.blockedClients,
                speedLimit = client.speedLimit ?: settings.speedLimits[client.id],
            )
        }
        announceChanges(enriched)
        clients = enriched
        recordBytes(snapshot.sessionBytes)

        val quota = settings.dataQuotaBytes
        if (settings.alertQuota80 && quota != null && !alertedQuotaToday) {
            if (_usageToday.value >= (quota * 0.8)) {
                alertedQuotaToday = true
                _events.tryEmit(ShareEvent.QuotaAlert80)
            }
        }

        val current = _state.value as? ShareState.On ?: return
        _state.value = current.copy(
            clients = enriched,
            hasInternet = snapshot.hasInternet,
            downMbps = snapshot.downMbps.takeIf { snapshot.hasInternet },
        )
    }

    private suspend fun announceChanges(latest: List<Client>) {
        val knownIds = clients.map { it.id }.toSet()
        val latestIds = latest.map { it.id }.toSet()
        val joined = latest.filter { it.id !in knownIds }
        if (joined.isNotEmpty() && store.settings.first().notifyNewDevice) {
            joined.forEach { _events.tryEmit(ShareEvent.ClientJoined(it)) }
        }
        clients.filter { it.id !in latestIds }.forEach { _events.tryEmit(ShareEvent.ClientLeft(it)) }
    }

    private suspend fun recordBytes(sessionBytes: Long) {
        val baseline = lastSessionBytes ?: sessionBytes
        // A smaller value means the engine's counter restarted, so all of it is new.
        val added = if (sessionBytes >= baseline) sessionBytes - baseline else sessionBytes
        lastSessionBytes = sessionBytes
        val updated = currentUsage().let { it.copy(bytes = it.bytes + added) }
        usage = updated
        _usageToday.value = updated.bytes

        val persisted = lastPersisted
        if (persisted == null || Duration.between(persisted, now()) >= PERSIST_INTERVAL) persistUsage()
    }

    private suspend fun currentUsage(): DailyUsage {
        val time = now()
        val current = (usage ?: store.loadUsage() ?: DailyUsage.startOf(time)).rolledTo(time)
        usage = current
        _usageToday.value = current.bytes
        return current
    }

    private suspend fun persistUsage() {
        usage?.let { store.saveUsage(it) }
        lastPersisted = now()
    }

    /**
     * The network to show in the QR. Read from the system when it allows that, generated when the app sets it.
     * Otherwise it stays null until the person types in their hotspot's details, since a made-up one would
     * produce a QR that doesn't work.
     */
    suspend fun currentNetwork(): NetworkConfig? {
        val saved = store.settings.first().network
        return when (engine.networkControl) {
            NetworkControl.READ_ONLY -> {
                val real = engine.readNetwork() ?: return saved
                if (real != saved) store.setNetwork(real)
                real
            }
            NetworkControl.READ_WRITE -> saved ?: defaultNetwork().also { store.setNetwork(it) }
            NetworkControl.NONE -> saved
        }
    }

    /** Call when returning to the app: the person may have changed the hotspot in system settings. */
    fun refreshNetwork() {
        scope.launch { currentNetwork() }
    }

    val networkControl: NetworkControl get() = engine.networkControl
    val tracksUsage: Boolean get() = engine.tracksUsage

    private suspend fun stopEngineQuietly() {
        try {
            val settings = store.settings.first()
            if (settings.level == PermissionLevel.ROOT) {
                if (settings.ttlValue != null) RootNetworkManager.applyTtl(null)
                if (settings.vpnTethering) RootNetworkManager.applyVpnTethering(false)
                if (settings.adBlockDns) RootNetworkManager.applyAdBlock(false)
            }
        } catch (_: Exception) { }
        try {
            engine.stop()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The state is already final. Phase 6 engines report a real failure through their events.
        }
    }

    private companion object {
        val PERSIST_INTERVAL: Duration = Duration.ofSeconds(30)
    }
}

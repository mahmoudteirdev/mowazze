package com.mowazze3.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.mowazze3.data.proxy.MowazzeProxyServer
import com.mowazze3.domain.Client
import com.mowazze3.domain.ClientKind
import com.mowazze3.domain.EngineEvent
import com.mowazze3.domain.FailReason
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.StartResult
import com.mowazze3.domain.megabitsPerSecond
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TAG = "WifiDirectShareEngine"
private const val POLL_MILLIS = 2_000L
private const val PROXY_PORT = 8245

/**
 * Wi-Fi Direct (P2P) + Embedded Proxy sharing engine.
 *
 * Solves two critical Android limitations:
 * 1. Enables sharing without root on Android 11+, which blocks ordinary apps from standard tethering.
 * 2. Allows sharing an active Wi-Fi connection without turning Wi-Fi off, working as a software repeater
 *    even on devices without hardware STA+AP dual-concurrency (like Samsung phones without Wi-Fi Sharing).
 */
class WifiDirectShareEngine(context: Context) : ShareEngine {
    private val context = context.applicationContext

    override val networkControl: NetworkControl =
        if (Build.VERSION.SDK_INT >= 29) NetworkControl.READ_WRITE else NetworkControl.READ_ONLY

    override val tracksUsage: Boolean = true

    private val p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var proxyServer: MowazzeProxyServer? = null
    private var activeGroup: WifiP2pGroup? = null

    private fun ensureChannel(): WifiP2pManager.Channel? {
        val manager = p2pManager ?: return null
        if (channel == null) {
            channel = manager.initialize(context, Looper.getMainLooper(), null)
        }
        return channel
    }

    override suspend fun start(config: NetworkConfig?): StartResult = withContext(Dispatchers.IO) {
        val manager = p2pManager ?: return@withContext StartResult.Failed(FailReason.UNSUPPORTED)
        val chan = ensureChannel() ?: return@withContext StartResult.Failed(FailReason.UNKNOWN)

        try {
            // First stop any lingering groups
            removeGroupSync(manager, chan)

            // Start the embedded proxy server
            val proxy = MowazzeProxyServer(port = PROXY_PORT)
            proxy.start()
            proxyServer = proxy

            // Create Wi-Fi P2P Group
            val created = createP2pGroup(manager, chan, config)
            if (!created) {
                proxy.stop()
                proxyServer = null
                return@withContext StartResult.Failed(FailReason.UNKNOWN)
            }

            // Retrieve group credentials (SSID and Passphrase)
            val group = requestGroupInfoSync(manager, chan)
            activeGroup = group
            if (group == null) {
                proxy.stop()
                proxyServer = null
                return@withContext StartResult.Failed(FailReason.UNKNOWN)
            }

            Log.i(TAG, "Wi-Fi Direct group created: ${group.networkName}, proxy on port $PROXY_PORT")
            StartResult.Started
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Wi-Fi Direct sharing", e)
            proxyServer?.stop()
            proxyServer = null
            StartResult.Failed(FailReason.UNKNOWN)
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val manager = p2pManager
        val chan = channel
        if (manager != null && chan != null) {
            removeGroupSync(manager, chan)
        }
        activeGroup = null
        proxyServer?.stop()
        proxyServer = null
        Log.i(TAG, "Wi-Fi Direct sharing stopped")
    }

    override suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        val manager = p2pManager ?: return@withContext false
        val chan = ensureChannel() ?: return@withContext false
        val group = requestGroupInfoSync(manager, chan)
        activeGroup = group
        group != null
    }

    override suspend fun readNetwork(): NetworkConfig? = withContext(Dispatchers.IO) {
        val manager = p2pManager ?: return@withContext null
        val chan = ensureChannel() ?: return@withContext null
        val group = activeGroup ?: requestGroupInfoSync(manager, chan)
        activeGroup = group
        if (group != null && !group.networkName.isNullOrBlank()) {
            NetworkConfig(group.networkName, group.passphrase.orEmpty())
        } else {
            null
        }
    }

    override val events: Flow<EngineEvent> = flow {
        var seenRunning = false
        var lastBytes: Long = 0
        var lastTime = SystemClock.elapsedRealtime()

        while (true) {
            val manager = p2pManager
            val chan = channel
            val group = if (manager != null && chan != null) requestGroupInfoSync(manager, chan) else null
            activeGroup = group

            if (group == null) {
                if (seenRunning) {
                    emit(EngineEvent.Stopped(null))
                    return@flow
                }
            } else {
                seenRunning = true
                val now = SystemClock.elapsedRealtime()
                val currentBytes = proxyServer?.totalBytes ?: 0L
                val elapsed = now - lastTime
                val speed = megabitsPerSecond(lastBytes, currentBytes, elapsed)
                lastBytes = currentBytes
                lastTime = now

                val clients = clientsFromGroup(group)
                val online = hasValidatedNetwork(context)

                emit(
                    EngineEvent.Snapshot(
                        clients = clients,
                        hasInternet = online,
                        downMbps = speed,
                        sessionBytes = currentBytes,
                    ),
                )
            }
            delay(POLL_MILLIS)
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private suspend fun createP2pGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        config: NetworkConfig?,
    ): Boolean {
        // Android 10+ supports custom SSID and Passphrase
        if (Build.VERSION.SDK_INT >= 29 && config != null && config.ssid.isNotBlank()) {
            val success = tryCreateWithConfig(manager, channel, config)
            if (success) return true
        }

        // Standard auto-configured group
        return suspendCancellableCoroutine { continuation ->
            manager.createGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "createGroup failed with reason: $reason")
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun tryCreateWithConfig(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        config: NetworkConfig,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val rawSsid = config.ssid.trim()
            val validSsid = if (rawSsid.startsWith("DIRECT-", ignoreCase = true)) rawSsid else "DIRECT-mz-$rawSsid"
            val validPassword = if (config.password.length >= 8) config.password else "12345678"

            val p2pConfig = WifiP2pConfig.Builder()
                .setNetworkName(validSsid)
                .setPassphrase(validPassword)
                .build()

            manager.createGroup(
                channel,
                p2pConfig,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "createGroup with config failed with reason: $reason, falling back")
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception during createGroup with config", e)
            if (continuation.isActive) continuation.resume(false)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun removeGroupSync(manager: WifiP2pManager, channel: WifiP2pManager.Channel) =
        suspendCancellableCoroutine { continuation ->
            manager.removeGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onFailure(reason: Int) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestGroupInfoSync(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ): WifiP2pGroup? = suspendCancellableCoroutine { continuation ->
        try {
            manager.requestGroupInfo(channel) { group ->
                if (continuation.isActive) continuation.resume(group)
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun clientsFromGroup(group: WifiP2pGroup): List<Client> {
        return group.clientList.orEmpty().map { device ->
            val name = device.deviceName?.takeIf { it.isNotBlank() } ?: device.deviceAddress
            Client(
                id = device.deviceAddress,
                name = name,
                ip = "", // Dynamic IP in P2P subnet (192.168.49.x)
                usageBytesToday = 0L,
                kind = clientKind(device),
            )
        }
    }

    private fun clientKind(device: WifiP2pDevice): ClientKind {
        val type = device.primaryDeviceType.orEmpty().lowercase()
        return when {
            "pc" in type || "laptop" in type || "computer" in type -> ClientKind.LAPTOP
            "tablet" in type -> ClientKind.TABLET
            else -> ClientKind.PHONE
        }
    }
}

package com.mowazze3.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.provider.Settings
import com.mowazze3.domain.Client
import com.mowazze3.domain.EngineEvent
import com.mowazze3.domain.FailReason
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.StartResult
import com.mowazze3.domain.megabitsPerSecond
import com.mowazze3.domain.tetherFailureReason
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val TETHERING_WIFI = 0
private const val SHELL_PACKAGE = "com.android.shell"

/**
 * Real Wi-Fi hotspot on Android 8 to 10, through a non-public system call. This reaches the same system
 * code as the hotspot switch in Settings. Android 11 changed that call; see [ShizukuShareEngine].
 *
 * Android 10 doesn't let this app read or set the hotspot's name and password, at any level tested
 * (docs/spikes/RESULTS.md), so the person has to type them in.
 */
class Android10ShareEngine(context: Context, private val store: SettingsStore) : ShareEngine {
    private val context = context.applicationContext

    override val networkControl = NetworkControl.NONE

    override suspend fun start(config: NetworkConfig?): StartResult {
        return try {
            val level = store.settings.first().level
            if (level != PermissionLevel.SHIZUKU && !Settings.System.canWrite(context)) {
                return StartResult.Failed(FailReason.NEEDS_WRITE_SETTINGS)
            }
            HiddenApi.allowAll()
            val code = requestStart(level)
            if (code == 0) StartResult.Started else StartResult.Failed(tetherFailureReason(code, wifiConnected()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StartResult.Failed(failureFrom(e))
        }
    }

    private fun failureFrom(e: Exception): FailReason {
        val cause = (e as? InvocationTargetException)?.targetException ?: e
        val level = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        return when {
            cause is SecurityException && !level -> FailReason.NEEDS_WRITE_SETTINGS
            cause is SecurityException || cause is IllegalStateException -> FailReason.SHIZUKU_EXPIRED
            else -> FailReason.UNKNOWN
        }
    }

    private suspend fun requestStart(level: PermissionLevel?): Int = suspendCancellableCoroutine { continuation ->
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (continuation.isActive) continuation.resume(resultCode)
            }
        }
        val service = connectivityService(level)
        val method = service.method("startTethering")
        method.invoke(service, *method.parameterTypes.map { argFor(it, level, receiver) }.toTypedArray())
    }

    override suspend fun stop() {
        val level = store.settings.first().level
        HiddenApi.allowAll()
        val service = connectivityService(level)
        val method = service.method("stopTethering")
        method.invoke(service, *method.parameterTypes.map { argFor(it, level, null) }.toTypedArray())
    }

    override suspend fun isRunning() = wifiTetherIface(context) != null

    override val events: Flow<EngineEvent> = flow {
        var seenRunning = false
        var waitedTicks = 0
        var baselineBytes: Long? = null
        var lastTx: Long? = null
        var lastTime = 0L
        while (true) {
            val iface = wifiTetherIface(context)
            if (iface == null) {
                if (seenRunning) {
                    emit(EngineEvent.Stopped(null))
                    return@flow
                }
                // The system reports the interface a moment after the start call succeeds.
                if (++waitedTicks >= MAX_STARTUP_TICKS) {
                    emit(EngineEvent.Stopped(FailReason.UNKNOWN))
                    return@flow
                }
            } else {
                seenRunning = true
                val tx = counter(iface, "tx_bytes")
                val total = tx + counter(iface, "rx_bytes")
                val base = baselineBytes ?: total.also { baselineBytes = it }
                val now = SystemClock.elapsedRealtime()
                val online = hasValidatedNetwork(context)
                emit(
                    EngineEvent.Snapshot(
                        clients = readClients(iface),
                        hasInternet = online,
                        // What leaves the hotspot's interface is what the connected devices download.
                        downMbps = megabitsPerSecond(lastTx, tx, now - lastTime),
                        sessionBytes = (total - base).coerceAtLeast(0),
                    ),
                )
                lastTx = tx
                lastTime = now
            }
            delay(POLL_MILLIS)
        }
    }.flowOn(Dispatchers.IO)

    private fun connectivityService(level: PermissionLevel?): Any {
        val binder: IBinder = if (level == PermissionLevel.SHIZUKU) {
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE))
        } else {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, Context.CONNECTIVITY_SERVICE) as IBinder
        }
        return Class.forName("android.net.IConnectivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    private fun Any.method(name: String): Method =
        javaClass.methods.firstOrNull { it.name == name } ?: error("$name isn't available on this Android version")

    /** The signature differs between Android 8 and 10, so arguments are filled in by type. */
    private fun argFor(type: Class<*>, level: PermissionLevel?, receiver: ResultReceiver?): Any? = when (type) {
        Int::class.javaPrimitiveType -> TETHERING_WIFI
        Boolean::class.javaPrimitiveType -> false
        // The system checks that this package belongs to the calling uid, which is shell under Shizuku.
        String::class.java -> if (level == PermissionLevel.SHIZUKU) SHELL_PACKAGE else context.packageName
        ResultReceiver::class.java -> receiver
        else -> null
    }

    private fun counter(iface: String, name: String): Long =
        runCatching { File("/sys/class/net/$iface/statistics/$name").readText().trim().toLong() }.getOrDefault(0)

    private fun readClients(iface: String): List<Client> {
        val lines = runCatching {
            val process = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readLines() }
            process.waitFor(IP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            output
        }.getOrDefault(emptyList())
        return clientsFromNeighbours(context, lines, iface)
    }

    private fun wifiConnected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private companion object {
        const val POLL_MILLIS = 2_000L
        const val MAX_STARTUP_TICKS = 10
        const val IP_TIMEOUT_SECONDS = 2L
    }
}

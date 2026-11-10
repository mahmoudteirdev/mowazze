package com.mowazze3.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.SoftApConfiguration
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.annotation.RequiresApi
import com.mowazze3.domain.EngineEvent
import com.mowazze3.domain.FailReason
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.StartResult
import com.mowazze3.domain.tetherHasUpstream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val TETHERING_WIFI = 0
private const val CONNECTIVITY_SCOPE_GLOBAL = 1
private const val SHELL_PACKAGE = "com.android.shell"
private const val LISTENER_DESCRIPTOR = "android.net.IIntResultListener"

/**
 * Android 11 and newer. Only Shizuku can run the hotspot here: its shell uid holds TETHER_PRIVILEGED and
 * OVERRIDE_WIFI_CONFIG, while an ordinary app can't start it or see who's connected (docs/spikes/RESULTS.md).
 * Tested on Android 13 only. Setting the name and password isn't tried yet, so they're read-only for now.
 */
@RequiresApi(30)
class ShizukuShareEngine(context: Context, private val store: SettingsStore) : ShareEngine {
    private val context = context.applicationContext

    override val networkControl = NetworkControl.READ_ONLY

    // No readable byte counter was found on Android 13, for an app or for shell.
    override val tracksUsage = false

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: RuntimeException) {
        false
    }

    override suspend fun start(config: NetworkConfig?): StartResult {
        if (store.settings.first().level != PermissionLevel.SHIZUKU) return StartResult.Failed(FailReason.UNSUPPORTED)
        if (!shizukuReady()) return StartResult.Failed(FailReason.SHIZUKU_EXPIRED)
        return try {
            val code = callConnector("startTethering")
            if (code == 0) StartResult.Started else StartResult.Failed(com.mowazze3.domain.tetherFailureReason(code, wifiConnected = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            StartResult.Failed(if (cause is SecurityException || cause is IllegalStateException) FailReason.SHIZUKU_EXPIRED else FailReason.UNKNOWN)
        }
    }

    override suspend fun stop() {
        if (shizukuReady()) callConnector("stopTethering")
    }

    override suspend fun isRunning() = wifiTetherIface(context) != null

    override suspend fun readNetwork(): NetworkConfig? = withContext(Dispatchers.IO) {
        if (!shizukuReady()) return@withContext null
        try {
            HiddenApi.allowAll()
            val wifi = aidl("android.net.wifi.IWifiManager", service(Context.WIFI_SERVICE))
            val config = wifi.javaClass.getMethod("getSoftApConfiguration").invoke(wifi) as? SoftApConfiguration
            @Suppress("DEPRECATION")
            config?.ssid?.let { NetworkConfig(it, config.passphrase.orEmpty()) }
        } catch (_: Exception) {
            null
        }
    }

    override val events: Flow<EngineEvent> = flow {
        var seenRunning = false
        var waitedTicks = 0
        while (true) {
            val iface = wifiTetherIface(context)
            if (iface == null) {
                if (seenRunning) {
                    emit(EngineEvent.Stopped(null))
                    return@flow
                }
                if (++waitedTicks >= MAX_STARTUP_TICKS) {
                    emit(EngineEvent.Stopped(FailReason.UNKNOWN))
                    return@flow
                }
            } else {
                seenRunning = true
                if (!shizukuReady()) {
                    // Shizuku stops on reboot and can stop at any time. The hotspot itself may carry on.
                    emit(EngineEvent.Stopped(FailReason.SHIZUKU_EXPIRED))
                    return@flow
                }
                // The phone being online isn't enough: the hotspot can come up with no upstream behind it.
                val hasUpstream = tetherHasUpstream(shell("dumpsys", "tethering")) ?: true
                emit(
                    EngineEvent.Snapshot(
                        clients = clientsFromNeighbours(context, shell("ip", "neigh", "show"), iface),
                        hasInternet = hasUpstream && hasValidatedNetwork(context),
                        downMbps = null,
                        sessionBytes = 0,
                    ),
                )
            }
            delay(POLL_MILLIS)
        }
    }.flowOn(Dispatchers.IO)

    private fun service(name: String): IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name))

    private fun aidl(interfaceName: String, binder: IBinder): Any =
        Class.forName("$interfaceName\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!

    /** Runs a command as shell. The library keeps this method private, so it's reached by reflection. */
    private fun shell(vararg command: String): List<String> = try {
        val newProcess = Shizuku::class.java
            .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .apply { isAccessible = true }
        val process = newProcess.invoke(null, command, null, null) as Process
        try {
            process.inputStream.bufferedReader().use { it.readLines() }
        } finally {
            process.destroy()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Signatures vary between Android versions, so arguments are filled in by type. */
    private suspend fun callConnector(methodName: String): Int = suspendCancellableCoroutine { continuation ->
        HiddenApi.allowAll()
        val connector = aidl("android.net.ITetheringConnector", service("tethering"))
        val method = connector.javaClass.methods.first { it.name == methodName }
        val listener = resultListener { code -> if (continuation.isActive) continuation.resume(code) }
        var firstString = true
        val args = method.parameterTypes.map { type ->
            when {
                type == Int::class.javaPrimitiveType -> TETHERING_WIFI
                type == String::class.java -> if (firstString) SHELL_PACKAGE.also { firstString = false } else null
                type.name == "android.net.TetheringRequestParcel" -> requestParcel(type)
                type.name == LISTENER_DESCRIPTOR -> listener
                else -> null
            }
        }
        method.invoke(connector, *args.toTypedArray())
    }

    private fun requestParcel(type: Class<*>): Any {
        val parcel = type.getConstructor().newInstance()
        type.getField("tetheringType").setInt(parcel, TETHERING_WIFI)
        runCatching { type.getField("connectivityScope").setInt(parcel, CONNECTIVITY_SCOPE_GLOBAL) }
        return parcel
    }

    /** The system calls back through this hidden interface, so it's implemented by hand on a plain Binder. */
    private fun resultListener(onResult: (Int) -> Unit): Any {
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == FIRST_CALL_TRANSACTION) {
                    data.enforceInterface(LISTENER_DESCRIPTOR)
                    onResult(data.readInt())
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        val type = Class.forName(LISTENER_DESCRIPTOR)
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "onResult" -> {
                    onResult(args[0] as Int)
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "TetherResultListener"
                else -> null
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 2_000L
        const val MAX_STARTUP_TICKS = 10
    }
}

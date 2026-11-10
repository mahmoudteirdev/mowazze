package com.mowazze3.data

import android.content.Context
import android.os.Build
import com.mowazze3.domain.EngineEvent
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.StartResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Dynamically delegates to the best engine:
 * - When [PermissionLevel.SHIZUKU] is selected, uses the native Wi-Fi tethering system call via Shizuku.
 * - When [PermissionLevel.NO_ROOT] is selected, uses [WifiDirectShareEngine] (Wi-Fi Direct + Proxy repeater)
 *   which runs without root on all Android versions (including 11+) and works without disconnecting active Wi-Fi.
 * - In debug builds, supports [FakeShareEngine] when requested.
 */
class AdaptiveShareEngine(
    private val context: Context,
    private val store: SettingsStore,
    private val fake: ShareEngine? = null,
) : ShareEngine {
    private val wifiDirect by lazy { WifiDirectShareEngine(context) }
    private val shizuku by lazy {
        if (Build.VERSION.SDK_INT >= 30) ShizukuShareEngine(context, store)
        else Android10ShareEngine(context, store)
    }
    private val android10Hotspot by lazy { Android10ShareEngine(context, store) }

    suspend fun currentEngine(): ShareEngine {
        if (fake != null) return fake
        val level = store.settings.first().level
        return when (level) {
            PermissionLevel.SHIZUKU -> shizuku
            else -> wifiDirect
        }
    }

    override val networkControl: NetworkControl
        get() = wifiDirect.networkControl

    override val tracksUsage: Boolean
        get() = true

    override suspend fun start(config: NetworkConfig?): StartResult = currentEngine().start(config)

    override suspend fun stop() {
        if (fake != null) {
            fake.stop()
            return
        }
        wifiDirect.stop()
        if (Build.VERSION.SDK_INT >= 30) {
            (shizuku as? ShizukuShareEngine)?.stop()
        } else {
            android10Hotspot.stop()
        }
    }

    override suspend fun isRunning(): Boolean = currentEngine().isRunning()

    override suspend fun readNetwork(): NetworkConfig? = currentEngine().readNetwork()

    override val events: Flow<EngineEvent> = flow {
        val engine = currentEngine()
        engine.events.collect { emit(it) }
    }
}

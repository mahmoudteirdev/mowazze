package com.mowazze3.domain

import kotlinx.coroutines.flow.Flow

sealed interface StartResult {
    data object Started : StartResult
    data class Failed(val reason: FailReason) : StartResult
}

sealed interface EngineEvent {
    /** [sessionBytes] counts up from zero when the engine starts. */
    data class Snapshot(
        val clients: List<Client>,
        val hasInternet: Boolean,
        val downMbps: Float?,
        val sessionBytes: Long,
    ) : EngineEvent

    /** Sharing ended without the app asking. A null [reason] means the person turned it off elsewhere. */
    data class Stopped(val reason: FailReason?) : EngineEvent
}

/** What the app may do with the hotspot's name and password. */
enum class NetworkControl {
    /** The app sets them. Saving restarts the hotspot with the new values. */
    READ_WRITE,

    /** They come from the system. To change them, the person uses the system's hotspot screen. */
    READ_ONLY,

    /** The system hides them, so the person types them in. Saved values only describe the hotspot, for the QR. */
    NONE,
}

/** How the hotspot actually runs. */
interface ShareEngine {
    val networkControl: NetworkControl

    /** False when no source for shared bytes exists, so usage must show as empty and not as zero. */
    val tracksUsage: Boolean get() = true

    /** Collected only while the engine is running. */
    val events: Flow<EngineEvent>

    /** With [NetworkControl.READ_ONLY], the hotspot's real name and password. Null if they can't be read now. */
    suspend fun readNetwork(): NetworkConfig? = null

    /** [config] is only used with [NetworkControl.READ_WRITE]. */
    suspend fun start(config: NetworkConfig?): StartResult
    suspend fun stop()

    /** True if sharing is on, for example after the app's process was killed. */
    suspend fun isRunning(): Boolean
}

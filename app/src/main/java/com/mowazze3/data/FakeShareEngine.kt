package com.mowazze3.data

import com.mowazze3.domain.Client
import com.mowazze3.domain.ClientKind
import com.mowazze3.domain.EngineEvent
import com.mowazze3.domain.FailReason
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.NetworkControl
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.StartResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/**
 * Debug only (see MowazzeApp): exercises the screens in every state without touching the hotspot.
 * The public vars let a debug tool force failures and offline states.
 */
class FakeShareEngine : ShareEngine {
    var nextFailure: FailReason? = null
    var hasInternet: Boolean = true

    private var running = false

    override val networkControl = NetworkControl.READ_WRITE

    override val events = flow {
        var bytes = 0L
        var tick = 0
        while (true) {
            val clients = SAMPLE_CLIENTS.take(tick / TICKS_PER_NEW_CLIENT)
            emit(
                EngineEvent.Snapshot(
                    clients = clients.map { it.copy(usageBytesToday = bytes / SAMPLE_CLIENTS.size) },
                    hasInternet = hasInternet,
                    downMbps = 12.4f,
                    sessionBytes = bytes,
                ),
            )
            delay(TICK_MILLIS)
            tick++
            if (hasInternet) bytes += clients.size * BYTES_PER_TICK_PER_CLIENT
        }
    }

    override suspend fun start(config: NetworkConfig?): StartResult {
        delay(START_DELAY_MILLIS)
        nextFailure?.let {
            nextFailure = null
            return StartResult.Failed(it)
        }
        running = true
        return StartResult.Started
    }

    override suspend fun stop() {
        running = false
    }

    override suspend fun isRunning() = running

    private companion object {
        const val START_DELAY_MILLIS = 1_200L
        const val TICK_MILLIS = 2_000L
        const val TICKS_PER_NEW_CLIENT = 3
        const val BYTES_PER_TICK_PER_CLIENT = 2_500_000L

        val SAMPLE_CLIENTS = listOf(
            Client("fake-laptop", "لابتوب", "192.168.43.10", 0, kind = ClientKind.LAPTOP),
            Client("fake-phone", "تلفون", "192.168.43.11", 0, kind = ClientKind.PHONE),
        )
    }
}

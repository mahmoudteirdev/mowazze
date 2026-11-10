package com.mowazze3.domain

import java.util.Locale

const val NO_VALUE = "—"

private const val MB = 1_000_000L
private const val GB = 1_000_000_000L

/** Decimal units, the way data plans are sold. Numbers stay Latin and these labels stay in English, as designed. */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < MB -> "1 MB" // Something was used, so it doesn't read as nothing.
    bytes < GB -> "${(bytes + MB / 2) / MB} MB"
    else -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / GB)
}

fun formatSpeed(mbps: Float): String = String.format(Locale.US, "%.1f Mb/s", mbps)

data class HomeStats(val devices: String, val usage: String, val speed: String) {
    companion object {
        /** Everything is empty unless sharing is on. Without internet only the speed is, since devices stay connected. */
        fun of(state: ShareState, usageToday: Long, tracksUsage: Boolean = true): HomeStats =
            if (state is ShareState.On) {
                HomeStats(
                    devices = state.connectedCount.toString(),
                    usage = if (tracksUsage) formatBytes(usageToday) else NO_VALUE,
                    speed = state.downMbps?.let(::formatSpeed) ?: NO_VALUE,
                )
            } else {
                HomeStats(NO_VALUE, NO_VALUE, NO_VALUE)
            }
    }
}

package com.mowazze3.domain

data class Neighbour(val ip: String, val mac: String)

private val USABLE_STATES = setOf("REACHABLE", "STALE", "DELAY", "PROBE", "PERMANENT")

/**
 * Reads `ip neigh show` output and keeps the devices on [iface]. An IPv4 line looks like:
 * `192.168.43.10 dev swlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE`
 * IPv6 entries are skipped because the same device also has an IPv4 one.
 */
fun parseNeighbours(lines: List<String>, iface: String): List<Neighbour> = lines.mapNotNull { line ->
    val parts = line.trim().split(Regex("\\s+"))
    val ip = parts.firstOrNull() ?: return@mapNotNull null
    if (':' in ip) return@mapNotNull null
    val device = parts.getOrNull(parts.indexOf("dev") + 1).takeIf { "dev" in parts }
    if (device != iface) return@mapNotNull null
    val mac = parts.getOrNull(parts.indexOf("lladdr") + 1).takeIf { "lladdr" in parts } ?: return@mapNotNull null
    if (parts.last() !in USABLE_STATES) return@mapNotNull null
    Neighbour(ip, mac)
}.distinctBy { it.mac }

/**
 * Reads `dumpsys tethering`. A line like `Current upstream interface(s): [wlan0]` means the hotspot has a
 * source of internet; `null` or `[]` means it's up with nothing behind it. Null if the line isn't there.
 */
fun tetherHasUpstream(lines: List<String>): Boolean? {
    val value = lines.firstOrNull { it.trim().startsWith("Current upstream interface(s):") }
        ?.substringAfter(":")
        ?.trim()
        ?: return null
    return value != "null" && value != "[]" && value.isNotEmpty()
}

/**
 * The hotspot is up with no internet while the phone itself is on Wi-Fi: the phone won't pass Wi-Fi on to the
 * hotspot (common on Samsung). Only the system hotspot has that problem; Wi-Fi Direct works on Wi-Fi.
 */
fun offersWifiNotSharedFix(level: PermissionLevel?, state: ShareState, phoneOnWifi: Boolean): Boolean =
    level == PermissionLevel.SHIZUKU && state is ShareState.On && !state.hasInternet && phoneOnWifi

/** Values of ConnectivityManager's TETHER_ERROR_* constants that have their own message. */
private const val TETHER_ERROR_UNSUPPORTED = 3
private const val TETHER_ERROR_PROVISION_FAILED = 11
private const val TETHER_ERROR_ENTITLEMENT_UNKNOWN = 13

/** Some phones can't share while on Wi-Fi, and there's no way to ask first, so it's the likely cause then. */
fun tetherFailureReason(code: Int, wifiConnected: Boolean): FailReason = when (code) {
    TETHER_ERROR_UNSUPPORTED -> FailReason.UNSUPPORTED
    TETHER_ERROR_PROVISION_FAILED, TETHER_ERROR_ENTITLEMENT_UNKNOWN -> FailReason.CARRIER_BLOCKED
    else -> if (wifiConnected) FailReason.WIFI_CONNECTED else FailReason.UNKNOWN
}

/** Megabits per second between two readings of a byte counter. Null if there's no earlier reading to compare. */
fun megabitsPerSecond(bytesBefore: Long?, bytesNow: Long, elapsedMillis: Long): Float? {
    if (bytesBefore == null || elapsedMillis <= 0 || bytesNow < bytesBefore) return null
    return (bytesNow - bytesBefore) * 8f / 1_000f / elapsedMillis
}

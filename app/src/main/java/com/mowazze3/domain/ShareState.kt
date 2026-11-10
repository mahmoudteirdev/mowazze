package com.mowazze3.domain

/** The three permission levels. Orange UI accent is reserved for ROOT-only features. */
enum class PermissionLevel { ROOT, SHIZUKU, NO_ROOT }

enum class FailReason {
    WIFI_CONNECTED,      // designed: "الواي فاي مشغّل"
    CARRIER_BLOCKED,     // text only (other reasons list)
    UNSUPPORTED,         // text only
    SHIZUKU_EXPIRED,     // text only
    TIMEOUT,             // proposed: 15 s generic failure
    SYSTEM_STOPPED,      // proposed: the system ended the foreground service while sharing
    NEEDS_WRITE_SETTINGS, // no root on Android 10: the "modify system settings" access isn't granted
    UNKNOWN,
}

data class Client(
    val id: String,              // see handoff: MAC may be randomized
    val name: String,
    val ip: String,
    val usageBytesToday: Long,
    val blocked: Boolean = false,
    val speedLimit: String? = null,
    val kind: ClientKind = ClientKind.PHONE,
)
enum class ClientKind { LAPTOP, PHONE, TABLET }

/** Single source of truth. Lives in the foreground service / repository, NOT in a screen. */
sealed interface ShareState {
    data object Off : ShareState
    data object Starting : ShareState                 // taps on the power button are ignored; 15 s timeout
    data class On(
        val ssid: String,
        val clients: List<Client>,
        val hasInternet: Boolean,                     // false -> "المشاركة شغّالة بدون إنترنت" banner, speed "—"
        val downMbps: Float?,
    ) : ShareState
    data class Failed(val reason: FailReason) : ShareState
}

/** Blocked devices are listed with a badge, but they don't count as connected. */
val ShareState.On.connectedCount: Int get() = clients.count { !it.blocked }

/**
 * What each level can do today. UI reads this. Blocking, speed limits and per-device usage are false
 * everywhere: no engine implements them, and no source for them was found without root (docs/spikes/RESULTS.md).
 */
data class Capabilities(
    val blockClient: Boolean,
    val limitSpeed: Boolean,
    val perClientUsage: Boolean,       // verify per level (see spikes)
    val setTtl: Boolean,
)
fun PermissionLevel.capabilities() = when (this) {
    PermissionLevel.ROOT -> Capabilities(false, false, false, true)
    PermissionLevel.SHIZUKU -> Capabilities(false, false, false, false)
    PermissionLevel.NO_ROOT -> Capabilities(false, false, false, false)
}

/** Without root, blocked actions open the "needs higher permissions" sheet instead of being disabled. */
val PermissionLevel.canUpgradeForControl: Boolean get() = this == PermissionLevel.NO_ROOT

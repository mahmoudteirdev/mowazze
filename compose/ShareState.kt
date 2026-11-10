package com.mowazze3.domain

/** The three permission levels. Orange UI accent is reserved for ROOT-only features. */
enum class PermissionLevel { ROOT, SHIZUKU, NO_ROOT }

enum class FailReason {
    WIFI_CONNECTED,      // designed: "الواي فاي مشغّل"
    CARRIER_BLOCKED,     // text only (other reasons list)
    UNSUPPORTED,         // text only
    SHIZUKU_EXPIRED,     // text only
    TIMEOUT,             // proposed: 15 s generic failure
    UNKNOWN,
}

data class Client(
    val id: String,              // see handoff: MAC may be randomized
    val name: String,
    val ip: String,
    val usageBytesToday: Long,
    val blocked: Boolean = false,
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

/** What each level may do. UI reads this; it never checks the level inline. */
data class Capabilities(
    val blockClient: Boolean,
    val limitSpeed: Boolean,
    val perClientUsage: Boolean,       // verify per level (see spikes)
    val setTtl: Boolean,
)
fun PermissionLevel.capabilities() = when (this) {
    PermissionLevel.ROOT -> Capabilities(true, true, true, true)
    PermissionLevel.SHIZUKU -> Capabilities(true, true, true, false)
    PermissionLevel.NO_ROOT -> Capabilities(false, false, false, false)   // placeholder: verify what NO_ROOT can really do
}

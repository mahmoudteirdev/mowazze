package com.mowazze3.domain

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    /** Null until the person finishes first-run setup. */
    val level: PermissionLevel? = null,
    /** Null until the first time sharing starts or settings are saved. */
    val network: NetworkConfig? = null,
    val notifyNewDevice: Boolean = true,
    val dataQuotaBytes: Long? = 5_000_000_000L,
    val alertQuota80: Boolean = false,
    val maxDevices: Int? = null,
    val blockedClients: Set<String> = emptySet(),
    val speedLimits: Map<String, String> = emptyMap(),
    val ttlValue: Int? = null,
    val vpnTethering: Boolean = false,
    val adBlockDns: Boolean = false,
    val customMac: String? = null,
)

interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setLevel(level: PermissionLevel)

    /** Sends the person back through first-run setup ("change level"). */
    suspend fun clearLevel()

    suspend fun setNetwork(network: NetworkConfig)
    suspend fun setNotifyNewDevice(enabled: Boolean)
    suspend fun setDataQuota(bytes: Long?)
    suspend fun setAlertQuota80(enabled: Boolean)
    suspend fun setMaxDevices(limit: Int?)
    suspend fun setBlockedClients(clients: Set<String>)
    suspend fun setSpeedLimits(limits: Map<String, String>)
    suspend fun setTtlValue(ttl: Int?)
    suspend fun setVpnTethering(enabled: Boolean)
    suspend fun setAdBlockDns(enabled: Boolean)
    suspend fun setCustomMac(mac: String?)

    suspend fun loadUsage(): DailyUsage?
    suspend fun saveUsage(usage: DailyUsage)
}

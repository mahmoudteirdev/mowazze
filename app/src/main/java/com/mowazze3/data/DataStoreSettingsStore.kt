package com.mowazze3.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mowazze3.domain.AppSettings
import com.mowazze3.domain.DailyUsage
import com.mowazze3.domain.NetworkConfig
import com.mowazze3.domain.PermissionLevel
import com.mowazze3.domain.SettingsStore
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val dataStore = context.applicationContext.dataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<AppSettings> = preferences.map { p ->
        val ssid = p[SSID]
        val password = p[PASSWORD]
        val limitsRaw = p[SPEED_LIMITS].orEmpty()
        val limits = limitsRaw.split(";")
            .mapNotNull {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()

        AppSettings(
            level = p[LEVEL]?.let { name -> PermissionLevel.entries.firstOrNull { it.name == name } },
            network = if (ssid != null && password != null) NetworkConfig(ssid, password) else null,
            notifyNewDevice = p[NOTIFY_NEW_DEVICE] ?: true,
            dataQuotaBytes = p[DATA_QUOTA_BYTES] ?: 5_000_000_000L,
            alertQuota80 = p[ALERT_QUOTA_80] ?: false,
            maxDevices = p[MAX_DEVICES],
            blockedClients = p[BLOCKED_CLIENTS] ?: emptySet(),
            speedLimits = limits,
            ttlValue = p[TTL_VALUE],
            vpnTethering = p[VPN_TETHERING] ?: false,
            adBlockDns = p[ADBLOCK_DNS] ?: false,
            customMac = p[CUSTOM_MAC],
        )
    }

    override suspend fun setLevel(level: PermissionLevel) {
        dataStore.edit { it[LEVEL] = level.name }
    }

    override suspend fun clearLevel() {
        dataStore.edit { it.remove(LEVEL) }
    }

    override suspend fun setNetwork(network: NetworkConfig) {
        dataStore.edit {
            it[SSID] = network.ssid
            it[PASSWORD] = network.password
        }
    }

    override suspend fun setNotifyNewDevice(enabled: Boolean) {
        dataStore.edit { it[NOTIFY_NEW_DEVICE] = enabled }
    }

    override suspend fun setDataQuota(bytes: Long?) {
        dataStore.edit {
            if (bytes != null) it[DATA_QUOTA_BYTES] = bytes else it.remove(DATA_QUOTA_BYTES)
        }
    }

    override suspend fun setAlertQuota80(enabled: Boolean) {
        dataStore.edit { it[ALERT_QUOTA_80] = enabled }
    }

    override suspend fun setMaxDevices(limit: Int?) {
        dataStore.edit {
            if (limit != null) it[MAX_DEVICES] = limit else it.remove(MAX_DEVICES)
        }
    }

    override suspend fun setBlockedClients(clients: Set<String>) {
        dataStore.edit { it[BLOCKED_CLIENTS] = clients }
    }

    override suspend fun setSpeedLimits(limits: Map<String, String>) {
        dataStore.edit {
            it[SPEED_LIMITS] = limits.entries.joinToString(";") { (k, v) -> "$k=$v" }
        }
    }

    override suspend fun setTtlValue(ttl: Int?) {
        dataStore.edit {
            if (ttl != null) it[TTL_VALUE] = ttl else it.remove(TTL_VALUE)
        }
    }

    override suspend fun setVpnTethering(enabled: Boolean) {
        dataStore.edit { it[VPN_TETHERING] = enabled }
    }

    override suspend fun setAdBlockDns(enabled: Boolean) {
        dataStore.edit { it[ADBLOCK_DNS] = enabled }
    }

    override suspend fun setCustomMac(mac: String?) {
        dataStore.edit {
            if (mac != null) it[CUSTOM_MAC] = mac else it.remove(CUSTOM_MAC)
        }
    }

    override suspend fun loadUsage(): DailyUsage? {
        val p = preferences.first()
        val day = p[USAGE_DAY] ?: return null
        val zone = p[USAGE_ZONE] ?: return null
        val bytes = p[USAGE_BYTES] ?: return null
        // A corrupt entry only costs today's count, so it isn't worth failing over.
        return runCatching { DailyUsage(LocalDate.parse(day), ZoneId.of(zone), bytes) }.getOrNull()
    }

    override suspend fun saveUsage(usage: DailyUsage) {
        dataStore.edit {
            it[USAGE_DAY] = usage.day.toString()
            it[USAGE_ZONE] = usage.zone.id
            it[USAGE_BYTES] = usage.bytes
        }
    }

    private companion object {
        val LEVEL = stringPreferencesKey("level")
        val SSID = stringPreferencesKey("ssid")
        val PASSWORD = stringPreferencesKey("password")
        val NOTIFY_NEW_DEVICE = booleanPreferencesKey("notify_new_device")
        val DATA_QUOTA_BYTES = longPreferencesKey("data_quota_bytes")
        val ALERT_QUOTA_80 = booleanPreferencesKey("alert_quota_80")
        val MAX_DEVICES = intPreferencesKey("max_devices")
        val BLOCKED_CLIENTS = stringSetPreferencesKey("blocked_clients")
        val SPEED_LIMITS = stringPreferencesKey("speed_limits")
        val TTL_VALUE = intPreferencesKey("ttl_value")
        val VPN_TETHERING = booleanPreferencesKey("vpn_tethering")
        val ADBLOCK_DNS = booleanPreferencesKey("adblock_dns")
        val CUSTOM_MAC = stringPreferencesKey("custom_mac")
        val USAGE_DAY = stringPreferencesKey("usage_day")
        val USAGE_ZONE = stringPreferencesKey("usage_zone")
        val USAGE_BYTES = longPreferencesKey("usage_bytes")
    }
}

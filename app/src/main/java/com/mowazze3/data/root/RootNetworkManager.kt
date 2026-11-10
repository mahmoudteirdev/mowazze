package com.mowazze3.data.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom

object RootNetworkManager {
    private const val TAG = "RootNetworkManager"
    private var originalMac: String? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        val res = runSuCommand("id")
        res.success && res.output.contains("uid=0")
    }

    suspend fun applyTtl(ttl: Int?): Boolean = withContext(Dispatchers.IO) {
        if (ttl != null && ttl in 1..255) {
            val cmd = buildString {
                append("iptables -t mangle -D POSTROUTING -j TTL --ttl-set $ttl 2>/dev/null; ")
                append("iptables -t mangle -A POSTROUTING -j TTL --ttl-set $ttl; ")
                append("ip6tables -t mangle -D POSTROUTING -j HL --hl-set $ttl 2>/dev/null; ")
                append("ip6tables -t mangle -A POSTROUTING -j HL --hl-set $ttl 2>/dev/null")
            }
            runSuCommand(cmd).success
        } else {
            val cmd = "iptables -t mangle -F POSTROUTING 2>/dev/null; ip6tables -t mangle -F POSTROUTING 2>/dev/null"
            runSuCommand(cmd).success
        }
    }

    suspend fun applyVpnTethering(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (enabled) {
            buildString {
                append("echo 1 > /proc/sys/net/ipv4/ip_forward; ")
                append("iptables -t nat -A POSTROUTING -o tun+ -j MASQUERADE; ")
                append("iptables -A FORWARD -o tun+ -j ACCEPT; ")
                append("iptables -A FORWARD -i tun+ -m state --state RELATED,ESTABLISHED -j ACCEPT")
            }
        } else {
            buildString {
                append("iptables -t nat -D POSTROUTING -o tun+ -j MASQUERADE 2>/dev/null; ")
                append("iptables -D FORWARD -o tun+ -j ACCEPT 2>/dev/null; ")
                append("iptables -D FORWARD -i tun+ -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null")
            }
        }
        runSuCommand(cmd).success
    }

    suspend fun applyAdBlock(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val dnsIp = "94.140.14.14" // AdGuard Default DNS
        val cmd = if (enabled) {
            buildString {
                append("iptables -t nat -A PREROUTING -p udp --dport 53 -j DNAT --to $dnsIp:53; ")
                append("iptables -t nat -A PREROUTING -p tcp --dport 53 -j DNAT --to $dnsIp:53")
            }
        } else {
            buildString {
                append("iptables -t nat -D PREROUTING -p udp --dport 53 -j DNAT --to $dnsIp:53 2>/dev/null; ")
                append("iptables -t nat -D PREROUTING -p tcp --dport 53 -j DNAT --to $dnsIp:53 2>/dev/null")
            }
        }
        runSuCommand(cmd).success
    }

    suspend fun blockClient(mac: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidMac(mac)) return@withContext false
        val cmd = "iptables -I FORWARD -m mac --mac-source $mac -j DROP"
        runSuCommand(cmd).success
    }

    suspend fun unblockClient(mac: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidMac(mac)) return@withContext false
        val cmd = "iptables -D FORWARD -m mac --mac-source $mac -j DROP 2>/dev/null"
        runSuCommand(cmd).success
    }

    suspend fun changeMac(iface: String = "wlan0", newMac: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidMac(newMac)) return@withContext false
        if (originalMac == null) {
            originalMac = getMac(iface)
        }
        val cmd = "ip link set dev $iface down && ip link set dev $iface address $newMac && ip link set dev $iface up"
        runSuCommand(cmd).success
    }

    suspend fun restoreOriginalMac(iface: String = "wlan0"): Boolean = withContext(Dispatchers.IO) {
        val mac = originalMac ?: return@withContext false
        val cmd = "ip link set dev $iface down && ip link set dev $iface address $mac && ip link set dev $iface up"
        val ok = runSuCommand(cmd).success
        if (ok) originalMac = null
        ok
    }

    suspend fun getMac(iface: String = "wlan0"): String? = withContext(Dispatchers.IO) {
        val res = runSuCommand("cat /sys/class/net/$iface/address 2>/dev/null")
        if (res.success && isValidMac(res.output.trim())) res.output.trim() else null
    }

    fun generateRandomMac(): String {
        val random = SecureRandom()
        val mac = ByteArray(6)
        random.nextBytes(mac)
        // Set locally administered, unicast (bit 1 of byte 0 = 1, bit 0 of byte 0 = 0)
        mac[0] = ((mac[0].toInt() or 0x02) and 0xFE.inv().inv() and 0xFE).toByte()
        return mac.joinToString(":") { String.format("%02x", it) }
    }

    private fun isValidMac(mac: String): Boolean {
        return Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$").matches(mac)
    }

    private fun runSuCommand(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "Command '$command' failed (code $exitCode): $error")
            }
            CommandResult(exitCode == 0, output, error)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: $command", e)
            CommandResult(false, "", e.message.orEmpty())
        }
    }

    private data class CommandResult(val success: Boolean, val output: String, val error: String)
}

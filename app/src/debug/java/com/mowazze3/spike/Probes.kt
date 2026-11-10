package com.mowazze3.spike

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

enum class Verdict { OK, NO, INFO }

data class ProbeResult(val id: String, val verdict: Verdict, val detail: String)

const val SPIKE_TAG = "MowazzeSpike"

/**
 * Results go to logcat so they can be collected with `adb logcat -s MowazzeSpike`. Never put an
 * SSID, password, IP or MAC in a detail string: report presence and counts only.
 */
fun ProbeResult.log() = also { Log.i(SPIKE_TAG, "$id|$verdict|$detail") }

fun Throwable.describe(): String {
    val root = if (this is InvocationTargetException) targetException ?: this else this
    return "${root.javaClass.simpleName}: ${root.message?.take(160)}"
}

private inline fun probe(id: String, block: () -> Pair<Verdict, String>): ProbeResult =
    try {
        val (verdict, detail) = block()
        ProbeResult(id, verdict, detail)
    } catch (t: Throwable) {
        ProbeResult(id, Verdict.NO, t.describe())
    }.log()

private val AP_IFACE = Regex("^(swlan|softap|ap|wlan1|wigig).*")

fun passiveProbes(context: Context): List<ProbeResult> = listOf(
    device(),
    suBinary(),
    packageInstalled(context, "level.shizuku.installed", "moe.shizuku.privileged.api"),
    ShizukuProbes.status(),
    packageInstalled(context, "level.magisk.installed", "com.topjohnwu.magisk"),
    permission(context, "perm.fineLocation", Manifest.permission.ACCESS_FINE_LOCATION),
    permission(context, "perm.nearbyWifi", "android.permission.NEARBY_WIFI_DEVICES", minSdk = 33),
    permission(context, "perm.notifications", "android.permission.POST_NOTIFICATIONS", minSdk = 33),
    writeSettings(context),
    usageStats(context),
    locationEnabled(context),
    activeNetwork(context),
    staApConcurrency(context),
    apStateBroadcast(context),
    tetherStateBroadcast(context),
    apStateHidden(context),
    apConfigHidden(context),
    interfaces(),
    arpTable(),
    ipNeigh(),
    ifaceStats(),
)

private fun device() = probe("device") {
    Verdict.INFO to "${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) targetSdk=36"
}

/** Looks for the binary only. Running su could pop up a grant dialog, and the root flow isn't designed yet. */
private fun suBinary() = probe("level.root.suBinary") {
    val hit = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk").filter { File(it).exists() }
    if (hit.isEmpty()) Verdict.NO to "no su binary found" else Verdict.OK to hit.joinToString()
}

private fun packageInstalled(context: Context, id: String, pkg: String) = probe(id) {
    try {
        context.packageManager.getPackageInfo(pkg, 0)
        Verdict.OK to "installed"
    } catch (_: PackageManager.NameNotFoundException) {
        Verdict.NO to "not installed"
    }
}

private fun permission(context: Context, id: String, name: String, minSdk: Int = 1) = probe(id) {
    when {
        Build.VERSION.SDK_INT < minSdk -> Verdict.INFO to "not needed below SDK $minSdk"
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED -> Verdict.OK to "granted"
        else -> Verdict.NO to "not granted"
    }
}

private fun writeSettings(context: Context) = probe("perm.writeSettings") {
    if (Settings.System.canWrite(context)) Verdict.OK to "granted" else Verdict.NO to "not granted (special access)"
}

private fun usageStats(context: Context) = probe("perm.usageStats") {
    val ops = context.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    if (mode == AppOpsManager.MODE_ALLOWED) Verdict.OK to "granted" else Verdict.NO to "not granted (special access)"
}

private fun locationEnabled(context: Context) = probe("device.locationEnabled") {
    val on = LocationManagerCompat.isLocationEnabled(context.getSystemService(android.location.LocationManager::class.java))
    (if (on) Verdict.OK else Verdict.NO) to "location services on=$on"
}

private fun activeNetwork(context: Context) = probe("net.active") {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    if (caps == null) {
        Verdict.NO to "no active network"
    } else {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        Verdict.INFO to "transport=$transport validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
    }
}

private fun staApConcurrency(context: Context) = probe("wifi.staApConcurrency") {
    if (Build.VERSION.SDK_INT < 30) {
        Verdict.INFO to "API needs SDK 30; must try and fail on this device"
    } else {
        val wifi = context.getSystemService(WifiManager::class.java)
        Verdict.INFO to "supported=${wifi.isStaApConcurrencySupported}"
    }
}

private fun apStateBroadcast(context: Context) = probe("ap.stateBroadcast") {
    val intent = ContextCompat.registerReceiver(
        context, null, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    if (intent == null) {
        Verdict.NO to "no sticky broadcast"
    } else {
        val state = intent.getIntExtra("wifi_state", -1)
        Verdict.OK to "readable; state=$state (11 off, 13 on)"
    }
}

private fun tetherStateBroadcast(context: Context) = probe("tether.stateBroadcast") {
    val intent = ContextCompat.registerReceiver(
        context, null, IntentFilter("android.net.conn.TETHER_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    if (intent == null) {
        Verdict.NO to "no sticky broadcast"
    } else {
        val active = intent.getStringArrayListExtra("tetherArray").orEmpty()
        Verdict.OK to "readable; active ifaces=$active"
    }
}

private fun apStateHidden(context: Context) = probe("ap.getWifiApState") {
    val wifi = context.getSystemService(WifiManager::class.java)
    val state = WifiManager::class.java.getMethod("getWifiApState").invoke(wifi)
    Verdict.OK to "readable via hidden API; state=$state"
}

/** The QR needs the real SSID and password. Reports only whether they can be read. */
private fun apConfigHidden(context: Context) = probe("ap.readConfig") {
    val wifi = context.getSystemService(WifiManager::class.java)
    val name = if (Build.VERSION.SDK_INT >= 30) "getSoftApConfiguration" else "getWifiApConfiguration"
    val config = WifiManager::class.java.getMethod(name).invoke(wifi)
    if (config == null) Verdict.NO to "$name returned null" else Verdict.OK to "$name is readable"
}

private fun interfaces() = probe("net.interfaces") {
    val names = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }.map { it.name }
    val ap = names.filter { AP_IFACE.matches(it) }
    Verdict.INFO to "up=$names ap=$ap"
}

private fun arpTable() = probe("clients.procNetArp") {
    val entries = File("/proc/net/arp").readLines().size - 1
    Verdict.OK to "readable; entries=$entries"
}

private fun ipNeigh() = probe("clients.ipNeigh") {
    val process = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
    if (!process.waitFor(3, TimeUnit.SECONDS)) {
        process.destroy()
        Verdict.NO to "timed out"
    } else {
        val lines = process.inputStream.bufferedReader().readLines()
        if (process.exitValue() == 0) {
            // Counts only: these lines hold the IP and MAC of whoever is connected.
            val onAp = lines.count { line -> line.split(" ").getOrNull(2)?.let(AP_IFACE::matches) == true }
            Verdict.OK to "readable; entries=${lines.size} onHotspot=$onAp"
        } else {
            Verdict.NO to "exit=${process.exitValue()} ${lines.firstOrNull()?.take(100)}"
        }
    }
}

/** Total hotspot usage could come from here, but not per device. */
private fun ifaceStats() = probe("usage.ifaceStats") {
    val up = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.map { it.name }
    // Really read the file: canRead() said yes on Android 10 where the read itself may still be refused.
    fun counter(iface: String, name: String) =
        runCatching { File("/sys/class/net/$iface/statistics/$name").readText().trim().toLong() }.getOrNull()
    val readable = up.filter { counter(it, "rx_bytes") != null }
    val ap = up.filter { AP_IFACE.matches(it) }.joinToString { "$it rx=${counter(it, "rx_bytes")} tx=${counter(it, "tx_bytes")}" }
    val verdict = if (readable.isNotEmpty()) Verdict.OK else Verdict.NO
    verdict to "actually read=$readable of up=$up; ap: ${ap.ifEmpty { "none" }}"
}

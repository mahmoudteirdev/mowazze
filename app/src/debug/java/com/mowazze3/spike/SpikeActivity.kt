package com.mowazze3.spike

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mowazze3.AppLocale
import com.mowazze3.ui.theme.MowazzeTheme
import com.mowazze3.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only feasibility screen. Read-only probes run by themselves. Anything that changes the phone's
 * state (hotspot, special access, service) runs only when a person presses its button.
 */
class SpikeActivity : ComponentActivity() {
    private val results = mutableStateListOf<ProbeResult>()
    private val busy = mutableStateOf(false)
    private var hotspot: WifiManager.LocalOnlyHotspotReservation? = null

    private val requestHotspotPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLocalHotspot() else put(ProbeResult("lohs.start", Verdict.NO, "permission refused").log())
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startService() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MowazzeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Screen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runPassive()
    }

    override fun onDestroy() {
        hotspot?.close()
        hotspot = null
        super.onDestroy()
    }

    private fun put(result: ProbeResult) {
        val index = results.indexOfFirst { it.id == result.id }
        if (index >= 0) results[index] = result else results.add(result)
    }

    private fun runPassive() {
        busy.value = true
        lifecycleScope.launch {
            val fresh = withContext(Dispatchers.IO) { passiveProbes(applicationContext) }
            fresh.forEach(::put)
            busy.value = false
        }
    }

    private fun hotspotPermission() =
        if (Build.VERSION.SDK_INT >= 33) "android.permission.NEARBY_WIFI_DEVICES" else Manifest.permission.ACCESS_FINE_LOCATION

    private fun onHotspotPressed() {
        when {
            hotspot != null -> {
                hotspot?.close()
                hotspot = null
                put(ProbeResult("lohs.start", Verdict.INFO, "closed by user").log())
                runPassive()
            }
            ContextCompat.checkSelfPermission(this, hotspotPermission()) == PackageManager.PERMISSION_GRANTED -> startLocalHotspot()
            else -> requestHotspotPermission.launch(hotspotPermission())
        }
    }

    // Callers check the permission first; a refusal is caught below and reported as a result.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun startLocalHotspot() {
        val wifi = getSystemService(WifiManager::class.java)
        try {
            wifi.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        hotspot = reservation
                        val hasCredentials = if (Build.VERSION.SDK_INT >= 30) {
                            val config = reservation.softApConfiguration
                            config.ssid != null && config.passphrase != null
                        } else {
                            val config = reservation.wifiConfiguration
                            config?.SSID != null && config.preSharedKey != null
                        }
                        put(ProbeResult("lohs.start", Verdict.OK, "started; ssid and password available=$hasCredentials").log())
                        runPassive()
                    }

                    override fun onStopped() {
                        hotspot = null
                        put(ProbeResult("lohs.stopped", Verdict.INFO, "stopped by the system").log())
                    }

                    override fun onFailed(reason: Int) {
                        put(ProbeResult("lohs.start", Verdict.NO, "failed reason=$reason (0 no channel, 1 generic, 2 incompatible mode, 3 tethering on)").log())
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (t: Throwable) {
            put(ProbeResult("lohs.start", Verdict.NO, t.describe()).log())
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, SpikeService::class.java))
            put(ProbeResult("fgs.request", Verdict.INFO, "requested; result is fgs.start in logcat, notification should show").log())
        } catch (t: Throwable) {
            put(ProbeResult("fgs.request", Verdict.NO, t.describe()).log())
        }
    }

    private fun onServicePressed() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch("android.permission.POST_NOTIFICATIONS")
        } else {
            startService()
        }
    }

    private fun open(id: String, intent: Intent) {
        try {
            startActivity(intent)
            put(ProbeResult(id, Verdict.OK, "opened").log())
        } catch (t: ActivityNotFoundException) {
            put(ProbeResult(id, Verdict.NO, t.describe()).log())
        } catch (t: SecurityException) {
            put(ProbeResult(id, Verdict.NO, t.describe()).log())
        }
    }

    /** The fallback if "no root" can't start the hotspot: send the person to the system screen. */
    private fun openTetherSettings() {
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$WifiTetherSettingsActivity")),
        )
        val target = candidates.firstOrNull { it.resolveActivityInfo(packageManager, 0)?.exported == true }
        if (target == null) {
            put(ProbeResult("settings.tether", Verdict.NO, "no exported tethering screen; using wireless settings").log())
            open("settings.wireless", Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } else {
            open("settings.tether", target)
        }
    }

    @Composable
    private fun Screen() {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text("فحص الجدوى", style = MaterialTheme.typography.titleLarge)
            Text(
                if (busy.value) "نفحص…" else "الفحوصات بالقراءة فقط بتشتغل لحالها. الأزرار بتغيّر حالة التلفون.",
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                item { Action("أعد الفحص") { runPassive() } }
                item { Action(if (hotspot != null) "أوقف الهوتسبوت المحلي" else "شغّل الهوتسبوت المحلي", primary = true) { onHotspotPressed() } }
                item { Action("منح إذن تعديل الإعدادات") { open("settings.writeAccess", Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) } }
                item { Action("شغّل مشاركة النت", primary = true) { startTethering(this@SpikeActivity, "tether.start", ::put) } }
                item {
                    Action("شغّل مشاركة النت مع تجاوز القيود") {
                        put(exemptHiddenApis())
                        startTethering(this@SpikeActivity, "tether.startExempt", ::put)
                    }
                }
                item { Action("أوقف مشاركة النت") { stopTethering(this@SpikeActivity, ::put); runPassive() } }
                item { Action("Shizuku: اطلب الإذن") { ShizukuProbes.requestPermission(::put) } }
                item {
                    Action("Shizuku: اقرأ إعدادات الهوتسبوت") {
                        put(exemptHiddenApis())
                        ShizukuProbes.readApConfig(::put)
                    }
                }
                item {
                    Action("Shizuku: شغّل مشاركة النت", primary = true) {
                        put(exemptHiddenApis())
                        ShizukuProbes.startTethering(::put)
                    }
                }
                item {
                    Action("Shizuku: أوقف مشاركة النت") {
                        put(exemptHiddenApis())
                        ShizukuProbes.stopTethering(::put)
                        runPassive()
                    }
                }
                item { Action("افتح إعدادات المشاركة") { openTetherSettings() } }
                item { Action("شغّل الخدمة الأمامية") { onServicePressed() } }
                item { Action("أوقف الخدمة الأمامية") { stopService(Intent(this@SpikeActivity, SpikeService::class.java)) } }
                items(results, key = { it.id }) { Row(it) }
            }
        }
    }

    @Composable
    private fun Action(label: String, primary: Boolean = false, onClick: () -> Unit) {
        if (primary) {
            Button(onClick, Modifier.fillMaxWidth()) { Text(label) }
        } else {
            OutlinedButton(onClick, Modifier.fillMaxWidth()) { Text(label) }
        }
    }

    @Composable
    private fun Row(result: ProbeResult) {
        val color = when (result.verdict) {
            Verdict.OK -> MowazzeTheme.extra.success
            Verdict.NO -> MaterialTheme.colorScheme.error
            Verdict.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column {
            Text("${result.verdict} · ${result.id}", style = MaterialTheme.typography.titleSmall, color = color)
            Text(result.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

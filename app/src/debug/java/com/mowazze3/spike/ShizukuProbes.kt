package com.mowazze3.spike

import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.lang.reflect.Proxy
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** The Shizuku level: same system calls as the app, but made with the shell uid. */
object ShizukuProbes {
    private const val REQUEST_CODE = 42
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val TETHERING_WIFI = 0
    private const val CONNECTIVITY_SCOPE_GLOBAL = 1
    private const val LISTENER_DESCRIPTOR = "android.net.IIntResultListener"

    fun status(): ProbeResult = try {
        when {
            !Shizuku.pingBinder() -> ProbeResult("shizuku.status", Verdict.NO, "server not running")
            Shizuku.isPreV11() -> ProbeResult("shizuku.status", Verdict.NO, "server too old")
            else -> {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                val verdict = if (granted) Verdict.OK else Verdict.INFO
                ProbeResult("shizuku.status", verdict, "running uid=${Shizuku.getUid()} version=${Shizuku.getVersion()} granted=$granted")
            }
        }
    } catch (t: Throwable) {
        ProbeResult("shizuku.status", Verdict.NO, t.describe())
    }.log()

    fun requestPermission(report: (ProbeResult) -> Unit) {
        try {
            Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    report(ProbeResult("shizuku.request", if (granted) Verdict.OK else Verdict.NO, "granted=$granted").log())
                }
            })
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            report(ProbeResult("shizuku.request", Verdict.NO, t.describe()).log())
        }
    }

    private fun service(name: String): IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name))

    /** The QR needs the saved SSID and password. Reports whether they came back, never their values. */
    fun readApConfig(report: (ProbeResult) -> Unit) {
        val id = "shizuku.readConfig"
        try {
            val wifi = aidlInterface("android.net.wifi.IWifiManager", service("wifi"))
            val name = if (Build.VERSION.SDK_INT >= 30) "getSoftApConfiguration" else "getWifiApConfiguration"
            val method = wifi.javaClass.methods.firstOrNull { it.name == name } ?: error("$name is not visible to this app")
            val config = method.invoke(wifi, *method.parameterTypes.map { SHELL_PACKAGE as Any? }.toTypedArray())
            if (config == null) {
                report(ProbeResult(id, Verdict.NO, "$name returned null").log())
                return
            }
            val hasSsid = runCatching { config.javaClass.getMethod("getSsid").invoke(config) != null }.getOrDefault(false)
            val hasPassword = runCatching { config.javaClass.getMethod("getPassphrase").invoke(config) != null }.getOrDefault(false)
            report(ProbeResult(id, Verdict.OK, "$name returned a config; ssid present=$hasSsid password present=$hasPassword").log())
        } catch (t: Throwable) {
            report(ProbeResult(id, Verdict.NO, t.describe()).log())
        }
    }

    fun startTethering(report: (ProbeResult) -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            callConnector("shizuku.tetherStart", "startTethering", report)
        } else {
            startViaConnectivity("shizuku.tetherStart", service("connectivity"), SHELL_PACKAGE, report)
        }
    }

    fun stopTethering(report: (ProbeResult) -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            callConnector("shizuku.tetherStop", "stopTethering", report)
        } else {
            stopViaConnectivity("shizuku.tetherStop", service("connectivity"), SHELL_PACKAGE, report)
        }
    }

    /** Android 11+ moved tethering to ITetheringConnector. Signatures vary by version, so arguments go by type. */
    private fun callConnector(id: String, methodName: String, report: (ProbeResult) -> Unit) {
        try {
            val connector = aidlInterface("android.net.ITetheringConnector", service("tethering"))
            val method = connector.javaClass.methods.firstOrNull { it.name == methodName } ?: error("$methodName is not visible to this app")
            val listener = resultListener { code ->
                val verdict = if (code == 0) Verdict.OK else Verdict.NO
                report(ProbeResult(id, verdict, "$methodName result=$code (0 is success)").log())
            }
            var firstString = true
            val args = method.parameterTypes.map { type ->
                when {
                    type == Int::class.javaPrimitiveType -> TETHERING_WIFI
                    type == String::class.java -> if (firstString) SHELL_PACKAGE.also { firstString = false } else null
                    type.name == "android.net.TetheringRequestParcel" -> requestParcel(type)
                    type.name == LISTENER_DESCRIPTOR -> listener
                    else -> null
                }
            }
            method.invoke(connector, *args.toTypedArray())
            report(ProbeResult(id, Verdict.INFO, "request sent, waiting for result").log())
        } catch (t: Throwable) {
            report(ProbeResult(id, Verdict.NO, t.describe()).log())
        }
    }

    private fun requestParcel(type: Class<*>): Any {
        val parcel = type.getConstructor().newInstance()
        type.getField("tetheringType").setInt(parcel, TETHERING_WIFI)
        runCatching { type.getField("connectivityScope").setInt(parcel, CONNECTIVITY_SCOPE_GLOBAL) }
        return parcel
    }

    /** The system calls back through this hidden interface, so it's implemented by hand on a plain Binder. */
    private fun resultListener(onResult: (Int) -> Unit): Any {
        val main = Handler(Looper.getMainLooper())
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == FIRST_CALL_TRANSACTION) {
                    data.enforceInterface(LISTENER_DESCRIPTOR)
                    val result = data.readInt()
                    main.post { onResult(result) }
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        val type = Class.forName(LISTENER_DESCRIPTOR)
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "onResult" -> {
                    val result = args[0] as Int
                    main.post { onResult(result) }
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "SpikeResultListener"
                else -> null
            }
        }
    }
}

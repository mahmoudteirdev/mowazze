package com.mowazze3.spike

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import org.lsposed.hiddenapibypass.HiddenApiBypass

private const val TETHERING_WIFI = 0

/**
 * Lifts the hidden-API filter for this process through double reflection. This works up to Android 10
 * only. It tells apart "the filter hides it" from "the system refuses it"; shipping it is a separate decision.
 */
fun exemptHiddenApis(): ProbeResult = try {
    if (Build.VERSION.SDK_INT >= 30) {
        // Double reflection stopped working on Android 11, so newer versions need the library.
        check(HiddenApiBypass.addHiddenApiExemptions("")) { "HiddenApiBypass refused" }
        return ProbeResult("hiddenapi.exempt", Verdict.OK, "filter lifted through HiddenApiBypass").log()
    }
    val classType = Class::class.java
    val forName = classType.getDeclaredMethod("forName", String::class.java)
    val getDeclared = classType.getDeclaredMethod("getDeclaredMethod", String::class.java, arrayOf<Class<*>>().javaClass)
    val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
    val getRuntime = getDeclared.invoke(vmRuntime, "getRuntime", null) as Method
    val setExemptions = getDeclared.invoke(vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(arrayOf<String>().javaClass)) as Method
    setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
    ProbeResult("hiddenapi.exempt", Verdict.OK, "filter lifted for this process")
} catch (t: Throwable) {
    ProbeResult("hiddenapi.exempt", Verdict.NO, t.describe())
}.log()

fun aidlInterface(stubClass: String, binder: IBinder): Any =
    Class.forName("$stubClass\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!

private fun systemBinder(name: String): IBinder =
    Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder

private fun Any.method(name: String): Method =
    javaClass.methods.firstOrNull { it.name == name }
        ?: error("$name is not visible to this app (hidden-API filter, or removed on this version)")

/** Signatures differ between SDK 26 and 29, so arguments are filled in by type. */
private fun argFor(type: Class<*>, callerPackage: String, receiver: ResultReceiver?): Any? = when (type) {
    Int::class.javaPrimitiveType -> TETHERING_WIFI
    Boolean::class.javaPrimitiveType -> false
    String::class.java -> callerPackage
    ResultReceiver::class.java -> receiver
    else -> null
}

/** Starts Wi-Fi tethering through IConnectivityManager on [binder], as whichever uid that binder calls with. */
fun startViaConnectivity(id: String, binder: IBinder, callerPackage: String, report: (ProbeResult) -> Unit) {
    try {
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val verdict = if (resultCode == 0) Verdict.OK else Verdict.NO
                report(ProbeResult(id, verdict, "result=$resultCode (0 is success)").log())
            }
        }
        val service = aidlInterface("android.net.IConnectivityManager", binder)
        val method = service.method("startTethering")
        method.invoke(service, *method.parameterTypes.map { argFor(it, callerPackage, receiver) }.toTypedArray())
        report(ProbeResult(id, Verdict.INFO, "request sent, waiting for result").log())
    } catch (t: Throwable) {
        report(ProbeResult(id, Verdict.NO, t.describe()).log())
    }
}

fun stopViaConnectivity(id: String, binder: IBinder, callerPackage: String, report: (ProbeResult) -> Unit) {
    try {
        val service = aidlInterface("android.net.IConnectivityManager", binder)
        val method = service.method("stopTethering")
        method.invoke(service, *method.parameterTypes.map { argFor(it, callerPackage, null) }.toTypedArray())
        report(ProbeResult(id, Verdict.OK, "call returned without error").log())
    } catch (t: Throwable) {
        report(ProbeResult(id, Verdict.NO, t.describe()).log())
    }
}

/**
 * Can an ordinary app start real internet-sharing tethering? Both routes are non-public. A failure
 * here is a valid result: it means "no root" can't start the hotspot itself on this Android version.
 */
fun startTethering(context: Context, id: String, report: (ProbeResult) -> Unit) {
    if (Build.VERSION.SDK_INT >= 30) {
        startViaTetheringManager(context, id, report)
    } else {
        try {
            startViaConnectivity(id, systemBinder("connectivity"), context.packageName, report)
        } catch (t: Throwable) {
            report(ProbeResult(id, Verdict.NO, t.describe()).log())
        }
    }
}

fun stopTethering(context: Context, report: (ProbeResult) -> Unit) {
    val id = "tether.stop"
    try {
        if (Build.VERSION.SDK_INT >= 30) {
            val manager = context.getSystemService("tethering")
            manager.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(manager, TETHERING_WIFI)
            report(ProbeResult(id, Verdict.OK, "call returned without error").log())
        } else {
            stopViaConnectivity(id, systemBinder("connectivity"), context.packageName, report)
        }
    } catch (t: Throwable) {
        report(ProbeResult(id, Verdict.NO, t.describe()).log())
    }
}

private fun startViaTetheringManager(context: Context, id: String, report: (ProbeResult) -> Unit) {
    try {
        val manager = context.getSystemService("tethering") ?: error("no tethering service")
        val builder = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
            .getConstructor(Int::class.javaPrimitiveType)
            .newInstance(TETHERING_WIFI)
        val request = builder.javaClass.getMethod("build").invoke(builder)
        val callbackType = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val callback = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
            when (method.name) {
                "onTetheringStarted" -> report(ProbeResult(id, Verdict.OK, "TetheringManager started").log())
                "onTetheringFailed" -> report(ProbeResult(id, Verdict.NO, "TetheringManager failed code=${args?.firstOrNull()}").log())
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "equals" -> return@newProxyInstance proxy === args?.firstOrNull()
                "toString" -> return@newProxyInstance "SpikeTetherCallback"
            }
            null
        }
        val executor = Executor { Handler(Looper.getMainLooper()).post(it) }
        manager.javaClass.getMethod("startTethering", request.javaClass, Executor::class.java, callbackType)
            .invoke(manager, request, executor, callback)
        report(ProbeResult(id, Verdict.INFO, "request sent, waiting for result").log())
    } catch (t: Throwable) {
        report(ProbeResult(id, Verdict.NO, t.describe()).log())
    }
}

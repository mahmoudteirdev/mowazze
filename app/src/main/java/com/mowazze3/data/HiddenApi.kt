package com.mowazze3.data

import android.os.Build
import java.lang.reflect.Method
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Lifts Android's filter on non-public system calls for this process. Double reflection works up to
 * Android 10; newer versions need the HiddenApiBypass library. Before a Play release, check Google Play's
 * policy on this (see ROADMAP.md).
 */
object HiddenApi {
    private var allowed = false

    fun allowAll() {
        if (allowed) return
        if (Build.VERSION.SDK_INT >= 30) {
            check(HiddenApiBypass.addHiddenApiExemptions("")) { "HiddenApiBypass refused" }
        } else {
            val classType = Class::class.java
            val forName = classType.getDeclaredMethod("forName", String::class.java)
            val getDeclared = classType.getDeclaredMethod("getDeclaredMethod", String::class.java, arrayOf<Class<*>>().javaClass)
            val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclared.invoke(vmRuntime, "getRuntime", null) as Method
            val setExemptions = getDeclared.invoke(vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(arrayOf<String>().javaClass)) as Method
            setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
        }
        allowed = true
    }
}

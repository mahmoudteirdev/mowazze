package com.mowazze3.data

import android.content.Context
import android.content.pm.PackageManager
import com.mowazze3.domain.DeviceProbe
import com.mowazze3.domain.ShizukuStatus
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

class AndroidDeviceProbe(private val context: Context) : DeviceProbe {
    override fun rootBinaryFound() = SU_PATHS.any { File(it).exists() }

    override fun isNoRootSupported() = true

    override suspend fun requestRootPermission(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    override fun shizukuStatus(): ShizukuStatus {
        if (!shizukuInstalled()) return ShizukuStatus.NOT_INSTALLED
        return try {
            when {
                !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShizukuStatus.NOT_RUNNING
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
                else -> ShizukuStatus.NEEDS_PERMISSION
            }
        } catch (_: RuntimeException) {
            // The server went away between the ping and the check.
            ShizukuStatus.NOT_RUNNING
        }
    }

    private fun shizukuInstalled() = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun requestShizukuPermission(): Boolean = try {
        // Refused earlier with "don't ask again": Shizuku shows nothing and the listener never fires.
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            false
        } else {
            withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { awaitPermissionResult() } ?: false
        }
    } catch (_: RuntimeException) {
        false
    }

    private suspend fun awaitPermissionResult(): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                if (continuation.isActive) continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    private companion object {
        const val REQUEST_CODE = 1
        const val REQUEST_TIMEOUT_MILLIS = 60_000L
        val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk")
    }
}

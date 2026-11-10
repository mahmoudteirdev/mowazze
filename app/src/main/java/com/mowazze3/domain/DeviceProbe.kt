package com.mowazze3.domain

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

/** What first-run setup needs to know about the device. */
interface DeviceProbe {
    /** Looks for the binary only, because running su could open a grant dialog. */
    fun rootBinaryFound(): Boolean

    fun shizukuStatus(): ShizukuStatus

    /** Returns true if the person allowed it. */
    suspend fun requestShizukuPermission(): Boolean

    /** Whether ordinary app hotspot tethering is supported on this Android OS version. False on Android 11+. */
    fun isNoRootSupported(): Boolean = true

    /** Requests root superuser access via su. */
    suspend fun requestRootPermission(): Boolean = false
}

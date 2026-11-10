package com.mowazze3.spike

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mowazze3.R

/** Checks that a foreground service of type connectedDevice may start on this Android version. */
class SpikeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "فحص الجدوى", NotificationManager.IMPORTANCE_LOW))
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("فحص الخدمة الأمامية")
                .setOngoing(true)
                .build()
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            ServiceCompat.startForeground(this, 1, notification, type)
            ProbeResult("fgs.start", Verdict.OK, "started with type connectedDevice").log()
        } catch (t: Throwable) {
            ProbeResult("fgs.start", Verdict.NO, t.describe()).log()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val CHANNEL = "spike"
    }
}

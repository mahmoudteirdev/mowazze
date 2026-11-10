package com.mowazze3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mowazze3.AppLocale
import com.mowazze3.MainActivity
import com.mowazze3.MowazzeApp
import com.mowazze3.R
import com.mowazze3.data.ShareEvent
import com.mowazze3.domain.ShareState
import com.mowazze3.domain.connectedCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while sharing and shows the permanent notification with the device count and
 * a stop button. It holds no state of its own: it mirrors [com.mowazze3.data.ShareRepository].
 */
class ShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    private val repository get() = (application as MowazzeApp).shareRepository

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            repository.stop()
            return START_NOT_STICKY
        }
        // Must happen even if sharing has already ended, or Android kills the app for not calling it.
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(repository.state.value), type)
        if (watcher == null) {
            watcher = scope.launch { watchState() }
            scope.launch { watchJoins() }
        }
        return START_NOT_STICKY
    }

    /** With the app open a Snackbar says this already, so the system notification is for when it's in the background. */
    private suspend fun watchJoins() {
        val app = application as MowazzeApp
        val manager = getSystemService(NotificationManager::class.java)
        repository.events.collect { event ->
            if (event is ShareEvent.ClientJoined && !app.isInForeground) {
                manager.createNotificationChannel(
                    NotificationChannel(DEVICES_CHANNEL_ID, getString(R.string.fg_channel_devices), NotificationManager.IMPORTANCE_DEFAULT),
                )
                val notification = NotificationCompat.Builder(this, DEVICES_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_person_add)
                    .setContentTitle(getString(R.string.toast_joined, event.client.name))
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                    )
                    .build()
                manager.notify(event.client.id.hashCode(), notification)
            } else if (event is ShareEvent.QuotaAlert80) {
                manager.createNotificationChannel(
                    NotificationChannel(QUOTA_CHANNEL_ID, getString(R.string.notif_quota), NotificationManager.IMPORTANCE_HIGH),
                )
                val notification = NotificationCompat.Builder(this, QUOTA_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_warning)
                    .setContentTitle(getString(R.string.notif_quota))
                    .setContentText(getString(R.string.alert_80))
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                    )
                    .build()
                manager.notify(QUOTA_NOTIFICATION_ID, notification)
            }
        }
    }

    private suspend fun watchState() {
        val manager = getSystemService(NotificationManager::class.java)
        repository.state.collect { state ->
            if (state == ShareState.Starting || state is ShareState.On) {
                manager.notify(NOTIFICATION_ID, buildNotification(state))
            } else {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        repository.onServiceDestroyed()
        super.onDestroy()
    }

    private fun buildNotification(state: ShareState): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.fg_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ShareService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
        return if (state is ShareState.On) {
            builder
                .setContentTitle(getString(R.string.fg_title))
                .setContentText(getString(R.string.fg_text, state.connectedCount))
                .build()
        } else {
            builder.setContentTitle(getString(R.string.share_starting)).build()
        }
    }

    private companion object {
        const val CHANNEL_ID = "sharing"
        const val DEVICES_CHANNEL_ID = "new_device"
        const val QUOTA_CHANNEL_ID = "quota"
        const val NOTIFICATION_ID = 1
        const val QUOTA_NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.mowazze3.action.STOP_SHARING"
    }
}

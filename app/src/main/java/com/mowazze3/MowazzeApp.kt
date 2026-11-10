package com.mowazze3

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.mowazze3.data.AdaptiveShareEngine
import com.mowazze3.data.Android10ShareEngine
import com.mowazze3.data.DataStoreSettingsStore
import com.mowazze3.data.FakeShareEngine
import com.mowazze3.data.ShareRepository
import com.mowazze3.data.ShizukuShareEngine
import com.mowazze3.domain.ShareEngine
import com.mowazze3.domain.ShareState
import com.mowazze3.service.ShareService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MowazzeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsStore by lazy { DataStoreSettingsStore(this) }

    /**
     * Debug builds can run on the fake engine to reach every state, by creating this file:
     *   adb shell run-as com.mowazze3 touch files/use_fake_engine
     */
    val shareEngine: ShareEngine by lazy {
        when {
            BuildConfig.DEBUG && File(filesDir, "use_fake_engine").exists() -> FakeShareEngine()
            else -> AdaptiveShareEngine(this, settingsStore)
        }
    }

    val shareRepository by lazy { ShareRepository(shareEngine, settingsStore, appScope) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    private var startedActivities = 0

    /** True while a screen of the app is visible. */
    val isInForeground: Boolean get() = startedActivities > 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        shareRepository.restore()
        appScope.launch {
            shareRepository.state
                .map { it == ShareState.Starting || it is ShareState.On }
                .distinctUntilChanged()
                .filter { sharing -> sharing }
                .collect { startService() }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, ShareService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses this from the background, which only a restore() can hit.
            Log.w(TAG, "Could not start the foreground service", e)
        }
    }

    private companion object {
        const val TAG = "MowazzeApp"
    }
}

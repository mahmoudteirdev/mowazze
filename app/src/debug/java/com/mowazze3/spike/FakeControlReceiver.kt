package com.mowazze3.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mowazze3.MowazzeApp
import com.mowazze3.data.FakeShareEngine
import com.mowazze3.domain.FailReason

/**
 * Debug only: forces states on the fake engine from adb, like the switches on the prototype's side panel.
 *   adb shell am broadcast -n com.mowazze3/.spike.FakeControlReceiver --ez internet false
 *   adb shell am broadcast -n com.mowazze3/.spike.FakeControlReceiver --es fail WIFI_CONNECTED
 */
class FakeControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = (context.applicationContext as MowazzeApp).shareEngine as? FakeShareEngine ?: return
        if (intent.hasExtra("internet")) engine.hasInternet = intent.getBooleanExtra("internet", true)
        intent.getStringExtra("fail")?.let { name ->
            engine.nextFailure = FailReason.entries.firstOrNull { it.name == name }
        }
    }
}

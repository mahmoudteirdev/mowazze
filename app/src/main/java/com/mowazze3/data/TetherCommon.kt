package com.mowazze3.data

import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.mowazze3.R
import com.mowazze3.domain.Client
import com.mowazze3.domain.parseNeighbours

private val WIFI_TETHER_IFACE = Regex("^(swlan|softap|ap|wlan1).*")
private const val MAC_SUFFIX_LENGTH = 5

/** The Wi-Fi hotspot's interface name, or null if the hotspot is off. Readable at every level tested. */
internal fun wifiTetherIface(context: Context): String? {
    val intent = ContextCompat.registerReceiver(
        context, null, IntentFilter("android.net.conn.TETHER_STATE_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED,
    ) ?: return null
    return intent.getStringArrayListExtra("tetherArray").orEmpty().firstOrNull { WIFI_TETHER_IFACE.matches(it) }
}

/** Android doesn't expose hostnames to apps, so the end of the address tells devices apart. */
internal fun clientsFromNeighbours(context: Context, lines: List<String>, iface: String): List<Client> =
    parseNeighbours(lines, iface).map { neighbour ->
        val suffix = neighbour.mac.takeLast(MAC_SUFFIX_LENGTH).uppercase()
        Client(neighbour.mac, context.getString(R.string.device_unnamed, suffix), neighbour.ip, usageBytesToday = 0)
    }

internal fun hasValidatedNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

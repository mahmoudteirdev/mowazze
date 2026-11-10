package com.mowazze3.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TetheringTest {
    @Test
    fun `reachable and stale devices on the hotspot are listed`() {
        val lines = listOf(
            "192.168.43.10 dev swlan0 lladdr aa:bb:cc:dd:ee:01 REACHABLE",
            "192.168.43.11 dev swlan0 lladdr aa:bb:cc:dd:ee:02 STALE",
        )
        assertEquals(
            listOf(Neighbour("192.168.43.10", "aa:bb:cc:dd:ee:01"), Neighbour("192.168.43.11", "aa:bb:cc:dd:ee:02")),
            parseNeighbours(lines, "swlan0"),
        )
    }

    @Test
    fun `other interfaces are ignored, like the phone's own router`() {
        val lines = listOf("192.168.1.1 dev wlan0 lladdr aa:bb:cc:dd:ee:03 REACHABLE")
        assertEquals(emptyList<Neighbour>(), parseNeighbours(lines, "swlan0"))
    }

    @Test
    fun `failed entries with no address are ignored`() {
        val lines = listOf(
            "192.168.43.12 dev swlan0  FAILED",
            "192.168.43.13 dev swlan0  INCOMPLETE",
            "192.168.43.14 dev swlan0 lladdr aa:bb:cc:dd:ee:04 FAILED",
        )
        assertEquals(emptyList<Neighbour>(), parseNeighbours(lines, "swlan0"))
    }

    @Test
    fun `IPv6 entries don't list a device twice`() {
        val lines = listOf(
            "fe80::1 dev swlan0 lladdr aa:bb:cc:dd:ee:01 STALE",
            "192.168.43.10 dev swlan0 lladdr aa:bb:cc:dd:ee:01 REACHABLE",
        )
        assertEquals(listOf(Neighbour("192.168.43.10", "aa:bb:cc:dd:ee:01")), parseNeighbours(lines, "swlan0"))
    }

    @Test
    fun `junk output doesn't crash`() {
        assertEquals(emptyList<Neighbour>(), parseNeighbours(listOf("", "   ", "Permission denied", "dev"), "swlan0"))
    }

    @Test
    fun `an upstream is recognised from the tethering dump`() {
        assertEquals(true, tetherHasUpstream(listOf("x", "  Current upstream interface(s): [wlan0]")))
        assertEquals(false, tetherHasUpstream(listOf("  Current upstream interface(s): null")))
        assertEquals(false, tetherHasUpstream(listOf("  Current upstream interface(s): []")))
    }

    @Test
    fun `a dump without the line says nothing either way`() {
        assertNull(tetherHasUpstream(listOf("Tether state:", "  Upstream wanted: true")))
        assertNull(tetherHasUpstream(emptyList()))
    }

    private val noNet = ShareState.On("hotspot", emptyList(), hasInternet = false, downMbps = null)
    private val online = noNet.copy(hasInternet = true)

    @Test
    fun `the Wi-Fi fix shows only for the system hotspot with no internet while on Wi-Fi`() {
        assertEquals(true, offersWifiNotSharedFix(PermissionLevel.SHIZUKU, noNet, phoneOnWifi = true))
    }

    @Test
    fun `the Wi-Fi fix stays away in every other case`() {
        assertEquals("mobile data is the cause then", false, offersWifiNotSharedFix(PermissionLevel.SHIZUKU, noNet, phoneOnWifi = false))
        assertEquals(false, offersWifiNotSharedFix(PermissionLevel.SHIZUKU, online, phoneOnWifi = true))
        assertEquals("Wi-Fi Direct already works on Wi-Fi", false, offersWifiNotSharedFix(PermissionLevel.NO_ROOT, noNet, phoneOnWifi = true))
        assertEquals(false, offersWifiNotSharedFix(PermissionLevel.SHIZUKU, ShareState.Off, phoneOnWifi = true))
    }

    @Test
    fun `failure codes map to their reasons`() {
        assertEquals(FailReason.UNSUPPORTED, tetherFailureReason(3, wifiConnected = true))
        assertEquals(FailReason.CARRIER_BLOCKED, tetherFailureReason(11, wifiConnected = true))
        assertEquals(FailReason.CARRIER_BLOCKED, tetherFailureReason(13, wifiConnected = false))
    }

    @Test
    fun `other failures point at Wi-Fi only when it is connected`() {
        assertEquals(FailReason.WIFI_CONNECTED, tetherFailureReason(5, wifiConnected = true))
        assertEquals(FailReason.UNKNOWN, tetherFailureReason(5, wifiConnected = false))
    }

    @Test
    fun `speed comes from the change in the counter`() {
        assertEquals(8f, megabitsPerSecond(0, 2_000_000, 2_000)!!, 0.001f)
    }

    @Test
    fun `speed is empty without a usable earlier reading`() {
        assertNull(megabitsPerSecond(null, 100, 2_000))
        assertNull(megabitsPerSecond(100, 200, 0))
        assertNull("counter restarted", megabitsPerSecond(500, 100, 2_000))
    }
}

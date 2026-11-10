package com.mowazze3.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `bytes pick a sensible unit and round`() {
        assertEquals("0 MB", formatBytes(0))
        assertEquals("1 MB", formatBytes(1))
        assertEquals("1 MB", formatBytes(999_999))
        assertEquals("1 MB", formatBytes(1_000_000))
        assertEquals("2 MB", formatBytes(1_500_000))
        assertEquals("999 MB", formatBytes(999_000_000))
        assertEquals("1.0 GB", formatBytes(1_000_000_000))
        assertEquals("1.4 GB", formatBytes(1_400_000_000))
    }

    @Test
    fun `speed keeps one decimal in Latin digits, whatever the device language`() {
        assertEquals("12.4 Mb/s", formatSpeed(12.4f))
        assertEquals("0.0 Mb/s", formatSpeed(0f))
    }

    private val client = Client("a", "لابتوب", "192.168.43.2", 0)

    @Test
    fun `stats are empty unless sharing is on`() {
        val empty = HomeStats(NO_VALUE, NO_VALUE, NO_VALUE)
        assertEquals(empty, HomeStats.of(ShareState.Off, 5_000_000))
        assertEquals(empty, HomeStats.of(ShareState.Starting, 5_000_000))
        assertEquals(empty, HomeStats.of(ShareState.Failed(FailReason.TIMEOUT), 5_000_000))
    }

    @Test
    fun `stats show real values while on`() {
        val on = ShareState.On("net", listOf(client), hasInternet = true, downMbps = 12.4f)
        assertEquals(HomeStats("1", "5 MB", "12.4 Mb/s"), HomeStats.of(on, 5_000_000))
    }

    @Test
    fun `without internet only the speed goes empty`() {
        val on = ShareState.On("net", listOf(client), hasInternet = false, downMbps = null)
        assertEquals(HomeStats("1", "5 MB", NO_VALUE), HomeStats.of(on, 5_000_000))
    }

    @Test
    fun `usage shows as empty, not zero, when nothing tracks it`() {
        val on = ShareState.On("net", listOf(client), hasInternet = true, downMbps = null)
        assertEquals(HomeStats("1", NO_VALUE, NO_VALUE), HomeStats.of(on, 0, tracksUsage = false))
    }

    @Test
    fun `blocked devices are not counted`() {
        val on = ShareState.On("net", listOf(client, client.copy(id = "b", blocked = true)), true, 1f)
        assertEquals("1", HomeStats.of(on, 0).devices)
    }
}

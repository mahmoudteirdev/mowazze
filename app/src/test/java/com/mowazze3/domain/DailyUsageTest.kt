package com.mowazze3.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageTest {
    private val gaza = ZoneId.of("Asia/Gaza")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val usage = DailyUsage(LocalDate.of(2026, 11, 9), gaza, 500)

    private fun at(day: Int, hour: Int, zone: ZoneId = gaza) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone)

    @Test
    fun `usage is kept during the same day`() {
        assertEquals(500, usage.rolledTo(at(9, 23)).bytes)
    }

    @Test
    fun `usage resets after local midnight`() {
        val next = usage.rolledTo(at(10, 0))
        assertEquals(0, next.bytes)
        assertEquals(LocalDate.of(2026, 11, 10), next.day)
    }

    @Test
    fun `moving to a time zone already in the next day does not reset`() {
        // 20:00 in Gaza on the 9th is already the 10th in Tokyo.
        val moved = at(9, 20).withZoneSameInstant(tokyo)
        assertEquals(10, moved.dayOfMonth)
        assertEquals(500, usage.rolledTo(moved).bytes)
    }

    @Test
    fun `the new day adopts the zone the person is in`() {
        val next = usage.rolledTo(at(10, 9).withZoneSameInstant(tokyo))
        assertEquals(0, next.bytes)
        assertEquals(tokyo, next.zone)
    }

    @Test
    fun `a clock moved backwards does not reset`() {
        assertEquals(500, usage.rolledTo(at(8, 12)).bytes)
    }
}

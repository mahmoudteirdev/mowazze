package com.mowazze3.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Bytes shared today. The day ends at midnight in the zone it started in, so travelling across
 * time zones doesn't reset the counter (HANDOFF section 5); the next day picks up the new zone.
 */
data class DailyUsage(val day: LocalDate, val zone: ZoneId, val bytes: Long) {
    fun rolledTo(now: ZonedDateTime): DailyUsage =
        if (now.withZoneSameInstant(zone).toLocalDate().isAfter(day)) startOf(now) else this

    companion object {
        fun startOf(now: ZonedDateTime) = DailyUsage(now.toLocalDate(), now.zone, 0)
    }
}

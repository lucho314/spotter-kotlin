package com.lucho314.spotter.domain.calc

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Monday-Sunday week boundaries (bug 15: the RN app used a Sunday-starting week). */
object WeekRange {

    /** Local midnight of the Monday on or before [now], in [zone]. */
    fun currentWeekStart(now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zone).toInstant()
    }
}

package com.lucho314.spotter.feature.dashboard

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Time-of-day greeting for the dashboard header, computed in the local ([ZoneId]) hour of day. */
enum class Greeting { MORNING, AFTERNOON, NIGHT }

/** 05:00-11:59 -> MORNING, 12:00-19:59 -> AFTERNOON, otherwise NIGHT (local hour, no RN precedent - migration plan section 9 default). */
fun greetingFor(now: Instant, zone: ZoneId): Greeting {
    val hour = now.atZone(zone).hour
    return when {
        hour in 5..11 -> Greeting.MORNING
        hour in 12..19 -> Greeting.AFTERNOON
        else -> Greeting.NIGHT
    }
}

/** "ÚLTIMA SESIÓN" label, in local calendar days (never a fixed 24h window - DST-safe). */
sealed interface LastSessionLabel {
    data object Today : LastSessionLabel
    data object Yesterday : LastSessionLabel
    data class DaysAgo(val days: Int) : LastSessionLabel
    data object None : LastSessionLabel
}

/** [last] null (never trained) maps to [LastSessionLabel.None]; a same local calendar day (or later, defensively) maps to [LastSessionLabel.Today]. */
fun lastSessionLabel(last: Instant?, now: Instant, zone: ZoneId): LastSessionLabel {
    if (last == null) return LastSessionLabel.None
    val lastDate = last.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(lastDate, today)
    return when {
        daysBetween <= 0 -> LastSessionLabel.Today
        daysBetween == 1L -> LastSessionLabel.Yesterday
        else -> LastSessionLabel.DaysAgo(daysBetween.toInt())
    }
}

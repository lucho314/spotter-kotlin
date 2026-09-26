package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.WorkoutSet
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Pure workout arithmetic, kept in sync with the DB's `estimated_1rm` trigger. */
object WorkoutMath {

    /** Epley formula, matching the backend trigger exactly. */
    fun epley1Rm(weightKg: Double, reps: Int): Double = weightKg * (1 + reps / 30.0)

    fun volumeKg(sets: List<WorkoutSet>, includeWarmups: Boolean = false): Double =
        sets.asSequence()
            .filter { includeWarmups || !it.isWarmup }
            .sumOf { it.weightKg * it.reps }

    fun durationMinutes(start: Instant, end: Instant?): Long? =
        end?.let { ChronoUnit.MINUTES.between(start, it) }

    fun formatDuration(minutes: Long?): String {
        if (minutes == null) return "—"
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (hours <= 0) "$minutes min" else "${hours}h ${remainder}min"
    }

    /** The heaviest set; ties broken by the highest reps. Null if [sets] is empty. */
    fun topSet(sets: List<WorkoutSet>): WorkoutSet? =
        sets.maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))
}

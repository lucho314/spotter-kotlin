package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WorkoutSet
import java.time.Instant
import org.junit.Test

private fun set(
    id: String = "s1",
    weightKg: Double,
    reps: Int,
    isWarmup: Boolean = false,
    completedAt: Instant = Instant.EPOCH,
) = WorkoutSet(
    id = id,
    sessionId = "session-1",
    exerciseId = 1,
    exerciseName = "Bench Press",
    setNumber = 1,
    weightKg = weightKg,
    reps = reps,
    rpe = null,
    isWarmup = isWarmup,
    completedAt = completedAt,
)

class WorkoutMathTest {

    @Test
    fun `epley1Rm matches the DB trigger's formula`() {
        assertThat(WorkoutMath.epley1Rm(100.0, 10)).isWithin(1e-9).of(100.0 * (1 + 10 / 30.0))
        assertThat(WorkoutMath.epley1Rm(100.0, 0)).isWithin(1e-9).of(100.0)
    }

    @Test
    fun `volumeKg excludes warmups by default`() {
        val sets = listOf(
            set(weightKg = 100.0, reps = 5, isWarmup = true),
            set(weightKg = 80.0, reps = 10, isWarmup = false),
        )

        assertThat(WorkoutMath.volumeKg(sets)).isWithin(1e-9).of(800.0)
        assertThat(WorkoutMath.volumeKg(sets, includeWarmups = true)).isWithin(1e-9).of(1300.0)
    }

    @Test
    fun `durationMinutes is null while the session hasn't finished`() {
        val start = Instant.EPOCH
        assertThat(WorkoutMath.durationMinutes(start, null)).isNull()
        assertThat(WorkoutMath.durationMinutes(start, start.plusSeconds(65 * 60))).isEqualTo(65L)
    }

    @Test
    fun `formatDuration formats minutes, hours and the null case`() {
        assertThat(WorkoutMath.formatDuration(null)).isEqualTo("—")
        assertThat(WorkoutMath.formatDuration(45)).isEqualTo("45 min")
        assertThat(WorkoutMath.formatDuration(65)).isEqualTo("1h 5min")
    }

    @Test
    fun `topSet picks the heaviest weight, breaking ties by reps`() {
        val sets = listOf(
            set(id = "a", weightKg = 80.0, reps = 5),
            set(id = "b", weightKg = 100.0, reps = 3),
            set(id = "c", weightKg = 100.0, reps = 8),
        )

        assertThat(WorkoutMath.topSet(sets)?.id).isEqualTo("c")
        assertThat(WorkoutMath.topSet(emptyList())).isNull()
    }
}

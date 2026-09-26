package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WorkoutSet
import java.time.Instant
import org.junit.Test

private fun set(
    sessionId: String,
    weightKg: Double,
    reps: Int,
    isWarmup: Boolean = false,
    completedAt: Instant,
) = WorkoutSet(
    id = "$sessionId-${weightKg}x$reps",
    sessionId = sessionId,
    exerciseId = 1,
    exerciseName = "Squat",
    setNumber = 1,
    weightKg = weightKg,
    reps = reps,
    rpe = null,
    isWarmup = isWarmup,
    completedAt = completedAt,
)

class ExerciseProgressAggregatorTest {

    @Test
    fun `aggregates one point per session, ignoring warmups`() {
        val sessionOneStart = Instant.ofEpochSecond(1_000)
        val sets = listOf(
            set("s1", weightKg = 40.0, reps = 10, isWarmup = true, completedAt = sessionOneStart),
            set("s1", weightKg = 100.0, reps = 5, completedAt = sessionOneStart.plusSeconds(120)),
            set("s1", weightKg = 90.0, reps = 8, completedAt = sessionOneStart.plusSeconds(240)),
        )

        val points = ExerciseProgressAggregator.aggregate(sets, maxSessions = 12)

        assertThat(points).hasSize(1)
        val point = points.single()
        assertThat(point.sessionId).isEqualTo("s1")
        // The date is the earliest *working* set's completedAt: the warmup (which is earlier) is excluded.
        assertThat(point.date).isEqualTo(sessionOneStart.plusSeconds(120))
        assertThat(point.topWeightKg).isWithin(1e-9).of(100.0)
        assertThat(point.volumeKg).isWithin(1e-9).of(100.0 * 5 + 90.0 * 8)
        assertThat(point.bestE1RmKg).isWithin(1e-9).of(maxOf(WorkoutMath.epley1Rm(100.0, 5), WorkoutMath.epley1Rm(90.0, 8)))
    }

    @Test
    fun `keeps only the most recent N sessions, in ascending date order`() {
        val sets = (1..15).map { i ->
            set("s$i", weightKg = 50.0 + i, reps = 5, completedAt = Instant.ofEpochSecond(i.toLong() * 1000))
        }

        val points = ExerciseProgressAggregator.aggregate(sets, maxSessions = 12)

        assertThat(points).hasSize(12)
        assertThat(points.map { it.sessionId }).containsExactly(
            "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "s12", "s13", "s14", "s15",
        ).inOrder()
    }

    @Test
    fun `a session with only warmup sets contributes no point`() {
        val sets = listOf(set("s1", weightKg = 40.0, reps = 10, isWarmup = true, completedAt = Instant.EPOCH))

        assertThat(ExerciseProgressAggregator.aggregate(sets, maxSessions = 12)).isEmpty()
    }
}

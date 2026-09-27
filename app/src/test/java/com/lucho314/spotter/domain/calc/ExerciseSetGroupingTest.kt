package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WorkoutSet
import java.time.Instant
import org.junit.Test

class ExerciseSetGroupingTest {

    private fun set(
        id: String,
        exerciseId: Int,
        exerciseName: String?,
        setNumber: Int,
        completedAt: Instant = Instant.EPOCH,
    ) = WorkoutSet(
        id = id, sessionId = "s1", exerciseId = exerciseId, exerciseName = exerciseName, setNumber = setNumber,
        weightKg = 80.0, reps = 10, rpe = null, isWarmup = false, completedAt = completedAt,
    )

    @Test
    fun `groups by exerciseId, orders sets by setNumber`() {
        val sets = listOf(
            set("a2", exerciseId = 1, exerciseName = "Bench", setNumber = 2, completedAt = Instant.ofEpochSecond(10)),
            set("a1", exerciseId = 1, exerciseName = "Bench", setNumber = 1, completedAt = Instant.ofEpochSecond(5)),
            set("b1", exerciseId = 2, exerciseName = "Squat", setNumber = 1, completedAt = Instant.ofEpochSecond(20)),
        )

        val groups = ExerciseSetGrouping.group(sets)

        assertThat(groups).hasSize(2)
        assertThat(groups[0].exerciseId).isEqualTo(1)
        assertThat(groups[0].sets.map { it.id }).containsExactly("a1", "a2").inOrder()
    }

    @Test
    fun `groups are ordered by the earliest completedAt, tie-broken by exerciseId`() {
        val sets = listOf(
            set("a1", exerciseId = 2, exerciseName = "Squat", setNumber = 1, completedAt = Instant.ofEpochSecond(5)),
            set("b1", exerciseId = 1, exerciseName = "Bench", setNumber = 1, completedAt = Instant.ofEpochSecond(5)),
            set("c1", exerciseId = 3, exerciseName = "Row", setNumber = 1, completedAt = Instant.ofEpochSecond(1)),
        )

        val groups = ExerciseSetGrouping.group(sets)

        // exerciseId 3 has the earliest completedAt; exercises 1 and 2 tie at 5s, broken by exerciseId.
        assertThat(groups.map { it.exerciseId }).containsExactly(3, 1, 2).inOrder()
    }

    @Test
    fun `group name is the first non-null exerciseName`() {
        val sets = listOf(
            set("a1", exerciseId = 1, exerciseName = null, setNumber = 1),
            set("a2", exerciseId = 1, exerciseName = "Bench", setNumber = 2),
        )

        val groups = ExerciseSetGrouping.group(sets)

        assertThat(groups.single().exerciseName).isEqualTo("Bench")
    }
}

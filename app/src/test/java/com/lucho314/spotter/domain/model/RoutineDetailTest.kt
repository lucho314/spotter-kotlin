package com.lucho314.spotter.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private fun exercise(id: String, exerciseId: Int, sortOrder: Int, dayNumber: Int) = RoutineExercise(
    id = id,
    routineId = "r1",
    exerciseId = exerciseId,
    exercise = null,
    sortOrder = sortOrder,
    dayNumber = dayNumber,
    targetSets = 3,
    targetReps = 10,
    restSeconds = 90,
)

class RoutineDetailTest {

    private val monday = RoutineDay("d1", "r1", 1, "Lunes")

    @Test
    fun `exercisesForDay returns only that day's exercises, ordered by sortOrder`() {
        val detail = RoutineDetail(
            id = "r1",
            userId = "u1",
            name = "Push",
            description = null,
            daysPerWeek = 1,
            isArchived = false,
            days = listOf(monday),
            exercises = listOf(
                exercise("b", 2, sortOrder = 1, dayNumber = 1),
                exercise("a", 1, sortOrder = 0, dayNumber = 1),
                exercise("c", 3, sortOrder = 0, dayNumber = UNASSIGNED_DAY_NUMBER),
            ),
        )

        assertThat(detail.exercisesForDay(1).map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `unassignedExercises includes the unassigned sentinel and orphaned day numbers`() {
        val detail = RoutineDetail(
            id = "r1",
            userId = "u1",
            name = "Push",
            description = null,
            daysPerWeek = 1,
            isArchived = false,
            days = listOf(monday),
            exercises = listOf(
                exercise("a", 1, sortOrder = 0, dayNumber = 1),
                exercise("b", 2, sortOrder = 0, dayNumber = UNASSIGNED_DAY_NUMBER),
                // day 9 has no matching row in `days` (e.g. its day was deleted).
                exercise("c", 3, sortOrder = 1, dayNumber = 9),
            ),
        )

        assertThat(detail.unassignedExercises.map { it.id }).containsExactly("b", "c")
    }
}

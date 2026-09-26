package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import org.junit.Test

private fun day(id: String, dayNumber: Int, name: String) = RoutineDay(id, "routine-1", dayNumber, name)

private fun exercise(id: String, exerciseId: Int, sortOrder: Int, dayNumber: Int) = RoutineExercise(
    id = id,
    routineId = "routine-1",
    exerciseId = exerciseId,
    exercise = null,
    sortOrder = sortOrder,
    dayNumber = dayNumber,
    targetSets = 3,
    targetReps = 10,
    restSeconds = 90,
)

class RoutineOrderingTest {

    @Test
    fun `dayComparator orders by day number`() {
        val days = listOf(day("c", 3, "Miércoles"), day("a", 1, "Lunes"), day("b", 2, "Martes"))

        assertThat(days.sortedWith(RoutineOrdering.dayComparator).map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `exerciseComparator sorts unassigned exercises last`() {
        val validDayNumbers = setOf(1)
        val exercises = listOf(
            exercise("unassigned", exerciseId = 1, sortOrder = 0, dayNumber = UNASSIGNED_DAY_NUMBER),
            exercise("day1-second", exerciseId = 2, sortOrder = 1, dayNumber = 1),
            exercise("day1-first", exerciseId = 3, sortOrder = 0, dayNumber = 1),
        )

        val sorted = exercises.sortedWith(RoutineOrdering.exerciseComparator(validDayNumbers)).map { it.id }

        assertThat(sorted).containsExactly("day1-first", "day1-second", "unassigned").inOrder()
    }

    @Test
    fun `exerciseComparator treats an orphaned legacy dayNumber the same as unassigned`() {
        // Day 1 was deleted (no RoutineDay row for it anymore), but its exercise's day_number is
        // never rewritten (RoutineOrdering.nextDayNumber's KDoc) - it must sort last too, mixed in
        // by sortOrder with the explicitly unassigned ones, exactly like RoutineDetail.unassignedExercises.
        val validDayNumbers = setOf(2) // day 1 no longer exists
        val exercises = listOf(
            exercise("day2", exerciseId = 1, sortOrder = 0, dayNumber = 2),
            exercise("orphaned-day1", exerciseId = 2, sortOrder = 0, dayNumber = 1),
            exercise("explicitly-unassigned", exerciseId = 3, sortOrder = 1, dayNumber = UNASSIGNED_DAY_NUMBER),
        )

        val sorted = exercises.sortedWith(RoutineOrdering.exerciseComparator(validDayNumbers)).map { it.id }

        assertThat(sorted).containsExactly("day2", "orphaned-day1", "explicitly-unassigned").inOrder()
    }

    @Test
    fun `nextSortOrder is one past the max within the same day`() {
        val exercises = listOf(
            exercise("a", exerciseId = 1, sortOrder = 0, dayNumber = 1),
            exercise("b", exerciseId = 2, sortOrder = 2, dayNumber = 1),
            exercise("c", exerciseId = 3, sortOrder = 5, dayNumber = 2),
        )

        assertThat(RoutineOrdering.nextSortOrder(exercises, dayNumber = 1)).isEqualTo(3)
        assertThat(RoutineOrdering.nextSortOrder(exercises, dayNumber = UNASSIGNED_DAY_NUMBER)).isEqualTo(0)
    }

    @Test
    fun `nextDayNumber picks the first free day, or null if all seven are used`() {
        val days = listOf(day("a", 1, "Lunes"), day("b", 2, "Martes"))
        assertThat(RoutineOrdering.nextDayNumber(days, exercises = emptyList())).isEqualTo(3)

        val fullWeek = (1..7).map { day("d$it", it, "Día $it") }
        assertThat(RoutineOrdering.nextDayNumber(fullWeek, exercises = emptyList())).isNull()
    }

    @Test
    fun `nextDayNumber does not let a new day adopt exercises orphaned by a deleted day`() {
        // Day 1 ("Lunes") was deleted; its exercise's dayNumber (1) is still on the row.
        val remainingDays = listOf(day("b", 2, "Martes"))
        val orphanedExercise = exercise("a", exerciseId = 1, sortOrder = 0, dayNumber = 1)

        val next = RoutineOrdering.nextDayNumber(remainingDays, listOf(orphanedExercise))

        assertThat(next).isNotEqualTo(1)
        assertThat(next).isEqualTo(3)
    }

    @Test
    fun `nextDayNumber returns null when every day 1 to 7 is either a real day or an orphaned day number`() {
        val days = (1..6).map { day("d$it", it, "Día $it") }
        // Day 7 has no RoutineDay row, but an exercise still references it (orphan).
        val orphanedExercise = exercise("a", exerciseId = 1, sortOrder = 0, dayNumber = 7)

        assertThat(RoutineOrdering.nextDayNumber(days, listOf(orphanedExercise))).isNull()
    }

    private fun routine(days: List<RoutineDay>, exercises: List<RoutineExercise>) = RoutineDetail(
        id = "routine-1", userId = "user-1", name = "Push", description = null,
        daysPerWeek = null, isArchived = false, days = days, exercises = exercises,
    )

    @Test
    fun `unassignedBucketDayNumber is the sentinel once the routine has a real day`() {
        val withDays = routine(days = listOf(day("a", 1, "Lunes")), exercises = emptyList())

        assertThat(RoutineOrdering.unassignedBucketDayNumber(withDays)).isEqualTo(UNASSIGNED_DAY_NUMBER)
    }

    @Test
    fun `unassignedBucketDayNumber reuses 1 for legacy RN routines with no days`() {
        val legacy = routine(
            days = emptyList(),
            exercises = listOf(exercise("a", exerciseId = 1, sortOrder = 0, dayNumber = 1)),
        )

        assertThat(RoutineOrdering.unassignedBucketDayNumber(legacy)).isEqualTo(1)
    }

    @Test
    fun `unassignedBucketDayNumber reuses 0 for template-adopted routines with no days`() {
        // AdoptTemplateUseCase inserts at UNASSIGNED_DAY_NUMBER (0), not 1 (review carry-over).
        val adopted = routine(
            days = emptyList(),
            exercises = listOf(exercise("a", exerciseId = 1, sortOrder = 0, dayNumber = UNASSIGNED_DAY_NUMBER)),
        )

        assertThat(RoutineOrdering.unassignedBucketDayNumber(adopted)).isEqualTo(UNASSIGNED_DAY_NUMBER)
    }

    @Test
    fun `unassignedBucketDayNumber falls back to 1 when there are no exercises yet or they disagree`() {
        val empty = routine(days = emptyList(), exercises = emptyList())
        assertThat(RoutineOrdering.unassignedBucketDayNumber(empty)).isEqualTo(1)

        val disagreeing = routine(
            days = emptyList(),
            exercises = listOf(
                exercise("a", exerciseId = 1, sortOrder = 0, dayNumber = 1),
                exercise("b", exerciseId = 2, sortOrder = 1, dayNumber = UNASSIGNED_DAY_NUMBER),
            ),
        )
        assertThat(RoutineOrdering.unassignedBucketDayNumber(disagreeing)).isEqualTo(1)
    }
}

package com.lucho314.spotter.domain.model

import java.time.Instant

/**
 * `routine_exercises.day_number` is `integer NOT NULL default 1` live (never nullable on the
 * wire - see `docs/backend/live_schema_2026-09-26.md`). An exercise with "no day assigned" is
 * represented by this sentinel value instead: [RoutineOrdering.nextDayNumber] and every day the
 * app itself ever creates are in `1..7`, so `0` never collides with a real `routine_days` row.
 */
const val UNASSIGNED_DAY_NUMBER = 0

data class RoutineDay(
    val id: String,
    val routineId: String,
    val dayNumber: Int,
    val name: String,
)

data class RoutineExercise(
    val id: String,
    val routineId: String,
    val exerciseId: Int,
    val exercise: Exercise?,
    val sortOrder: Int,
    val dayNumber: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)

data class RoutineSummary(
    val id: String,
    val name: String,
    val description: String?,
    val daysPerWeek: Int?,
    val exerciseCount: Int,
    val days: List<RoutineDay>,
    val createdAt: Instant,
)

data class RoutineDetail(
    val id: String,
    val userId: String,
    val name: String,
    val description: String?,
    val daysPerWeek: Int?,
    val isArchived: Boolean,
    val days: List<RoutineDay>,
    val exercises: List<RoutineExercise>,
) {
    /** Exercises assigned to [dayNumber], ordered by `sortOrder`. */
    fun exercisesForDay(dayNumber: Int): List<RoutineExercise> =
        exercises.filter { it.dayNumber == dayNumber }.sortedBy { it.sortOrder }

    /** Exercises whose `dayNumber` has no matching row in [days] (includes [UNASSIGNED_DAY_NUMBER]). */
    val unassignedExercises: List<RoutineExercise>
        get() {
            val validDayNumbers = days.map { it.dayNumber }.toSet()
            return exercises
                .filter { it.dayNumber !in validDayNumbers }
                .sortedBy { it.sortOrder }
        }
}

data class RoutineInput(
    val name: String,
    val description: String?,
    val daysPerWeek: Int?,
)

/** [dayNumber]: a real day 1..7, or [UNASSIGNED_DAY_NUMBER] - never null (the DB column isn't). */
data class NewRoutineExercise(
    val exerciseId: Int,
    val dayNumber: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)

/**
 * [dayNumber] `null` means "leave the day unchanged"; it is never encoded as a JSON null.
 * [sortOrder] `null` means "leave it unchanged" too - only moving an exercise to a different day
 * should set it, to the destination group's next free slot (see
 * [com.lucho314.spotter.domain.calc.RoutineOrdering.nextSortOrderIn]), so it doesn't collide with
 * (or interleave with, forming ties) whatever is already there.
 */
data class RoutineExercisePatch(
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val dayNumber: Int?,
    val sortOrder: Int? = null,
)

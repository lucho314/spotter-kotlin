package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER

/** Deterministic ordering for routine days and exercises, shared by every routine screen. */
object RoutineOrdering {

    val dayComparator: Comparator<RoutineDay> = compareBy({ it.dayNumber }, { it.name }, { it.id })

    /**
     * Exercises ordered by day, then `sortOrder`/`exerciseId`/`id`. "Unassigned" means `dayNumber`
     * isn't one of [validDayNumbers] - kept consistent with
     * [com.lucho314.spotter.domain.model.RoutineDetail.unassignedExercises]: this covers
     * [UNASSIGNED_DAY_NUMBER] *and* a legacy `dayNumber` orphaned by a deleted day (deleting a day
     * never rewrites its exercises' `dayNumber`, see [nextDayNumber]'s KDoc), so both sort last the
     * same way.
     */
    fun exerciseComparator(validDayNumbers: Set<Int>): Comparator<RoutineExercise> = compareBy(
        { if (it.dayNumber in validDayNumbers) it.dayNumber else Int.MAX_VALUE },
        { it.sortOrder },
        { it.exerciseId },
        { it.id },
    )

    /** Next `sort_order` within [dayNumber] (including [UNASSIGNED_DAY_NUMBER]). */
    fun nextSortOrder(exercises: List<RoutineExercise>, dayNumber: Int): Int =
        nextSortOrderIn(exercises.filter { it.dayNumber == dayNumber })

    /**
     * Next `sort_order` to append at the end of an already-resolved group of exercises (e.g. one
     * [com.lucho314.spotter.feature.routines.detail.RoutineDayGroup]'s current contents, or the
     * "unassigned"/legacy-flat bucket resolved by `AddExerciseViewModel`). Unlike [nextSortOrder],
     * this does **not** re-filter by `dayNumber`: the caller must have already resolved which
     * exercises visually belong to the target group - which, for the "unassigned" bucket, is a mix
     * of [UNASSIGNED_DAY_NUMBER] and orphaned real numbers that a naive `dayNumber == X` filter
     * would miss (review issue: this caused duplicate-looking entries and a wrong sort order).
     */
    fun nextSortOrderIn(groupExercises: List<RoutineExercise>): Int =
        (groupExercises.maxOfOrNull { it.sortOrder } ?: -1) + 1

    /**
     * First day number in 1..7 not already used by [days], **or referenced by any [exercises]**
     * (including orphans: an exercise whose `dayNumber` has no matching [RoutineDay] row, e.g. left
     * behind by `deleteDay`, which never rewrites its exercises' `day_number` - by design, so the
     * exercises aren't destructively reassigned). Without also excluding orphaned numbers here,
     * creating a new day right after deleting one with exercises would silently "adopt" them: the
     * new day would happen to reuse the deleted day's number.
     *
     * Null if all 7 are taken (by a day or by an orphan) - callers should surface this as a
     * validation error, not fall back to a number that would cause the adoption above.
     */
    fun nextDayNumber(days: List<RoutineDay>, exercises: List<RoutineExercise>): Int? {
        val used = days.mapTo(mutableSetOf()) { it.dayNumber }
        for (exercise in exercises) {
            if (exercise.dayNumber != UNASSIGNED_DAY_NUMBER) used += exercise.dayNumber
        }
        return (1..7).firstOrNull { it !in used }
    }

    /**
     * Day number to use for the very first day created on a routine that currently has none
     * (review carry-over: legacy RN routines never had `routine_days` rows and put every exercise
     * on `day_number = 1` regardless). [nextDayNumber] alone would treat that `1` as "taken" (same
     * orphan-exclusion rule that protects against a new day silently adopting a *different*
     * deleted day's leftovers) and suggest `2`, orphaning those exercises the moment the user
     * organizes the routine by days - the opposite of what they asked for.
     *
     * If every one of [exercises] that already has a real day number (i.e. isn't
     * [UNASSIGNED_DAY_NUMBER]) agrees on a single number, this reuses it so they land in the new
     * day. Otherwise (no exercises yet, or they disagree) it falls back to `1`, same as
     * [nextDayNumber] would with no days and no orphans.
     *
     * Only meaningful when the routine has no [RoutineDay] rows yet; callers must use
     * [nextDayNumber] once at least one day exists.
     */
    fun suggestedFirstDayNumber(exercises: List<RoutineExercise>): Int {
        val existingDayNumbers = exercises.map { it.dayNumber }.filter { it != UNASSIGNED_DAY_NUMBER }.distinct()
        return existingDayNumbers.singleOrNull() ?: 1
    }

    /**
     * The `day_number` a newly-added or moved "sin día asignado"/legacy-flat exercise should get
     * (review issue: `AddExerciseViewModel`/`RoutineDetailViewModel` used to always use
     * [UNASSIGNED_DAY_NUMBER], which mismatched what the UI actually shows as one group for a
     * dayless routine - see [RoutineDetail.unassignedExercises]).
     *
     * Once [routine] has at least one real [RoutineDay], the sentinel [UNASSIGNED_DAY_NUMBER] is
     * used, so "Sin día asignado" stays a distinct bucket from any real day.
     *
     * While it has none yet, every exercise is "unassigned" (the whole routine renders as one flat
     * list): this reuses whichever single `dayNumber` they already all share, falling back to `1`
     * only when there isn't one (no exercises yet, or they disagree) - the same
     * share-if-there-is-one/fall-back-to-1 rule as [suggestedFirstDayNumber], and for the same
     * reason. Plain `1` unconditionally (the original rule, RN convention for its legacy flat
     * routines) mismatched routines adopted from a template
     * ([com.lucho314.spotter.domain.usecase.AdoptTemplateUseCase]), which insert their exercises at
     * [UNASSIGNED_DAY_NUMBER] (`0`) instead of `1` and have no `routine_days` rows either - the
     * bucket would then be `1` while every exercise sits at `0`, so a newly added exercise landed
     * in a second, empty-looking "day 1" instead of joining the others (review carry-over).
     *
     * [RoutineDetail.unassignedExercises] is "this bucket's current visible contents" in both
     * cases - it already treats every `dayNumber` with no matching [RoutineDay] row as unassigned,
     * whatever the literal stored number (0, legacy 1, or an orphan), which is why callers should
     * use it - not a `dayNumber == X` filter - to compute "already added"/[nextSortOrderIn] here.
     */
    fun unassignedBucketDayNumber(routine: RoutineDetail): Int {
        if (routine.days.isNotEmpty()) return UNASSIGNED_DAY_NUMBER
        val sharedDayNumber = routine.exercises.map { it.dayNumber }.distinct().singleOrNull()
        return sharedDayNumber ?: 1
    }
}

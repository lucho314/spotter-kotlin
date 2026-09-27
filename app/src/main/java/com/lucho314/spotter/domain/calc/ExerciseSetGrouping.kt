package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.WorkoutSet

/** One exercise's worth of [WorkoutSet]s, as grouped by [ExerciseSetGrouping.group]. */
data class ExerciseSetGroup(val exerciseId: Int, val exerciseName: String?, val sets: List<WorkoutSet>)

/**
 * Single source of truth for how a session's sets are grouped and ordered into per-exercise
 * blocks, shared by [com.lucho314.spotter.feature.history.detail.SessionDetailViewModel] (screen)
 * and [WorkoutExportDataBuilder] (PDF/story export) so both always agree.
 */
object ExerciseSetGrouping {

    /**
     * Groups [sets] by [WorkoutSet.exerciseId] (never trusting Postgrest's row order); each
     * group's name is its first non-null [WorkoutSet.exerciseName], and its sets are ordered by
     * [WorkoutSet.setNumber]. Groups are ordered by their earliest [WorkoutSet.completedAt],
     * tie-broken by [WorkoutSet.exerciseId].
     */
    fun group(sets: List<WorkoutSet>): List<ExerciseSetGroup> =
        sets.groupBy { it.exerciseId }
            .map { (exerciseId, exerciseSets) ->
                ExerciseSetGroup(
                    exerciseId = exerciseId,
                    exerciseName = exerciseSets.firstNotNullOfOrNull { it.exerciseName },
                    sets = exerciseSets.sortedBy { it.setNumber },
                )
            }
            .sortedWith(compareBy({ group -> group.sets.minOf { it.completedAt } }, { it.exerciseId }))
}

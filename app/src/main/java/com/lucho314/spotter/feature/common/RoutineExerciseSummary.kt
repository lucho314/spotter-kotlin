package com.lucho314.spotter.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lucho314.spotter.R

/**
 * "N series × N reps · Ns descanso", used by both `RoutineDetailScreen` and
 * `TemplateDetailScreen`'s exercise rows. Spanish does distinguish singular/plural ("1 serie" vs
 * "3 series"), unlike the placeholder-only string this replaced - sets and reps are pluralized
 * independently since either can be 1 while the other isn't.
 */
@Composable
fun routineExerciseSummary(sets: Int, reps: Int, restSeconds: Int): String {
    val setsText = pluralStringResource(R.plurals.sets_count, sets, sets)
    val repsText = pluralStringResource(R.plurals.reps_count, reps, reps)
    return stringResource(R.string.routine_exercise_summary_format, setsText, repsText, restSeconds)
}

/** "N series × N reps", without the rest time - used by `WorkoutScreen`, which shows the rest countdown separately (as an actual timer, not a static target). */
@Composable
fun targetSetsRepsSummary(sets: Int, reps: Int): String {
    val setsText = pluralStringResource(R.plurals.sets_count, sets, sets)
    val repsText = pluralStringResource(R.plurals.reps_count, reps, reps)
    return stringResource(R.string.workout_target_sets_reps_format, setsText, repsText)
}

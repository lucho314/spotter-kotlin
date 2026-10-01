package com.lucho314.spotter.domain.model

import java.time.Instant
import kotlin.math.ceil

data class ActiveWorkout(
    val sessionId: String,
    val userId: String,
    val routineId: String?,
    val routineName: String,
    val dayName: String?,
    val startedAt: Instant,
    val weightUnit: WeightUnit,
    val currentExerciseIndex: Int,
    val rest: RestTimer?,
    val exercises: List<ActiveExercise>,
) {
    val completedSetCount: Int
        get() = exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } }
}

data class ActiveExercise(
    val rowId: Long,
    val position: Int,
    val exerciseId: Int,
    val name: String,
    val equipment: Equipment,
    val mediaUrl: String?,
    val imageUrl: String?,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val sets: List<ActiveSet>,
    /** Raw text as typed (may be blank); normalized with `ExerciseNote.normalize` when finishing. */
    val note: String? = null,
)

data class ActiveSet(
    val id: String,
    val setNumber: Int,
    val weightText: String,
    val repsText: String,
    val isWarmup: Boolean,
    val completedAt: Instant?,
) {
    val isCompleted get() = completedAt != null
}

data class RestTimer(
    val endsAt: Instant,
    val totalSeconds: Int,
) {
    /** Seconds left, rounded up, never negative. */
    fun remainingSeconds(now: Instant): Int {
        val millisLeft = endsAt.toEpochMilli() - now.toEpochMilli()
        if (millisLeft <= 0) return 0
        return ceil(millisLeft / 1000.0).toInt()
    }
}

/**
 * Outcome of [com.lucho314.spotter.domain.repository.ActiveWorkoutRepository.start]. Modeled as a
 * result, not an [com.lucho314.spotter.core.common.AppError] (ADR A9): already having an active
 * session is an expected business outcome the caller must branch on (`StartWorkoutUseCase`, FASE
 * 4, turns this into its own "resume or discard" prompt), not a failure.
 */
sealed interface ActiveWorkoutStartOutcome {
    data object Started : ActiveWorkoutStartOutcome
    data class AlreadyActive(val existingSessionId: String) : ActiveWorkoutStartOutcome
}

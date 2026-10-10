package com.lucho314.spotter.domain.model

import java.time.Instant

data class WorkoutSet(
    val id: String,
    val sessionId: String,
    val exerciseId: Int,
    val exerciseName: String?,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val isWarmup: Boolean,
    val completedAt: Instant,
) {
    /** Reps in reserve, derived from [rpe] (`rir = 10 - rpe`); null when no RPE was recorded. */
    val rir: Double? get() = rpe?.let { (10.0 - it).coerceAtLeast(0.0) }
}

data class WorkoutSessionSummary(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
)

data class WorkoutSessionDetail(
    val id: String,
    val routineName: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val notes: String?,
    val sets: List<WorkoutSet>,
    /** Per-exercise notes of this session, keyed by exercise id. */
    val exerciseNotes: Map<Int, String> = emptyMap(),
)

data class LastExerciseSession(
    val sessionId: String,
    val date: Instant,
    val sets: List<WorkoutSet>,
    /** The note written for this exercise in that session, if any. */
    val note: String? = null,
)

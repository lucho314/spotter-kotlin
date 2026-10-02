package com.lucho314.spotter.domain.model

import java.time.Instant

enum class PendingStatus { PENDING, FAILED }

data class PendingWorkout(
    val id: String,
    val userId: String,
    val routineId: String?,
    val startedAt: Instant,
    val completedAt: Instant,
    val notes: String?,
    val sets: List<PendingSet>,
    val status: PendingStatus,
    val lastError: String?,
    /** Transient-failure retry count so far; used by [com.lucho314.spotter.domain.usecase.SyncPendingWorkoutsUseCase] to cap retries of ambiguous errors (`Unauthorized`/`Unknown`) before giving up. Always 0 for a freshly created row. */
    val attempts: Int = 0,
    /** At most one per exercise, already normalized (`ExerciseNote.normalize`). */
    val exerciseNotes: List<PendingExerciseNote> = emptyList(),
)

data class PendingExerciseNote(
    val exerciseId: Int,
    val note: String,
)

data class PendingSet(
    val id: String,
    val exerciseId: Int,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val completedAt: Instant,
)

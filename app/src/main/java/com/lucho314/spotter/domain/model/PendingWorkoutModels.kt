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

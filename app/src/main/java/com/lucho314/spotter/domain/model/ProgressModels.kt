package com.lucho314.spotter.domain.model

import java.time.Instant

data class PersonalRecord(
    val id: String,
    val exerciseId: Int,
    val exerciseName: String?,
    val bestWeightKg: Double,
    val bestRepsAtWeight: Int,
    val estimated1RmKg: Double,
    val achievedAt: Instant,
    val updatedAt: Instant,
)

/** One aggregated point in an exercise's progress chart: the best set of one session. */
data class ExerciseProgressPoint(
    val sessionId: String,
    val date: Instant,
    val bestE1RmKg: Double,
    val topWeightKg: Double,
    val volumeKg: Double,
)

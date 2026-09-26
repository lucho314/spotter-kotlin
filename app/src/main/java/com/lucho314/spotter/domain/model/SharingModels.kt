package com.lucho314.spotter.domain.model

import java.time.Instant

data class SharedRoutinePreview(
    val code: ShareCode,
    val routineName: String,
    val exerciseCount: Int,
    val dayCount: Int,
)

/**
 * The shared routine's content needed to preview and import it. Kept in `domain/model` instead of
 * exposing `SharedRoutineDto` through [com.lucho314.spotter.domain.repository.SharingRepository]:
 * the domain layer must not see DTOs.
 */
data class SharedRoutineContent(
    val routineName: String,
    val description: String?,
    val daysPerWeek: Int?,
    val days: List<SharedRoutineDay>,
    val exercises: List<SharedRoutineExercise>,
    val expiresAt: Instant?,
)

data class SharedRoutineDay(
    val dayNumber: Int,
    val name: String,
)

data class SharedRoutineExercise(
    val exerciseId: Int,
    val dayNumber: Int?,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)

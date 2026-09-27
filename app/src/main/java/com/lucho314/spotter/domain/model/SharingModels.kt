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

/**
 * Output of [com.lucho314.spotter.domain.calc.SharedRoutineSanitizer.sanitize]: exactly what
 * [com.lucho314.spotter.domain.usecase.ImportSharedRoutineUseCase] will insert.
 */
data class SanitizedSharedRoutine(
    /** For the preview screen (no " (importada)" suffix). */
    val originalName: String,
    /** Name already suffixed as "<name> (importada)", clamped to 50 chars. */
    val input: RoutineInput,
    /** (dayNumber 1..7, name), distinct by dayNumber. */
    val days: List<Pair<Int, String>>,
    /** (exercise, sortOrder). */
    val exercises: List<Pair<NewRoutineExercise, Int>>,
)

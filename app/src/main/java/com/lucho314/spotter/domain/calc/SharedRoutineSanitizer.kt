package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.model.SanitizedSharedRoutine
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER

/**
 * Turns an untrusted [SharedRoutineContent] (fetched by `share_code` from another user's data,
 * section 8 B2) into exactly what [com.lucho314.spotter.domain.usecase.ImportSharedRoutineUseCase]
 * will insert: nothing from the other user is trusted to already be within Spotter's own limits
 * ([Validators]) or free of control/bidi characters ([TextSanitizer]).
 */
object SharedRoutineSanitizer {

    /** Matches `AddExercise`'s own cap and keeps a single insert well clear of any statement-size limit. */
    const val MAX_EXERCISES = 100
    const val IMPORTED_SUFFIX = " (importada)"
    const val FALLBACK_NAME = "Rutina"

    private const val MAX_NAME = 50 // Validators.routineInput
    private const val MAX_DESCRIPTION = 200
    private const val MAX_DAY_NAME = 50

    /** Null if, after de-duplication, there are more than [MAX_EXERCISES] exercises. */
    fun sanitize(content: SharedRoutineContent): SanitizedSharedRoutine? {
        val originalName = TextSanitizer.singleLine(content.routineName, MAX_NAME) ?: FALLBACK_NAME
        val description = TextSanitizer.multiLine(content.description, MAX_DESCRIPTION)
        val daysPerWeek = content.daysPerWeek?.takeIf { it in 1..7 }

        val days = content.days
            .filter { it.dayNumber in 1..7 }
            .distinctBy { it.dayNumber }
            .map { day ->
                val name = TextSanitizer.singleLine(day.name, MAX_DAY_NAME)
                    ?: SpanishWeekdays.ALL.getOrNull(day.dayNumber - 1)
                    ?: "Día ${day.dayNumber}"
                day.dayNumber to name
            }
            .sortedBy { it.first }

        val exercises = content.exercises
            .filter { it.exerciseId > 0 }
            .map { exercise ->
                val dayNumber = exercise.dayNumber?.takeIf { it in 0..7 } ?: UNASSIGNED_DAY_NUMBER
                NewRoutineExercise(
                    exerciseId = exercise.exerciseId,
                    dayNumber = dayNumber,
                    targetSets = exercise.targetSets.coerceIn(1, 20),
                    targetReps = exercise.targetReps.coerceIn(1, 100),
                    restSeconds = exercise.restSeconds.coerceIn(15, 600),
                ) to exercise.sortOrder.coerceAtLeast(0)
            }
            .sortedWith(compareBy({ it.first.dayNumber }, { it.second }, { it.first.exerciseId }))
            .distinctBy { it.first.exerciseId to it.first.dayNumber }

        if (exercises.size > MAX_EXERCISES) return null

        return SanitizedSharedRoutine(
            originalName = originalName,
            input = RoutineInput(name = importedName(content.routineName), description = description, daysPerWeek = daysPerWeek),
            days = days,
            exercises = exercises,
        )
    }

    /** `base = singleLine(original, MAX_NAME) ?: FALLBACK_NAME`; clamped so the suffixed name stays within [MAX_NAME]. */
    fun importedName(original: String?): String {
        val base = TextSanitizer.singleLine(original, MAX_NAME) ?: FALLBACK_NAME
        val maxBaseLength = (MAX_NAME - IMPORTED_SUFFIX.length).coerceAtLeast(0)
        val clampedBase = base.take(maxBaseLength).trimEnd()
        return "$clampedBase$IMPORTED_SUFFIX"
    }
}

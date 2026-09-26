package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IdGenerator
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.calc.RoutineOrdering
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.ActiveWorkoutStartOutcome
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Which of a routine's exercises to load into the new session. */
sealed interface DaySelection {
    data class Day(val dayNumber: Int) : DaySelection

    /** "Sin día asignado": [RoutineDetail.unassignedExercises]. */
    data object Unassigned : DaySelection

    /** Every exercise in the routine - only valid when the routine has no [RoutineDetail.days] at all. */
    data object All : DaySelection
}

sealed interface StartResult {
    data class Started(val sessionId: String) : StartResult

    /** The user already has [existing] in progress; the caller must ask "Continuar o descartar y empezar". */
    data class ActiveWorkoutExists(val existing: ActiveWorkout) : StartResult
}

/**
 * Starts a new workout session from [routineId], snapshotting the routine's exercises (as cached -
 * this never triggers a network refresh) into a fresh [ActiveWorkout]. Never silently overwrites an
 * in-progress session (RN bug #6, `docs/MIGRATION_PLAN.md` section 7): if one is already active for
 * [userId], this reports [StartResult.ActiveWorkoutExists] instead, and only actually discards it
 * when [replaceExisting] is `true` (the user picked "Descartar y empezar" in that prompt) - fixing
 * RN bug #7 too (`routines/[id].tsx:63`: a routine with days always started with every exercise from
 * every day), by only loading the exercises for the day the user picked ([day]).
 */
class StartWorkoutUseCase @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val preferencesRepository: PreferencesRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        userId: String,
        routineId: String,
        day: DaySelection,
        replaceExisting: Boolean,
    ): AppResult<StartResult> {
        val routine = routineRepository.observeRoutine(userId, routineId).first()
            ?: return AppResult.Failure(AppError.NotFound)

        val selectedExercises = selectExercises(routine, day)
        if (selectedExercises.isEmpty()) {
            return AppResult.Failure(AppError.Validation(ValidationReason.NO_EXERCISES))
        }

        val workout = buildWorkout(userId, routineId, routine, day, selectedExercises)
        return when (val startResult = activeWorkoutRepository.start(workout)) {
            is AppResult.Failure -> startResult
            is AppResult.Success -> when (val outcome = startResult.value) {
                ActiveWorkoutStartOutcome.Started -> AppResult.Success(StartResult.Started(workout.sessionId))
                is ActiveWorkoutStartOutcome.AlreadyActive -> resolveAlreadyActive(userId, outcome.existingSessionId, workout, replaceExisting)
            }
        }
    }

    private suspend fun resolveAlreadyActive(
        userId: String,
        existingSessionId: String,
        workout: ActiveWorkout,
        replaceExisting: Boolean,
    ): AppResult<StartResult> {
        if (!replaceExisting) {
            // Always the id ActiveWorkoutStartOutcome.AlreadyActive itself reported (review
            // carry-over) - `getActive` here is only to fetch its display data (name) for the
            // prompt, never used to pick a *different* id to act on later.
            val existing = activeWorkoutRepository.getActive(userId)
                ?: return AppResult.Failure(AppError.Unknown(null))
            return AppResult.Success(StartResult.ActiveWorkoutExists(existing))
        }
        return when (val replaceResult = activeWorkoutRepository.replace(existingSessionId, workout)) {
            is AppResult.Success -> AppResult.Success(StartResult.Started(workout.sessionId))
            is AppResult.Failure -> replaceResult
        }
    }

    private fun selectExercises(routine: RoutineDetail, day: DaySelection): List<RoutineExercise> = when (day) {
        is DaySelection.Day -> routine.exercisesForDay(day.dayNumber)
        DaySelection.Unassigned -> routine.unassignedExercises
        DaySelection.All -> routine.exercises.sortedWith(
            RoutineOrdering.exerciseComparator(routine.days.map { it.dayNumber }.toSet()),
        )
    }

    private suspend fun buildWorkout(
        userId: String,
        routineId: String,
        routine: RoutineDetail,
        day: DaySelection,
        exercises: List<RoutineExercise>,
    ): ActiveWorkout {
        val unit = preferencesRepository.weightUnit.first()
        val dayName = if (day is DaySelection.Day) routine.days.firstOrNull { it.dayNumber == day.dayNumber }?.name else null
        val activeExercises = exercises.mapIndexed { index, routineExercise ->
            ActiveExercise(
                rowId = 0L,
                position = index,
                exerciseId = routineExercise.exerciseId,
                name = routineExercise.exercise?.name.orEmpty(),
                equipment = routineExercise.exercise?.equipment ?: Equipment.OTHER,
                mediaUrl = routineExercise.exercise?.mediaUrl,
                imageUrl = routineExercise.exercise?.imageUrl,
                targetSets = routineExercise.targetSets,
                targetReps = routineExercise.targetReps,
                restSeconds = routineExercise.restSeconds,
                sets = (1..routineExercise.targetSets).map { setNumber ->
                    ActiveSet(
                        id = idGenerator.uuid(),
                        setNumber = setNumber,
                        weightText = "",
                        repsText = routineExercise.targetReps.toString(),
                        isWarmup = false,
                        completedAt = null,
                    )
                },
            )
        }
        return ActiveWorkout(
            sessionId = idGenerator.uuid(),
            userId = userId,
            routineId = routineId,
            routineName = routine.name,
            dayName = dayName,
            startedAt = timeProvider.now(),
            weightUnit = unit,
            currentExerciseIndex = 0,
            rest = null,
            exercises = activeExercises,
        )
    }
}

package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.domain.calc.ActiveSetWeight
import com.lucho314.spotter.domain.calc.WeightInputParser
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import javax.inject.Inject

/**
 * Toggles one set's completion. Completing it validates the typed weight/reps (RN bug #9, section
 * 7: `parseFloat("72,5")` silently truncated to `72` on the es-AR keyboard's comma decimal
 * separator - [WeightInputParser]/[ActiveSetWeight] accept both) and, on success, always
 * (re)starts the rest timer (RN bug #10: completing another set while a rest was already counting
 * down never restarted it) and (re)schedules the rest-finished alarm so it survives the app being
 * backgrounded. Un-completing a set only clears its `completedAt`; the running rest timer, if any,
 * is left alone.
 */
class ToggleSetCompletionUseCase @Inject constructor(
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(workout: ActiveWorkout, exerciseRowId: Long, setId: String): AppResult<Unit> {
        val exercise = workout.exercises.firstOrNull { it.rowId == exerciseRowId }
            ?: return AppResult.Failure(AppError.NotFound)
        val set = exercise.sets.firstOrNull { it.id == setId }
            ?: return AppResult.Failure(AppError.NotFound)

        if (set.isCompleted) {
            activeWorkoutRepository.setCompleted(setId, null)
            return AppResult.Success(Unit)
        }

        if (ActiveSetWeight.parse(set.weightText, exercise.equipment) == null) {
            return AppResult.Failure(AppError.Validation(ValidationReason.WEIGHT_INVALID))
        }
        if (WeightInputParser.parseReps(set.repsText) == null) {
            return AppResult.Failure(AppError.Validation(ValidationReason.REPS_RANGE))
        }

        val now = timeProvider.now()
        activeWorkoutRepository.setCompleted(setId, now)
        val rest = RestTimer(endsAt = now.plusSeconds(exercise.restSeconds.toLong()), totalSeconds = exercise.restSeconds)
        activeWorkoutRepository.setRestTimer(workout.sessionId, rest)
        restTimerAlarmScheduler.schedule(rest.endsAt)
        return AppResult.Success(Unit)
    }
}

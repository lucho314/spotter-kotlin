package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.resultOf
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.core.work.SyncScheduler
import com.lucho314.spotter.domain.calc.ActiveSetWeight
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.model.PendingSet
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import javax.inject.Inject

sealed interface FinishResult {
    data object Saved : FinishResult

    /** No set was ever completed (RN bug #11, section 7: the RN app saved an empty session). */
    data object NothingToSave : FinishResult

    /**
     * [sessionId] no longer matches the current active session for this user (already finished or
     * discarded elsewhere - e.g. another device, or a stale screen instance). Distinct from
     * [NothingToSave]: the caller must not treat this as "the user chose to discard an empty
     * workout" (review carry-over 3), since there may in fact have been completed sets that were
     * already synced.
     */
    data object SessionGone : FinishResult
}

/**
 * Finishes [sessionId]: only completed sets are kept, converted to kg per [ActiveWorkout.weightUnit]
 * (the unit is fixed for the whole session, migration plan ADR A12), and moved into the offline
 * outbox in one Room transaction ([ActiveWorkoutRepository.moveToOutbox]) - the active session row
 * and its exercises/sets disappear at the exact same instant the outbox row appears, so a process
 * death mid-finish can never lose or duplicate the workout. A completed set's own id is reused
 * as-is for the eventual `workout_sets.id` (idempotent upsert on sync, RN bug #1).
 */
class FinishWorkoutUseCase @Inject constructor(
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val syncScheduler: SyncScheduler,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(userId: String, sessionId: String): AppResult<FinishResult> = resultOf {
        val workout = activeWorkoutRepository.getActive(userId)?.takeIf { it.sessionId == sessionId }
            ?: return@resultOf FinishResult.SessionGone

        val now = timeProvider.now()
        val pendingSets = workout.exercises.flatMap { exercise ->
            exercise.sets.filter { it.isCompleted }.map { set ->
                val weightInUnit = ActiveSetWeight.parse(set.weightText, exercise.equipment) ?: 0.0
                PendingSet(
                    id = set.id,
                    exerciseId = exercise.exerciseId,
                    setNumber = set.setNumber,
                    weightKg = WeightConverter.toKg(weightInUnit, workout.weightUnit),
                    reps = set.repsText.trim().toIntOrNull() ?: 0,
                    isWarmup = set.isWarmup,
                    completedAt = set.completedAt ?: now,
                )
            }
        }
        if (pendingSets.isEmpty()) return@resultOf FinishResult.NothingToSave

        val pendingWorkout = PendingWorkout(
            id = workout.sessionId,
            userId = userId,
            routineId = workout.routineId,
            startedAt = workout.startedAt,
            completedAt = now,
            notes = null,
            sets = pendingSets,
            status = PendingStatus.PENDING,
            lastError = null,
        )
        activeWorkoutRepository.moveToOutbox(workout.sessionId, pendingWorkout)
        restTimerAlarmScheduler.cancel()
        syncScheduler.schedule()
        FinishResult.Saved
    }
}

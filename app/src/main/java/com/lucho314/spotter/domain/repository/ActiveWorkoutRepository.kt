package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.ActiveWorkoutStartOutcome
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.RestTimer
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The single source of truth for an in-progress workout (Room-backed, survives process death). */
interface ActiveWorkoutRepository {
    fun observeActive(userId: String): Flow<ActiveWorkout?>
    suspend fun getActive(userId: String): ActiveWorkout?

    /**
     * Starts [workout] as `workout.userId`'s active session - but never in place of an existing
     * one: [ActiveWorkoutStartOutcome.AlreadyActive] is returned instead of silently overwriting it
     * (that regressed the RN app's bug #6 fix - see `ActiveSessionEntity`'s KDoc). Callers that want
     * to discard the existing one first must call [replace] explicitly.
     */
    suspend fun start(workout: ActiveWorkout): AppResult<ActiveWorkoutStartOutcome>

    /** Atomically discards [existingSessionId] and starts [workout] in its place. */
    suspend fun replace(existingSessionId: String, workout: ActiveWorkout): AppResult<Unit>

    suspend fun updateSetInputs(setId: String, weightText: String, repsText: String)
    suspend fun setCompleted(setId: String, completedAt: Instant?)
    suspend fun addSet(exerciseRowId: Long, setId: String, weightText: String, repsText: String)
    suspend fun setCurrentExercise(sessionId: String, index: Int)
    suspend fun setRestTimer(sessionId: String, rest: RestTimer?)

    /**
     * Clears the rest timer only if it's still exactly [expectedEndsAt] - guards
     * `WorkoutViewModel.maybeHandleRestFinished` against wiping out a *new* rest period that
     * started (from completing another set) between it reading a stale snapshot and acting on it.
     * Returns `true` if the timer was actually cleared, `false` if it had already changed.
     */
    suspend fun clearRestTimerIfMatches(sessionId: String, expectedEndsAt: Instant): Boolean

    suspend fun discard(sessionId: String)

    /** Moves the finished workout into the outbox and clears the active session, atomically. */
    suspend fun moveToOutbox(sessionId: String, pending: PendingWorkout)
}

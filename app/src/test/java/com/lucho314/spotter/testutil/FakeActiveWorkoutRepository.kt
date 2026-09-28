package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.ActiveWorkoutStartOutcome
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fake: one active session at a time, keyed by `workout.userId` (mirrors the DB's unique index). */
class FakeActiveWorkoutRepository : ActiveWorkoutRepository {

    private val byUser = mutableMapOf<String, ActiveWorkout>()
    private val flows = mutableMapOf<String, MutableStateFlow<ActiveWorkout?>>()

    val movedToOutbox = mutableListOf<PendingWorkout>()
    var startError: AppError? = null
    var replaceError: AppError? = null

    /**
     * Test-only hook to simulate a race where [getActive] no longer agrees with what
     * [observeActive] is still emitting (e.g. the session was finished/discarded on another
     * device between a screen reading its stale `sessionId` and acting on it). Leave `false` to
     * keep both reads in sync, which is the fake's default behavior.
     */
    var useGetActiveOverride = false
    var getActiveOverride: ActiveWorkout? = null

    private fun flowFor(userId: String) = flows.getOrPut(userId) { MutableStateFlow(byUser[userId]) }

    override fun observeActive(userId: String) = flowFor(userId)

    override suspend fun getActive(userId: String): ActiveWorkout? =
        if (useGetActiveOverride) getActiveOverride else byUser[userId]

    override suspend fun start(workout: ActiveWorkout): AppResult<ActiveWorkoutStartOutcome> {
        startError?.let { return AppResult.Failure(it) }
        val existing = byUser[workout.userId]
        if (existing != null) return AppResult.Success(ActiveWorkoutStartOutcome.AlreadyActive(existing.sessionId))
        byUser[workout.userId] = workout
        flowFor(workout.userId).value = workout
        return AppResult.Success(ActiveWorkoutStartOutcome.Started)
    }

    override suspend fun replace(existingSessionId: String, workout: ActiveWorkout): AppResult<Unit> {
        replaceError?.let { return AppResult.Failure(it) }
        byUser[workout.userId] = workout
        flowFor(workout.userId).value = workout
        return AppResult.Success(Unit)
    }

    override suspend fun updateSetInputs(setId: String, weightText: String, repsText: String) {
        mutate { workout ->
            workout.copy(exercises = workout.exercises.map { exercise ->
                exercise.copy(sets = exercise.sets.map { set -> if (set.id == setId) set.copy(weightText = weightText, repsText = repsText) else set })
            })
        }
    }

    override suspend fun setCompleted(setId: String, completedAt: Instant?) {
        mutate { workout ->
            workout.copy(exercises = workout.exercises.map { exercise ->
                exercise.copy(sets = exercise.sets.map { set -> if (set.id == setId) set.copy(completedAt = completedAt) else set })
            })
        }
    }

    override suspend fun addSet(exerciseRowId: Long, setId: String, weightText: String, repsText: String) {
        mutate { workout ->
            workout.copy(exercises = workout.exercises.map { exercise ->
                if (exercise.rowId != exerciseRowId) return@map exercise
                val nextSetNumber = (exercise.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
                exercise.copy(
                    sets = exercise.sets + com.lucho314.spotter.domain.model.ActiveSet(
                        id = setId, setNumber = nextSetNumber, weightText = weightText, repsText = repsText, isWarmup = false, completedAt = null,
                    ),
                )
            })
        }
    }

    override suspend fun setCurrentExercise(sessionId: String, index: Int) {
        mutate { workout -> workout.copy(currentExerciseIndex = index) }
    }

    override suspend fun setRestTimer(sessionId: String, rest: RestTimer?) {
        mutate { workout -> workout.copy(rest = rest) }
    }

    override suspend fun clearRestTimerIfMatches(sessionId: String, expectedEndsAt: Instant): Boolean {
        val entry = byUser.entries.firstOrNull { it.value.sessionId == sessionId } ?: return false
        if (entry.value.rest?.endsAt != expectedEndsAt) return false
        byUser[entry.key] = entry.value.copy(rest = null)
        flowFor(entry.key).value = byUser[entry.key]
        return true
    }

    override suspend fun discard(sessionId: String) {
        val userId = byUser.entries.firstOrNull { it.value.sessionId == sessionId }?.key ?: return
        byUser.remove(userId)
        flowFor(userId).value = null
    }

    override suspend fun moveToOutbox(sessionId: String, pending: PendingWorkout) {
        movedToOutbox += pending
        val userId = byUser.entries.firstOrNull { it.value.sessionId == sessionId }?.key ?: return
        byUser.remove(userId)
        flowFor(userId).value = null
    }

    /** Simplification: only correct with a single active user in the fake at a time (fine for the use case tests this exists for - they only ever start one workout). */
    private inline fun mutate(transform: (ActiveWorkout) -> ActiveWorkout) {
        val entry = byUser.entries.firstOrNull() ?: return
        val updated = transform(entry.value)
        byUser[entry.key] = updated
        flowFor(entry.key).value = updated
    }
}

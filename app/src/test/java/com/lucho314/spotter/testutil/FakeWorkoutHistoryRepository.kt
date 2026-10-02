package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred

class FakeWorkoutHistoryRepository : WorkoutHistoryRepository {

    var sessionsResult: AppResult<List<WorkoutSessionSummary>> = AppResult.Success(emptyList())

    /** Overrides [sessionsResult] when set, keyed by (offset, limit) - lets a test vary the page returned. */
    var sessionsProvider: ((Int, Int) -> AppResult<List<WorkoutSessionSummary>>)? = null
    var sessionResult: AppResult<WorkoutSessionDetail> = AppResult.Failure(com.lucho314.spotter.core.common.AppError.NotFound)
    var updateSetResult: AppResult<Unit> = AppResult.Success(Unit)
    var addSetResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteSetResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteSessionResult: AppResult<Unit> = AppResult.Success(Unit)
    var lastSessionResult: AppResult<LastExerciseSession?> = AppResult.Success(null)
    var completedSinceResult: AppResult<Int> = AppResult.Success(0)
    var lastCompletedAtResult: AppResult<Instant?> = AppResult.Success(null)
    var countCompletedResult: AppResult<Int> = AppResult.Success(0)
    var setExerciseNoteResult: AppResult<Unit> = AppResult.Success(Unit)
    val setExerciseNoteCalls = mutableListOf<Triple<String, Int, String?>>()

    /** Set to make the next [updateSet] call suspend until this deferred completes (row-level guard tests). */
    var updateSetGate: CompletableDeferred<Unit>? = null

    val getLastSessionCalls = mutableListOf<Pair<String, Int>>()
    val getSessionsCalls = mutableListOf<Pair<Int, Int>>()
    val updateSetCalls = mutableListOf<Triple<String, Double, Int>>()

    data class AddSetCall(val sessionId: String, val exerciseId: Int, val setNumber: Int, val weightKg: Double, val reps: Int, val completedAt: Instant)

    val addSetCalls = mutableListOf<AddSetCall>()
    val deleteSetCalls = mutableListOf<String>()
    val deleteSessionCalls = mutableListOf<String>()
    val completedSinceCalls = mutableListOf<Pair<String, Instant>>()

    override suspend fun getSessions(userId: String, offset: Int, limit: Int): AppResult<List<WorkoutSessionSummary>> {
        getSessionsCalls += offset to limit
        return sessionsProvider?.invoke(offset, limit) ?: sessionsResult
    }

    override suspend fun getSession(sessionId: String): AppResult<WorkoutSessionDetail> = sessionResult

    override suspend fun updateSet(setId: String, weightKg: Double, reps: Int): AppResult<Unit> {
        updateSetCalls += Triple(setId, weightKg, reps)
        updateSetGate?.await()
        return updateSetResult
    }

    override suspend fun addSet(sessionId: String, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, completedAt: Instant): AppResult<Unit> {
        addSetCalls += AddSetCall(sessionId, exerciseId, setNumber, weightKg, reps, completedAt)
        return addSetResult
    }

    override suspend fun setExerciseNote(sessionId: String, exerciseId: Int, note: String?): AppResult<Unit> {
        setExerciseNoteCalls += Triple(sessionId, exerciseId, note)
        return setExerciseNoteResult
    }

    override suspend fun deleteSet(setId: String): AppResult<Unit> {
        deleteSetCalls += setId
        return deleteSetResult
    }

    override suspend fun deleteSession(sessionId: String): AppResult<Unit> {
        deleteSessionCalls += sessionId
        return deleteSessionResult
    }

    override suspend fun getLastSession(userId: String, exerciseId: Int): AppResult<LastExerciseSession?> {
        getLastSessionCalls += userId to exerciseId
        return lastSessionResult
    }

    override suspend fun getCompletedSince(userId: String, since: Instant): AppResult<Int> {
        completedSinceCalls += userId to since
        return completedSinceResult
    }

    override suspend fun getLastCompletedAt(userId: String): AppResult<Instant?> = lastCompletedAtResult

    override suspend fun countCompleted(userId: String): AppResult<Int> = countCompletedResult
}

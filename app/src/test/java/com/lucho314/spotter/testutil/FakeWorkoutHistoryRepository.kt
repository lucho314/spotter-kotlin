package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import java.time.Instant

class FakeWorkoutHistoryRepository : WorkoutHistoryRepository {

    var sessionsResult: AppResult<List<WorkoutSessionSummary>> = AppResult.Success(emptyList())
    var sessionResult: AppResult<WorkoutSessionDetail> = AppResult.Failure(com.lucho314.spotter.core.common.AppError.NotFound)
    var updateSetResult: AppResult<Unit> = AppResult.Success(Unit)
    var addSetResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteSetResult: AppResult<Unit> = AppResult.Success(Unit)
    var deleteSessionResult: AppResult<Unit> = AppResult.Success(Unit)
    var lastSessionResult: AppResult<LastExerciseSession?> = AppResult.Success(null)
    var completedSinceResult: AppResult<Int> = AppResult.Success(0)
    var lastCompletedAtResult: AppResult<Instant?> = AppResult.Success(null)
    var countCompletedResult: AppResult<Int> = AppResult.Success(0)

    val getLastSessionCalls = mutableListOf<Pair<String, Int>>()

    override suspend fun getSessions(userId: String, page: Int, pageSize: Int): AppResult<List<WorkoutSessionSummary>> = sessionsResult

    override suspend fun getSession(sessionId: String): AppResult<WorkoutSessionDetail> = sessionResult

    override suspend fun updateSet(setId: String, weightKg: Double, reps: Int): AppResult<Unit> = updateSetResult

    override suspend fun addSet(sessionId: String, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, completedAt: Instant): AppResult<Unit> = addSetResult

    override suspend fun deleteSet(setId: String): AppResult<Unit> = deleteSetResult

    override suspend fun deleteSession(sessionId: String): AppResult<Unit> = deleteSessionResult

    override suspend fun getLastSession(userId: String, exerciseId: Int): AppResult<LastExerciseSession?> {
        getLastSessionCalls += userId to exerciseId
        return lastSessionResult
    }

    override suspend fun getCompletedSince(userId: String, since: Instant): AppResult<Int> = completedSinceResult

    override suspend fun getLastCompletedAt(userId: String): AppResult<Instant?> = lastCompletedAtResult

    override suspend fun countCompleted(userId: String): AppResult<Int> = countCompletedResult
}

package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import java.time.Instant

interface WorkoutHistoryRepository {
    suspend fun getSessions(userId: String, page: Int, pageSize: Int = 30): AppResult<List<WorkoutSessionSummary>>
    suspend fun getSession(sessionId: String): AppResult<WorkoutSessionDetail>
    suspend fun updateSet(setId: String, weightKg: Double, reps: Int): AppResult<Unit>

    /** `completedAt` should be the session's completion time (or start time), never "now" (bug 13). */
    suspend fun addSet(sessionId: String, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, completedAt: Instant): AppResult<Unit>
    suspend fun deleteSet(setId: String): AppResult<Unit>

    /** Deletes with an exact row count; 0 rows affected maps to `Server("not_deleted")`. */
    suspend fun deleteSession(sessionId: String): AppResult<Unit>

    suspend fun getLastSession(userId: String, exerciseId: Int): AppResult<LastExerciseSession?>
    suspend fun getCompletedSince(userId: String, since: Instant): AppResult<Int>
    suspend fun getLastCompletedAt(userId: String): AppResult<Instant?>
    suspend fun countCompleted(userId: String): AppResult<Int>
}

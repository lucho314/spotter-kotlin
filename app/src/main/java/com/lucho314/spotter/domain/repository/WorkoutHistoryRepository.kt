package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import java.time.Instant

/** 0-based row offset, 30 rows per page (history list's "Cargar más"). */
const val HISTORY_PAGE_SIZE = 30

interface WorkoutHistoryRepository {
    suspend fun getSessions(userId: String, offset: Int, limit: Int = HISTORY_PAGE_SIZE): AppResult<List<WorkoutSessionSummary>>
    suspend fun getSession(sessionId: String): AppResult<WorkoutSessionDetail>

    /** 0 rows affected (RLS: not this user's set) maps to [com.lucho314.spotter.core.common.AppError.NotFound]. */
    suspend fun updateSet(setId: String, weightKg: Double, reps: Int): AppResult<Unit>

    /** `completedAt` should be the session's completion time (or start time), never "now" (bug 13). */
    suspend fun addSet(sessionId: String, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, completedAt: Instant): AppResult<Unit>

    /** 0 rows affected (RLS: not this user's set) maps to [com.lucho314.spotter.core.common.AppError.NotFound]. */
    suspend fun deleteSet(setId: String): AppResult<Unit>

    /** Deletes with an exact row count; 0 rows affected maps to `Server("not_deleted")`. */
    suspend fun deleteSession(sessionId: String): AppResult<Unit>

    /** Saves [note] (normalized) for that exercise of the session; a blank/null note deletes it. */
    suspend fun setExerciseNote(sessionId: String, exerciseId: Int, note: String?): AppResult<Unit>

    suspend fun getLastSession(userId: String, exerciseId: Int): AppResult<LastExerciseSession?>
    suspend fun getCompletedSince(userId: String, since: Instant): AppResult<Int>
    suspend fun getLastCompletedAt(userId: String): AppResult<Instant?>
    suspend fun countCompleted(userId: String): AppResult<Int>
}

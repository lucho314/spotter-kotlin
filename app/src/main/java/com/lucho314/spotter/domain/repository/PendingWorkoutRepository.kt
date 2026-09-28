package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.PendingWorkout
import kotlinx.coroutines.flow.Flow

interface PendingWorkoutRepository {
    /** Counts both `PENDING` and `FAILED` rows. */
    fun observeCount(userId: String): Flow<Int>
    fun observeFailed(userId: String): Flow<List<PendingWorkout>>

    /** Only `PENDING` rows. */
    suspend fun getPending(userId: String): List<PendingWorkout>

    /** Idempotent upsert of the session then its sets (`onConflict = "id", ignoreDuplicates = true`). */
    suspend fun upload(workout: PendingWorkout): AppResult<Unit>

    /**
     * Best-effort delete of the *server-side* session `id` (never throws) - used right before
     * permanently marking a row `FAILED` so a session whose own upload succeeded but whose sets
     * then failed permanently doesn't leave an orphan, set-less `workout_sessions` row behind
     * (observation from the FASE 4 review). A no-op if the session was never created either.
     */
    suspend fun deleteRemoteSession(id: String)

    suspend fun delete(id: String)
    suspend fun markFailed(id: String, error: String)
    suspend fun resetToPending(id: String)
    suspend fun recordAttempt(id: String, error: String)
}

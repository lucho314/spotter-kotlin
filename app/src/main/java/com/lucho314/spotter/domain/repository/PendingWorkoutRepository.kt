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
    suspend fun delete(id: String)
    suspend fun markFailed(id: String, error: String)
    suspend fun resetToPending(id: String)
    suspend fun recordAttempt(id: String, error: String)
}

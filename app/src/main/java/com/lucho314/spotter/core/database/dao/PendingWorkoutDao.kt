package com.lucho314.spotter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutWithSets
import kotlinx.coroutines.flow.Flow

private const val STATUS_PENDING = "PENDING"
private const val STATUS_FAILED = "FAILED"

@Dao
interface PendingWorkoutDao {

    @Query("SELECT COUNT(*) FROM pending_workout WHERE user_id = :userId AND status IN ('$STATUS_PENDING', '$STATUS_FAILED')")
    fun observeCount(userId: String): Flow<Int>

    @Transaction
    @Query("SELECT * FROM pending_workout WHERE user_id = :userId AND status = '$STATUS_FAILED' ORDER BY created_at_epoch_ms DESC")
    fun observeFailed(userId: String): Flow<List<PendingWorkoutWithSets>>

    @Transaction
    @Query("SELECT * FROM pending_workout WHERE user_id = :userId AND status = '$STATUS_PENDING' ORDER BY created_at_epoch_ms ASC")
    suspend fun getPending(userId: String): List<PendingWorkoutWithSets>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: PendingWorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<PendingWorkoutSetEntity>)

    @Transaction
    suspend fun insertFull(workout: PendingWorkoutEntity, sets: List<PendingWorkoutSetEntity>) {
        insertWorkout(workout)
        insertSets(sets)
    }

    @Query("DELETE FROM pending_workout WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE pending_workout SET status = '$STATUS_FAILED', last_error = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("UPDATE pending_workout SET status = '$STATUS_PENDING', last_error = NULL WHERE id = :id")
    suspend fun resetToPending(id: String)

    @Query("UPDATE pending_workout SET attempts = attempts + 1, last_error = :error WHERE id = :id")
    suspend fun recordAttempt(id: String, error: String)
}

package com.lucho314.spotter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lucho314.spotter.core.database.entity.GarminUploadEntity
import kotlinx.coroutines.flow.Flow

private const val STATUS_PENDING = "PENDING"
private const val STATUS_UPLOADED = "UPLOADED"
private const val STATUS_FAILED = "FAILED"

const val GARMIN_ENQUEUE_RESULT_ENQUEUED = "ENQUEUED"
const val GARMIN_ENQUEUE_RESULT_ALREADY_PENDING = "ALREADY_PENDING"
const val GARMIN_ENQUEUE_RESULT_ALREADY_UPLOADED = "ALREADY_UPLOADED"

@Dao
interface GarminUploadDao {

    @Query("SELECT * FROM garmin_upload WHERE user_id = :userId AND status = '$STATUS_PENDING' ORDER BY created_at_epoch_ms ASC")
    suspend fun getPending(userId: String): List<GarminUploadEntity>

    @Query("SELECT * FROM garmin_upload WHERE workout_id = :workoutId")
    suspend fun get(workoutId: String): GarminUploadEntity?

    @Query("SELECT status FROM garmin_upload WHERE workout_id = :workoutId")
    fun observeStatus(workoutId: String): Flow<String?>

    @Query("SELECT COUNT(*) FROM garmin_upload WHERE user_id = :userId AND status = '$STATUS_FAILED'")
    fun observeFailedCount(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: GarminUploadEntity)

    @Query(
        "UPDATE garmin_upload SET status = '$STATUS_PENDING', attempts = 0, last_error = NULL, " +
            "payload_json = COALESCE(:payload, payload_json), updated_at_epoch_ms = :now WHERE workout_id = :id",
    )
    suspend fun requeue(id: String, payload: String?, now: Long)

    @Query("UPDATE garmin_upload SET payload_json = :payload, updated_at_epoch_ms = :now WHERE workout_id = :id AND payload_json IS NULL")
    suspend fun fillPayloadIfMissing(id: String, payload: String, now: Long)

    /**
     * `null` row -> insert (ENQUEUED). `UPLOADED` -> ALREADY_UPLOADED, untouched (idempotent).
     * `PENDING` -> fills a missing payload if [entity] brought one, ALREADY_PENDING either way.
     * `FAILED` -> [requeueFailed] decides between a fresh retry (ENQUEUED) or leaving it be.
     */
    @Transaction
    suspend fun enqueue(entity: GarminUploadEntity, requeueFailed: Boolean): String {
        val existing = get(entity.workoutId)
        return when {
            existing == null -> {
                insert(entity)
                GARMIN_ENQUEUE_RESULT_ENQUEUED
            }
            existing.status == STATUS_UPLOADED -> GARMIN_ENQUEUE_RESULT_ALREADY_UPLOADED
            existing.status == STATUS_FAILED && requeueFailed -> {
                requeue(entity.workoutId, entity.payloadJson, entity.updatedAtEpochMs)
                GARMIN_ENQUEUE_RESULT_ENQUEUED
            }
            else -> {
                if (entity.payloadJson != null) fillPayloadIfMissing(entity.workoutId, entity.payloadJson, entity.updatedAtEpochMs)
                GARMIN_ENQUEUE_RESULT_ALREADY_PENDING
            }
        }
    }

    @Query(
        "UPDATE garmin_upload SET status = '$STATUS_UPLOADED', garmin_activity_id = :activityId, " +
            "garmin_upload_id = :uploadId, payload_json = NULL, last_error = NULL, updated_at_epoch_ms = :now WHERE workout_id = :id",
    )
    suspend fun markUploaded(id: String, activityId: Long?, uploadId: Long?, now: Long)

    @Query("UPDATE garmin_upload SET attempts = attempts + 1, last_error = :error, updated_at_epoch_ms = :now WHERE workout_id = :id")
    suspend fun recordAttempt(id: String, error: String, now: Long)

    @Query("UPDATE garmin_upload SET status = '$STATUS_FAILED', attempts = attempts + 1, last_error = :error, updated_at_epoch_ms = :now WHERE workout_id = :id")
    suspend fun markFailed(id: String, error: String, now: Long)

    @Query("UPDATE garmin_upload SET status = '$STATUS_PENDING', attempts = 0, last_error = NULL, updated_at_epoch_ms = :now WHERE user_id = :userId AND status = '$STATUS_FAILED'")
    suspend fun resetFailedToPending(userId: String, now: Long): Int

    /** Sign-out and disconnect cleanup: UPLOADED rows are kept for idempotency (see [com.lucho314.spotter.domain.usecase.DisconnectGarminUseCase]). */
    @Query("DELETE FROM garmin_upload WHERE user_id = :userId AND status != '$STATUS_UPLOADED'")
    suspend fun deleteNotUploaded(userId: String)

    /** Sign-out cleanup ([com.lucho314.spotter.domain.repository.LocalDataRepository]). */
    @Query("DELETE FROM garmin_upload")
    suspend fun deleteAll()
}

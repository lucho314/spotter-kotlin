package com.lucho314.spotter.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per workout that should (or already did) go to Garmin Connect. Independent from
 * `pending_workout` (that outbox is deleted once synced to Supabase; this table is Garmin's own
 * idempotency record and survives long after). `payload_json` holds the [com.lucho314.spotter.domain.model.GarminActivitySnapshot]
 * as JSON for the automatic path (right after finishing); `null` means the worker should look the
 * session up in [com.lucho314.spotter.domain.repository.WorkoutHistoryRepository] (the manual path).
 */
@Entity(tableName = "garmin_upload", indices = [Index(value = ["user_id", "status", "created_at_epoch_ms"])])
data class GarminUploadEntity(
    @PrimaryKey @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    /** PENDING | UPLOADED | FAILED. */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "garmin_activity_id") val garminActivityId: Long?,
    @ColumnInfo(name = "garmin_upload_id") val garminUploadId: Long?,
    @ColumnInfo(name = "payload_json") val payloadJson: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

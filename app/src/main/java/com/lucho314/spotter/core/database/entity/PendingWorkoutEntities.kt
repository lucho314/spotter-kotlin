package com.lucho314.spotter.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Outbox row for a finished workout (ADR A3-b): `FinishWorkoutUseCase` always writes here first;
 * `SyncWorkoutsWorker` uploads it with an idempotent upsert and only then deletes the row.
 */
@Entity(
    tableName = "pending_workout",
    indices = [Index("user_id")],
)
data class PendingWorkoutEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "routine_id") val routineId: String?,
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "completed_at") val completedAt: String,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Entity(
    tableName = "pending_workout_set",
    foreignKeys = [
        ForeignKey(
            entity = PendingWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_id")],
)
data class PendingWorkoutSetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "reps") val reps: Int,
    @ColumnInfo(name = "is_warmup") val isWarmup: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: String,
)

data class PendingWorkoutWithSets(
    @Embedded val workout: PendingWorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workout_id")
    val sets: List<PendingWorkoutSetEntity>,
)

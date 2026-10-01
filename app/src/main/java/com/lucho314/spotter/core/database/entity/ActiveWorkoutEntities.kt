package com.lucho314.spotter.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * The single in-progress workout for a user (source of truth per ADR A3; survives process death).
 * `id` is the session id, reused as-is for the eventual `workout_sessions` row. `user_id` is
 * `unique`, and every insert of this entity uses `OnConflictStrategy.ABORT` (see
 * `ActiveWorkoutDao`): starting a session while one is already active for that user **fails**
 * instead of silently overwriting it (that regressed the RN app's bug #6 fix, which the offline
 * workout redesign exists to avoid: losing an in-progress workout's exercises/sets by cascade when
 * a second one gets inserted). The only sanctioned way to remove an existing active session is the
 * explicit `ActiveWorkoutDao.replaceActive`/`ActiveWorkoutRepository.replace` transaction, driven by
 * the user picking "Descartar y empezar" in `StartWorkoutUseCase`'s resume-or-replace flow.
 */
@Entity(
    tableName = "active_session",
    indices = [Index("user_id", unique = true)],
)
data class ActiveSessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "routine_id") val routineId: String?,
    @ColumnInfo(name = "routine_name") val routineName: String,
    @ColumnInfo(name = "day_name") val dayName: String?,
    @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @ColumnInfo(name = "weight_unit") val weightUnit: String,
    @ColumnInfo(name = "current_exercise_index") val currentExerciseIndex: Int,
    @ColumnInfo(name = "rest_ends_at_epoch_ms") val restEndsAtEpochMs: Long?,
    @ColumnInfo(name = "rest_total_seconds") val restTotalSeconds: Int?,
)

@Entity(
    tableName = "active_exercise",
    foreignKeys = [
        ForeignKey(
            entity = ActiveSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class ActiveExerciseEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "equipment") val equipment: String,
    @ColumnInfo(name = "media_url") val mediaUrl: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    @ColumnInfo(name = "target_reps") val targetReps: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    @ColumnInfo(name = "note") val note: String? = null,
)

@Entity(
    tableName = "active_set",
    foreignKeys = [
        ForeignKey(
            entity = ActiveExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["active_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("active_exercise_id")],
)
data class ActiveSetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "active_exercise_id") val activeExerciseId: Long,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "weight_text") val weightText: String,
    @ColumnInfo(name = "reps_text") val repsText: String,
    @ColumnInfo(name = "is_warmup") val isWarmup: Boolean,
    @ColumnInfo(name = "completed_at_epoch_ms") val completedAtEpochMs: Long?,
)

data class ActiveExerciseWithSets(
    @Embedded val exercise: ActiveExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "active_exercise_id")
    val sets: List<ActiveSetEntity>,
)

data class ActiveSessionWithExercises(
    @Embedded val session: ActiveSessionEntity,
    @Relation(entity = ActiveExerciseEntity::class, parentColumn = "id", entityColumn = "session_id")
    val exercises: List<ActiveExerciseWithSets>,
)

package com.lucho314.spotter.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.dao.GarminUploadDao
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.database.entity.GarminUploadEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity

/**
 * Offline-first storage (ADR A3): read caches, the active workout (source of truth while
 * training), the outbox of finished-but-not-yet-synced workouts, and the Garmin upload queue.
 * v1 -> v2 adds the queue; v2 -> v3 changes its lookup index. Both are automatic migrations.
 */
@Database(
    entities = [
        CachedPayloadEntity::class,
        ActiveSessionEntity::class,
        ActiveExerciseEntity::class,
        ActiveSetEntity::class,
        PendingWorkoutEntity::class,
        PendingWorkoutSetEntity::class,
        GarminUploadEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun cachedPayloadDao(): CachedPayloadDao
    abstract fun activeWorkoutDao(): ActiveWorkoutDao
    abstract fun pendingWorkoutDao(): PendingWorkoutDao
    abstract fun garminUploadDao(): GarminUploadDao
}

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
 * training), the outbox of finished-but-not-yet-synced workouts, and (v2) the Garmin upload queue.
 * v1 -> v2 only adds a table, which `AutoMigration` handles without a migration spec.
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
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun cachedPayloadDao(): CachedPayloadDao
    abstract fun activeWorkoutDao(): ActiveWorkoutDao
    abstract fun pendingWorkoutDao(): PendingWorkoutDao
    abstract fun garminUploadDao(): GarminUploadDao
}

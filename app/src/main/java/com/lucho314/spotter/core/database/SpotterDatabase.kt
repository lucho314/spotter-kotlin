package com.lucho314.spotter.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity

/**
 * Offline-first storage (ADR A3): read caches, the active workout (source of truth while
 * training) and the outbox of finished-but-not-yet-synced workouts.
 */
@Database(
    entities = [
        CachedPayloadEntity::class,
        ActiveSessionEntity::class,
        ActiveExerciseEntity::class,
        ActiveSetEntity::class,
        PendingWorkoutEntity::class,
        PendingWorkoutSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SpotterDatabase : RoomDatabase() {
    abstract fun cachedPayloadDao(): CachedPayloadDao
    abstract fun activeWorkoutDao(): ActiveWorkoutDao
    abstract fun pendingWorkoutDao(): PendingWorkoutDao
}

package com.lucho314.spotter.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read-cache row for the "cache-then-network" repositories (routines, routine detail, exercise
 * catalog, muscle groups): [json] is the serialized DTO payload used both offline and to render
 * instantly while a refresh is in flight.
 *
 * Keys: `routines:active`, `routines:archived`, `routine:{id}`, `exercises:catalog`,
 * `muscle_groups`. The catalog/muscle-group rows use `userId = ""` (device-wide, not per-user).
 */
@Entity(tableName = "cached_payload")
data class CachedPayloadEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "json") val json: String,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

package com.lucho314.spotter.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedPayloadDao {
    @Query("SELECT * FROM cached_payload WHERE `key` = :key AND user_id = :userId")
    fun observe(key: String, userId: String): Flow<CachedPayloadEntity?>

    @Query("SELECT * FROM cached_payload WHERE `key` = :key AND user_id = :userId")
    suspend fun get(key: String, userId: String): CachedPayloadEntity?

    @Upsert
    suspend fun upsert(entity: CachedPayloadEntity)

    @Query("DELETE FROM cached_payload WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Deletes every row whose key starts with [prefix] (e.g. clearing all `routine:*` entries). */
    @Query("DELETE FROM cached_payload WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)
}

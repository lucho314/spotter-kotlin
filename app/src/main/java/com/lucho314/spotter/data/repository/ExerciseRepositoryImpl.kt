package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.DefaultDispatcher
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.notNullOrNotFound
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.remote.datasource.ExerciseRemoteDataSource
import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.data.remote.dto.MuscleGroupDto
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.MuscleGroup
import com.lucho314.spotter.domain.repository.ExerciseRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CATALOG_KEY = "exercises:catalog"
private const val MUSCLE_GROUPS_KEY = "muscle_groups"

/** The catalog and muscle groups are device-wide, not per-user (section 9.7). */
private const val CACHE_USER_ID = ""
private const val CATALOG_TTL_MS = 24 * 60 * 60 * 1000L

/**
 * Cache-then-network (ADR A3-c): reads come from [CachedPayloadDao]; [refreshCatalog] fills it.
 * Cache decoding never throws: a stale/corrupt row is treated as absent and deleted so the next
 * [refreshCatalog] regenerates it, instead of crashing whatever is collecting `observe*`.
 */
@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val remote: ExerciseRemoteDataSource,
    private val cacheDao: CachedPayloadDao,
    private val json: Json,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override fun observeCatalog(): Flow<List<Exercise>> =
        cacheDao.observe(CATALOG_KEY, CACHE_USER_ID)
            .distinctUntilChanged()
            .map { entity -> entity?.let { decodeExercisesOrClear(it) } ?: emptyList() }
            .flowOn(defaultDispatcher)

    override fun observeMuscleGroups(): Flow<List<MuscleGroup>> =
        cacheDao.observe(MUSCLE_GROUPS_KEY, CACHE_USER_ID)
            .distinctUntilChanged()
            .map { entity -> entity?.let { decodeMuscleGroupsOrClear(it) } ?: emptyList() }
            .flowOn(defaultDispatcher)

    override suspend fun refreshCatalog(force: Boolean): AppResult<Unit> = safeCall {
        withContext(ioDispatcher) {
            if (!force && isCatalogFresh()) return@withContext
            val exercises = remote.getCatalog()
            val muscleGroups = remote.getMuscleGroups()
            val now = timeProvider.now().toEpochMilli()
            cacheDao.upsert(CachedPayloadEntity(CATALOG_KEY, CACHE_USER_ID, json.encodeToString(exercises), now))
            cacheDao.upsert(CachedPayloadEntity(MUSCLE_GROUPS_KEY, CACHE_USER_ID, json.encodeToString(muscleGroups), now))
        }
    }

    override suspend fun getExercise(id: Int): AppResult<Exercise> {
        val cachedResult = safeCall {
            cacheDao.get(CATALOG_KEY, CACHE_USER_ID)?.let { decodeExercisesOrClear(it) }?.firstOrNull { it.id == id }
        }
        val cached = (cachedResult as? AppResult.Success)?.value
        if (cached != null) return AppResult.Success(cached)

        return safeCall { remote.getExercise(id)?.toDomain() }.notNullOrNotFound()
    }

    private suspend fun isCatalogFresh(): Boolean {
        val cached = cacheDao.get(CATALOG_KEY, CACHE_USER_ID) ?: return false
        return timeProvider.now().toEpochMilli() - cached.updatedAtEpochMs < CATALOG_TTL_MS
    }

    private suspend fun decodeExercisesOrClear(entity: CachedPayloadEntity): List<Exercise> = try {
        json.decodeFromString<List<ExerciseDto>>(entity.json).map { it.toDomain() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        // Covers kotlinx.serialization.SerializationException too (it extends this).
        cacheDao.delete(CATALOG_KEY)
        emptyList()
    }

    private suspend fun decodeMuscleGroupsOrClear(entity: CachedPayloadEntity): List<MuscleGroup> = try {
        json.decodeFromString<List<MuscleGroupDto>>(entity.json).map { it.toDomain() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        cacheDao.delete(MUSCLE_GROUPS_KEY)
        emptyList()
    }
}

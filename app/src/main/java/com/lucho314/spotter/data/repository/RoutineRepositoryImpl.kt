package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.DefaultDispatcher
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.requirePositiveOrNotFound
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.dayInsertDto
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.mapper.toInsertDto
import com.lucho314.spotter.data.remote.datasource.RoutineRemoteDataSource
import com.lucho314.spotter.data.remote.dto.RoutineDetailDto
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercisePatch
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.repository.RoutineRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ACTIVE_ROUTINES_KEY = "routines:active"
private const val ARCHIVED_ROUTINES_KEY = "routines:archived"
private fun routineDetailKey(routineId: String) = "routine:$routineId"

/**
 * Cache-then-network (ADR A3-c): `observe*` reads [CachedPayloadDao]; `refresh*` fills it from
 * [RoutineRemoteDataSource]. Every successful mutation refreshes the affected cache entries so the
 * UI (which only observes the cache) sees the change without a manual reload.
 *
 * Cache decoding never throws: a stale/corrupt row (e.g. after a DTO shape change) is treated as
 * absent and deleted so the next `refresh*` regenerates it cleanly, instead of crashing whatever
 * is collecting `observe*`.
 */
@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val remote: RoutineRemoteDataSource,
    private val cacheDao: CachedPayloadDao,
    private val json: Json,
    private val timeProvider: TimeProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : RoutineRepository {

    override fun observeRoutines(userId: String): Flow<List<RoutineSummary>> =
        cacheDao.observe(ACTIVE_ROUTINES_KEY, userId)
            .distinctUntilChanged()
            .map { decodeSummariesOrClear(it, ACTIVE_ROUTINES_KEY) }
            .flowOn(defaultDispatcher)

    override suspend fun refreshRoutines(userId: String): AppResult<Unit> = safeCall {
        val dtos = remote.getRoutines(userId)
        cacheDao.upsert(summariesCacheEntity(ACTIVE_ROUTINES_KEY, userId, dtos))
    }

    override fun observeArchivedRoutines(userId: String): Flow<List<RoutineSummary>> =
        cacheDao.observe(ARCHIVED_ROUTINES_KEY, userId)
            .distinctUntilChanged()
            .map { decodeSummariesOrClear(it, ARCHIVED_ROUTINES_KEY) }
            .flowOn(defaultDispatcher)

    override suspend fun refreshArchived(userId: String): AppResult<Unit> = safeCall {
        val dtos = remote.getArchivedRoutines(userId)
        cacheDao.upsert(summariesCacheEntity(ARCHIVED_ROUTINES_KEY, userId, dtos))
    }

    override fun observeRoutine(userId: String, routineId: String): Flow<RoutineDetail?> =
        cacheDao.observe(routineDetailKey(routineId), userId)
            .distinctUntilChanged()
            .map { decodeDetailOrClear(it, routineId) }
            .flowOn(defaultDispatcher)

    override suspend fun refreshRoutine(userId: String, routineId: String): AppResult<Unit> = safeCall {
        val dto = remote.getRoutineDetail(routineId, userId)
        if (dto == null) {
            cacheDao.delete(routineDetailKey(routineId))
        } else {
            cacheDao.upsert(
                CachedPayloadEntity(
                    key = routineDetailKey(routineId),
                    userId = userId,
                    json = json.encodeToString(dto),
                    updatedAtEpochMs = timeProvider.now().toEpochMilli(),
                ),
            )
        }
    }

    override suspend fun createRoutine(userId: String, input: RoutineInput, sourceTemplateId: String?): AppResult<String> {
        val result = safeCall { remote.insertRoutine(input.toInsertDto(userId, sourceTemplateId)) }
        if (result is AppResult.Success) refreshRoutines(userId)
        return result
    }

    override suspend fun updateRoutine(userId: String, routineId: String, input: RoutineInput): AppResult<Unit> {
        val result = safeCall {
            remote.updateRoutine(routineId, userId, input.name.trim(), input.description, input.daysPerWeek)
        }.requirePositiveOrNotFound()
        if (result is AppResult.Success) refreshRoutine(userId, routineId)
        return result
    }

    override suspend fun setArchived(userId: String, routineId: String, archived: Boolean): AppResult<Unit> {
        val result = safeCall { remote.setArchived(routineId, userId, archived) }.requirePositiveOrNotFound()
        if (result is AppResult.Success) {
            refreshRoutines(userId)
            refreshArchived(userId)
        }
        return result
    }

    override suspend fun deleteRoutine(userId: String, routineId: String): AppResult<Unit> {
        val result = safeCall { remote.deleteRoutine(routineId, userId) }.requirePositiveOrNotFound()
        if (result is AppResult.Success) {
            cacheDao.delete(routineDetailKey(routineId))
            // Removed locally right away (works fully offline); refreshRoutines/refreshArchived
            // below is only a best-effort reconciliation with the server, not required for this to
            // take effect, so its own result is intentionally not propagated.
            removeFromCachedList(ACTIVE_ROUTINES_KEY, userId, routineId)
            removeFromCachedList(ARCHIVED_ROUTINES_KEY, userId, routineId)
            refreshRoutines(userId)
            refreshArchived(userId)
        }
        return result
    }

    override suspend fun addExercise(userId: String, routineId: String, input: NewRoutineExercise, sortOrder: Int): AppResult<Unit> =
        mutateThenRefreshRoutine(userId, routineId) { remote.insertExercise(input.toInsertDto(routineId, sortOrder)) }

    override suspend fun addExercises(userId: String, routineId: String, items: List<Pair<NewRoutineExercise, Int>>): AppResult<Unit> =
        mutateThenRefreshRoutine(userId, routineId) {
            remote.insertExercises(items.map { (item, sortOrder) -> item.toInsertDto(routineId, sortOrder) })
        }

    override suspend fun updateRoutineExercise(
        userId: String,
        routineId: String,
        routineExerciseId: String,
        patch: RoutineExercisePatch,
    ): AppResult<Unit> {
        val result = safeCall {
            remote.updateRoutineExercise(routineExerciseId, patch.targetSets, patch.targetReps, patch.restSeconds, patch.dayNumber, patch.sortOrder)
        }.requirePositiveOrNotFound()
        if (result is AppResult.Success) refreshRoutine(userId, routineId)
        return result
    }

    override suspend fun removeExercise(userId: String, routineId: String, routineExerciseId: String): AppResult<Unit> {
        val result = safeCall { remote.deleteRoutineExercise(routineExerciseId) }.requirePositiveOrNotFound()
        if (result is AppResult.Success) refreshRoutine(userId, routineId)
        return result
    }

    /**
     * Sequential updates, aborting on the first error (bug 19: parallel updates with discarded
     * errors left a silent partial order). The detail cache is refreshed either way, so a partial
     * reorder never lingers as stale optimistic state.
     */
    override suspend fun reorderExercises(userId: String, routineId: String, orderedIds: List<String>): AppResult<Unit> {
        val result = safeCall {
            orderedIds.forEachIndexed { index, id ->
                val rows = remote.updateExerciseSortOrder(id, index)
                if (rows <= 0) error("routine_exercise $id not found (0 rows affected)")
            }
        }
        refreshRoutine(userId, routineId)
        return result
    }

    override suspend fun addDays(userId: String, routineId: String, days: List<Pair<Int, String>>): AppResult<Unit> =
        mutateThenRefreshRoutine(userId, routineId) {
            remote.insertDays(days.map { (dayNumber, name) -> dayInsertDto(routineId, dayNumber, name) })
        }

    override suspend fun renameDay(userId: String, routineId: String, dayId: String, name: String): AppResult<Unit> {
        val result = safeCall { remote.renameDay(dayId, name.trim()) }.requirePositiveOrNotFound()
        if (result is AppResult.Success) refreshRoutine(userId, routineId)
        return result
    }

    override suspend fun deleteDay(userId: String, routineId: String, dayId: String): AppResult<Unit> {
        val result = safeCall { remote.deleteDay(dayId) }.requirePositiveOrNotFound()
        if (result is AppResult.Success) refreshRoutine(userId, routineId)
        return result
    }

    /** Runs [mutation] and, only if it succeeds, refreshes [routineId]'s detail cache entry. */
    private suspend fun mutateThenRefreshRoutine(userId: String, routineId: String, mutation: suspend () -> Unit): AppResult<Unit> {
        val result = safeCall { mutation() }
        if (result is AppResult.Success) refreshRoutine(userId, routineId)
        return result
    }

    private suspend fun removeFromCachedList(key: String, userId: String, routineId: String) {
        val cached = cacheDao.get(key, userId) ?: return
        val list = decodeSummaryDtosOrNull(cached.json) ?: return
        val filtered = list.filterNot { it.id == routineId }
        if (filtered.size != list.size) {
            cacheDao.upsert(cached.copy(json = json.encodeToString(filtered)))
        }
    }

    private suspend fun decodeSummariesOrClear(entity: CachedPayloadEntity?, key: String): List<RoutineSummary> {
        if (entity == null) return emptyList()
        val dtos = decodeSummaryDtosOrNull(entity.json)
        if (dtos == null) {
            cacheDao.delete(key)
            return emptyList()
        }
        return dtos.map { it.toDomain() }
    }

    private suspend fun decodeDetailOrClear(entity: CachedPayloadEntity?, routineId: String): RoutineDetail? {
        if (entity == null) return null
        return try {
            json.decodeFromString<RoutineDetailDto>(entity.json).toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // Covers kotlinx.serialization.SerializationException too (it extends this).
            cacheDao.delete(routineDetailKey(routineId))
            null
        }
    }

    private fun decodeSummaryDtosOrNull(payload: String): List<RoutineSummaryDto>? = try {
        json.decodeFromString<List<RoutineSummaryDto>>(payload)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun summariesCacheEntity(key: String, userId: String, dtos: List<RoutineSummaryDto>): CachedPayloadEntity =
        CachedPayloadEntity(key, userId, json.encodeToString(dtos), timeProvider.now().toEpochMilli())
}

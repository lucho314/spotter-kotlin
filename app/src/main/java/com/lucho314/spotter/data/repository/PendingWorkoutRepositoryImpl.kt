package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.mapper.toSessionInsertDto
import com.lucho314.spotter.data.mapper.toSetInsertDto
import com.lucho314.spotter.data.remote.datasource.WorkoutRemoteDataSource
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The offline outbox (ADR A3-b): [upload] is idempotent, safe to retry after a partial failure. */
@Singleton
class PendingWorkoutRepositoryImpl @Inject constructor(
    private val dao: PendingWorkoutDao,
    private val remote: WorkoutRemoteDataSource,
) : PendingWorkoutRepository {

    override fun observeCount(userId: String): Flow<Int> = dao.observeCount(userId)

    override fun observeFailed(userId: String): Flow<List<PendingWorkout>> =
        dao.observeFailed(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPending(userId: String): List<PendingWorkout> =
        dao.getPending(userId).map { it.toDomain() }

    override suspend fun upload(workout: PendingWorkout): AppResult<Unit> = safeCall {
        remote.uploadSession(workout.toSessionInsertDto())
        remote.uploadSets(workout.sets.map { it.toSetInsertDto(workout.id) })
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun markFailed(id: String, error: String) {
        dao.markFailed(id, error)
    }

    override suspend fun resetToPending(id: String) {
        dao.resetToPending(id)
    }

    override suspend fun recordAttempt(id: String, error: String) {
        dao.recordAttempt(id, error)
    }
}

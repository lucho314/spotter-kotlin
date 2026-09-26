package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.remote.datasource.ProgressRemoteDataSource
import com.lucho314.spotter.data.remote.datasource.WorkoutRemoteDataSource
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.domain.repository.ProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Network-only, no local cache (ADR A3). */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val progressRemote: ProgressRemoteDataSource,
    private val workoutRemote: WorkoutRemoteDataSource,
) : ProgressRepository {

    override suspend fun getPersonalRecords(userId: String): AppResult<List<PersonalRecord>> = safeCall {
        progressRemote.getPersonalRecords(userId).map { it.toDomain() }
    }

    override suspend fun getLatestPersonalRecord(userId: String): AppResult<PersonalRecord?> = safeCall {
        progressRemote.getLatestPersonalRecord(userId)?.toDomain()
    }

    override suspend fun countPersonalRecords(userId: String): AppResult<Int> = safeCall {
        progressRemote.countPersonalRecords(userId)
    }

    override suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int): AppResult<List<WorkoutSet>> = safeCall {
        workoutRemote.getExerciseSets(userId, exerciseId, limit).map { it.toDomain() }
    }
}

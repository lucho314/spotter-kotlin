package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.notNullOrNotFound
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDateString
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.remote.datasource.ProfileRemoteDataSource
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.repository.ProfileRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Network-only, no local cache (ADR A3). */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val remote: ProfileRemoteDataSource,
) : ProfileRepository {

    override suspend fun getProfile(userId: String): AppResult<Profile> =
        safeCall { remote.getProfile(userId)?.toDomain() }.notNullOrNotFound()

    override suspend fun updatePhysical(
        userId: String,
        weightKg: Double?,
        heightCm: Int?,
        birthDate: LocalDate?,
        goal: String?,
    ): AppResult<Unit> = safeCall {
        remote.updatePhysical(userId, weightKg, heightCm, birthDate?.toDateString(), goal)
    }

    override suspend fun countActiveRoutines(userId: String): AppResult<Int> = safeCall {
        remote.countActiveRoutines(userId)
    }
}

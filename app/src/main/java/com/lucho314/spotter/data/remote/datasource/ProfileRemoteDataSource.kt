package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ProfileDto

interface ProfileRemoteDataSource {
    suspend fun getProfile(userId: String): ProfileDto?

    /**
     * @param goal raw `fitness_goal` column value, or null to clear it.
     * @return the number of rows actually updated (0 under RLS if [userId] isn't the caller's own profile).
     */
    suspend fun updatePhysical(userId: String, weightKg: Double?, heightCm: Int?, birthDate: String?, goal: String?): Int
    suspend fun countActiveRoutines(userId: String): Int
}

package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.ProfileRemoteDataSource
import com.lucho314.spotter.data.remote.dto.ProfileDto

/** Throws plain exceptions (per ADR A2): the repository wraps every call with `safeCall`. */
class FakeProfileRemoteDataSource : ProfileRemoteDataSource {

    var profile: ProfileDto? = null

    /** Rows "affected" by the next [updatePhysical] call, simulating RLS matching 0 rows. */
    var updatePhysicalRowsAffected: Int = 1
    var countActiveRoutinesResult: Int = 0

    var getProfileError: Throwable? = null
    var updatePhysicalError: Throwable? = null

    data class UpdatePhysicalCall(val userId: String, val weightKg: Double?, val heightCm: Int?, val birthDate: String?, val goal: String?)

    val updatePhysicalCalls = mutableListOf<UpdatePhysicalCall>()

    override suspend fun getProfile(userId: String): ProfileDto? {
        getProfileError?.let { throw it }
        return profile
    }

    override suspend fun updatePhysical(userId: String, weightKg: Double?, heightCm: Int?, birthDate: String?, goal: String?): Int {
        updatePhysicalCalls += UpdatePhysicalCall(userId, weightKg, heightCm, birthDate, goal)
        updatePhysicalError?.let { throw it }
        return updatePhysicalRowsAffected
    }

    override suspend fun countActiveRoutines(userId: String): Int = countActiveRoutinesResult
}

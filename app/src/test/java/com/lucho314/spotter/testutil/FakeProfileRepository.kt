package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.repository.ProfileRepository
import java.time.LocalDate

class FakeProfileRepository : ProfileRepository {

    var profileResult: AppResult<Profile> = AppResult.Failure(com.lucho314.spotter.core.common.AppError.NotFound)
    var updatePhysicalResult: AppResult<Unit> = AppResult.Success(Unit)
    var countActiveRoutinesResult: AppResult<Int> = AppResult.Success(0)

    data class UpdatePhysicalCall(val userId: String, val weightKg: Double?, val heightCm: Int?, val birthDate: LocalDate?, val goal: String?)

    val updatePhysicalCalls = mutableListOf<UpdatePhysicalCall>()

    override suspend fun getProfile(userId: String): AppResult<Profile> = profileResult

    override suspend fun updatePhysical(userId: String, weightKg: Double?, heightCm: Int?, birthDate: LocalDate?, goal: String?): AppResult<Unit> {
        updatePhysicalCalls += UpdatePhysicalCall(userId, weightKg, heightCm, birthDate, goal)
        return updatePhysicalResult
    }

    override suspend fun countActiveRoutines(userId: String): AppResult<Int> = countActiveRoutinesResult
}

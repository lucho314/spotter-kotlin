package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.work.GarminUploadScheduler
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import javax.inject.Inject

class ConnectGarminUseCase @Inject constructor(
    private val garminAccountRepository: GarminAccountRepository,
    private val garminUploadScheduler: GarminUploadScheduler,
) {
    suspend fun login(userId: String, email: String, password: String): GarminResult<GarminLoginResult> {
        val result = garminAccountRepository.login(userId, email.trim(), password)
        if (result is GarminResult.Success && result.value is GarminLoginResult.Connected) garminUploadScheduler.schedule()
        return result
    }

    suspend fun verifyMfa(userId: String, challengeId: String, code: String): GarminResult<Unit> {
        val result = garminAccountRepository.verifyMfa(userId, challengeId, code.trim())
        if (result is GarminResult.Success) garminUploadScheduler.schedule()
        return result
    }
}

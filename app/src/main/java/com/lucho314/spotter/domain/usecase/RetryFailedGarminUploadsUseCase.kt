package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.work.GarminUploadScheduler
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import javax.inject.Inject

class RetryFailedGarminUploadsUseCase @Inject constructor(
    private val garminUploadRepository: GarminUploadRepository,
    private val garminUploadScheduler: GarminUploadScheduler,
) {
    suspend operator fun invoke(userId: String) {
        val resetCount = garminUploadRepository.resetFailedToPending(userId)
        if (resetCount > 0) garminUploadScheduler.schedule()
    }
}

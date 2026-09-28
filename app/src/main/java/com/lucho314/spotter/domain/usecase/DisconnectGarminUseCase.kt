package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.resultOf
import com.lucho314.spotter.core.work.GarminUploadScheduler
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import javax.inject.Inject

/** `UPLOADED` rows are kept (idempotency survives a disconnect/reconnect), only PENDING/FAILED ones are dropped. */
class DisconnectGarminUseCase @Inject constructor(
    private val garminUploadScheduler: GarminUploadScheduler,
    private val garminUploadRepository: GarminUploadRepository,
    private val garminAccountRepository: GarminAccountRepository,
) {
    suspend operator fun invoke(userId: String) {
        garminUploadScheduler.cancel()
        resultOf { garminUploadRepository.deleteNotUploaded(userId) }
        garminAccountRepository.disconnect()
    }
}

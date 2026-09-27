package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.repository.SharingRepository
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Gets (or creates) the share code for [RoutineDetail], reusing an active, non-expired share if
 * there is one instead of always minting a new code (RN parity).
 */
class ShareRoutineUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val random: SecureRandom,
) {
    suspend operator fun invoke(userId: String, routine: RoutineDetail): AppResult<ShareCode> {
        // Defense in depth against B2: `shared_insert_own` doesn't itself validate this server-side.
        if (routine.userId != userId) return AppResult.Failure(AppError.NotFound)

        when (val existing = sharingRepository.findActiveShare(routine.id, userId)) {
            is AppResult.Failure -> return existing
            is AppResult.Success -> existing.value?.let { return AppResult.Success(it) }
        }

        var lastFailure: AppResult.Failure = AppResult.Failure(AppError.Conflict())
        repeat(MAX_CREATE_ATTEMPTS) {
            when (val created = sharingRepository.createShare(routine.id, userId, ShareCode.generate(random))) {
                is AppResult.Success -> return created
                is AppResult.Failure -> {
                    lastFailure = created
                    if (created.error !is AppError.Conflict) return created
                }
            }
        }
        return lastFailure
    }

    companion object {
        const val MAX_CREATE_ATTEMPTS = 3
    }
}

package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.mapper.toInstant
import com.lucho314.spotter.data.remote.datasource.SharingRemoteDataSource
import com.lucho314.spotter.data.remote.dto.SharedRoutineInsertDto
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.repository.SharingRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Network-only, no local cache (ADR A3). */
@Singleton
class SharingRepositoryImpl @Inject constructor(
    private val remote: SharingRemoteDataSource,
    private val timeProvider: TimeProvider,
) : SharingRepository {

    override suspend fun findActiveShare(routineId: String, userId: String): AppResult<ShareCode?> = safeCall {
        // `is_active` alone isn't enough: a row can still be past its `expires_at`, and the RN app
        // created a new row per "share" instead of reusing one, so more than one can exist. Picks
        // the newest one that isn't expired (checked here, not as a DB-side filter, to keep the
        // query simple) instead of assuming the newest active row is always usable.
        val now = timeProvider.now()
        val candidates = remote.findActiveShares(routineId, userId)
        val usable = candidates.firstOrNull { dto ->
            val expiresAt = dto.expiresAt?.toInstant()
            expiresAt == null || expiresAt.isAfter(now)
        }
        usable?.let { ShareCode.parse(it.shareCode) }
    }

    override suspend fun createShare(routineId: String, userId: String, code: ShareCode): AppResult<ShareCode> = safeCall {
        val inserted = remote.insertShare(
            SharedRoutineInsertDto(routineId = routineId, sharedBy = userId, shareCode = code.value),
        )
        // Falls back to the code we asked for: it's already known-valid and is what the DB stored
        // unless a trigger normalized it, which the live schema doesn't do for client-supplied codes.
        ShareCode.parse(inserted.shareCode) ?: code
    }

    override suspend fun getSharedRoutine(code: ShareCode): AppResult<SharedRoutineContent?> = safeCall {
        remote.getSharedRoutine(code.value)?.toDomain()
    }
}

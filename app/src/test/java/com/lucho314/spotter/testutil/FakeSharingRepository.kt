package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.repository.SharingRepository
import kotlinx.coroutines.CompletableDeferred

/** In-memory [SharingRepository] test double; call sites can inspect the recorded calls. */
class FakeSharingRepository : SharingRepository {

    var findActiveShareResult: AppResult<ShareCode?> = AppResult.Success(null)
    val createShareResults = ArrayDeque<AppResult<ShareCode>>()
    var sharedRoutineResult: AppResult<SharedRoutineContent?> = AppResult.Success(null)

    val createShareCodes = mutableListOf<ShareCode>()
    var findActiveShareCallCount = 0
    var getSharedRoutineCallCount = 0

    /** If set, awaited before each [createShare] returns - lets tests exercise concurrent double-tap calls. */
    var createShareGate: CompletableDeferred<Unit>? = null

    override suspend fun findActiveShare(routineId: String, userId: String): AppResult<ShareCode?> {
        findActiveShareCallCount++
        return findActiveShareResult
    }

    override suspend fun createShare(routineId: String, userId: String, code: ShareCode): AppResult<ShareCode> {
        createShareGate?.await()
        createShareCodes += code
        return if (createShareResults.isEmpty()) AppResult.Success(code) else createShareResults.removeFirst()
    }

    override suspend fun getSharedRoutine(code: ShareCode): AppResult<SharedRoutineContent?> {
        getSharedRoutineCallCount++
        return sharedRoutineResult
    }
}

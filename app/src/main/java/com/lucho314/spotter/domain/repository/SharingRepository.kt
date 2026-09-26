package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.model.SharedRoutineContent

interface SharingRepository {
    suspend fun findActiveShare(routineId: String, userId: String): AppResult<ShareCode?>
    suspend fun createShare(routineId: String, userId: String, code: ShareCode): AppResult<ShareCode>

    /** Null if [code] doesn't match any `shared_routines` row; expiry is checked by the caller. */
    suspend fun getSharedRoutine(code: ShareCode): AppResult<SharedRoutineContent?>
}

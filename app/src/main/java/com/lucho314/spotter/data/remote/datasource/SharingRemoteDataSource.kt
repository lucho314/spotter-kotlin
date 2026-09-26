package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineInsertDto

interface SharingRemoteDataSource {
    /**
     * Active shares for [routineId]/[userId], newest first, capped at a handful of rows: the RN
     * app created a new row per "share" instead of reusing one, so more than one can legitimately
     * exist. The repository picks the first one that isn't expired (checked in Kotlin, not
     * filtered here) rather than assuming the newest is always usable.
     */
    suspend fun findActiveShares(routineId: String, userId: String): List<SharedRoutineDto>
    suspend fun insertShare(dto: SharedRoutineInsertDto): SharedRoutineDto

    /** Includes the full nested routine (days + exercises) so it can be previewed/imported. */
    suspend fun getSharedRoutine(code: String): SharedRoutineDto?
}

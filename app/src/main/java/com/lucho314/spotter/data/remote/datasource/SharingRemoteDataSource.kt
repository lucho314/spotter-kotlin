package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineImportDto
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

    /**
     * Fetches only what's needed to preview/import a shared routine: no ids of the sharer/routine
     * owner, no exercise catalog join (section 2, finding 4).
     */
    suspend fun getSharedRoutine(code: String): SharedRoutineImportDto?
}

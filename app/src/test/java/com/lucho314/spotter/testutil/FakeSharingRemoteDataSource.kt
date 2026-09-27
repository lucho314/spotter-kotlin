package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.SharingRemoteDataSource
import com.lucho314.spotter.data.remote.dto.SharedRoutineDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineImportDto
import com.lucho314.spotter.data.remote.dto.SharedRoutineInsertDto

class FakeSharingRemoteDataSource : SharingRemoteDataSource {
    var activeShares: List<SharedRoutineDto> = emptyList()
    var insertedShare: SharedRoutineDto? = null
    var sharedRoutine: SharedRoutineImportDto? = null

    override suspend fun findActiveShares(routineId: String, userId: String): List<SharedRoutineDto> = activeShares

    override suspend fun insertShare(dto: SharedRoutineInsertDto): SharedRoutineDto =
        insertedShare ?: SharedRoutineDto(
            id = "share-1", routineId = dto.routineId, sharedBy = dto.sharedBy,
            shareCode = dto.shareCode, isActive = true, createdAt = "2026-01-15T10:00:00Z",
        )

    override suspend fun getSharedRoutine(code: String): SharedRoutineImportDto? = sharedRoutine
}

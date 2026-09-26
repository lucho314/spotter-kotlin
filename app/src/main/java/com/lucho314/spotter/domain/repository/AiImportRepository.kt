package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult

interface AiImportRepository {
    /** @return (routineId, routineName) on success. */
    suspend fun importFromImage(userId: String, base64Jpeg: String): AppResult<Pair<String, String>>
}

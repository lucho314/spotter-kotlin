package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AiImportedRoutine

interface AiImportRepository {
    /**
     * Sends only the image, its mime type and [userId] (the live function still requires it, B1).
     * The server's own error text is never carried through the returned [AppResult] - see
     * [com.lucho314.spotter.data.repository.AiImportErrorMapper].
     */
    suspend fun importFromImage(userId: String, base64Jpeg: String): AppResult<AiImportedRoutine>
}

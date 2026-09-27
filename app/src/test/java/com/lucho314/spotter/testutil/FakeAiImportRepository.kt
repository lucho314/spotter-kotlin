package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AiImportedRoutine
import com.lucho314.spotter.domain.repository.AiImportRepository

/** In-memory [AiImportRepository] test double. */
class FakeAiImportRepository : AiImportRepository {

    var result: AppResult<AiImportedRoutine> = AppResult.Success(AiImportedRoutine("r1", "Push"))
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun importFromImage(userId: String, base64Jpeg: String): AppResult<AiImportedRoutine> {
        calls += userId to base64Jpeg
        return result
    }
}

fun aiImportFailure(code: String): AppResult<AiImportedRoutine> = AppResult.Failure(AppError.Server(code))

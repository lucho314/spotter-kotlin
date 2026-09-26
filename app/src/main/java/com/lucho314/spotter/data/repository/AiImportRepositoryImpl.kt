package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.remote.datasource.AiImportRemoteDataSource
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageRequest
import com.lucho314.spotter.domain.repository.AiImportRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val JPEG_MIME_TYPE = "image/jpeg"

@Singleton
class AiImportRepositoryImpl @Inject constructor(
    private val remote: AiImportRemoteDataSource,
) : AiImportRepository {

    override suspend fun importFromImage(userId: String, base64Jpeg: String): AppResult<Pair<String, String>> {
        val result = safeCall {
            // `user_id` is still sent for compatibility with the live edge function (section 8, B1).
            remote.parseRoutineImage(ParseRoutineImageRequest(imageBase64 = base64Jpeg, mimeType = JPEG_MIME_TYPE, userId = userId))
        }
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val response = result.value
                val routineId = response.routineId
                val routineName = response.routineName
                if (response.error == null && routineId != null && routineName != null) {
                    AppResult.Success(routineId to routineName)
                } else {
                    AppResult.Failure(AppError.Server(message = response.error ?: "missing routine id/name"))
                }
            }
        }
    }
}

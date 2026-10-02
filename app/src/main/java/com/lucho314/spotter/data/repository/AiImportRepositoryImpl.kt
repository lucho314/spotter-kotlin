package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.data.remote.datasource.AiImportRemoteDataSource
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageRequest
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import com.lucho314.spotter.domain.model.AiImportedRoutine
import com.lucho314.spotter.domain.repository.AiImportRepository
import io.github.jan.supabase.exceptions.RestException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val JPEG_MIME_TYPE = "image/jpeg"
private const val TAG = "AiImportRepository"

@Singleton
class AiImportRepositoryImpl @Inject constructor(
    private val remote: AiImportRemoteDataSource,
    private val logger: Logger,
) : AiImportRepository {

    override suspend fun importFromImage(userId: String, base64Jpeg: String): AppResult<AiImportedRoutine> {
        val response = try {
            // The edge function derives identity from the JWT. The use case checks ownership
            // with userId after this returns; the request itself contains no user id.
            remote.parseRoutineImage(ParseRoutineImageRequest(imageBase64 = base64Jpeg, mimeType = JPEG_MIME_TYPE))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Never the exception's own message/error - see AiImportErrorMapper's KDoc.
            logger.d(TAG, "AI import failed: ${t::class.simpleName} status=${(t as? RestException)?.statusCode}")
            return AppResult.Failure(AiImportErrorMapper.map(t))
        }

        if (response.error != null) {
            logger.d(TAG, "AI import rejected by function")
            return AppResult.Failure(when (response.code) {
                "RATE_LIMITED" -> AppError.Server(AiImportErrorCodes.RATE_LIMITED)
                "IMAGE_TOO_LARGE" -> AppError.Validation(ValidationReason.IMAGE_TOO_LARGE)
                "INVALID_IMAGE", "UNSUPPORTED_IMAGE_TYPE", "INVALID_REQUEST" -> AppError.Validation(ValidationReason.IMAGE_UNREADABLE)
                "UNAUTHORIZED" -> AppError.Unauthorized
                "NO_EXERCISES_FOUND", "AI_PARSE_FAILED" -> AppError.Server(AiImportErrorCodes.REJECTED)
                null -> AppError.Server(AiImportErrorCodes.REJECTED)
                else -> AppError.Server(AiImportErrorCodes.FAILED)
            })
        }
        val routineId = response.routineId ?: return AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE))
        return AppResult.Success(AiImportedRoutine(routineId, response.routineName))
    }
}

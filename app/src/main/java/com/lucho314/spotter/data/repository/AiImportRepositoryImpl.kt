package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.Logger
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
            // `user_id` is still sent for compatibility with the live edge function (section 8, B1).
            remote.parseRoutineImage(ParseRoutineImageRequest(imageBase64 = base64Jpeg, mimeType = JPEG_MIME_TYPE, userId = userId))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Never the exception's own message/error - see AiImportErrorMapper's KDoc.
            logger.d(TAG, "AI import failed: ${t::class.simpleName} status=${(t as? RestException)?.statusCode}")
            return AppResult.Failure(AiImportErrorMapper.map(t))
        }

        if (response.error != null) {
            logger.d(TAG, "AI import rejected by function")
            return AppResult.Failure(AppError.Server(AiImportErrorCodes.REJECTED))
        }
        val routineId = response.routineId ?: return AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE))
        return AppResult.Success(AiImportedRoutine(routineId, response.routineName))
    }
}

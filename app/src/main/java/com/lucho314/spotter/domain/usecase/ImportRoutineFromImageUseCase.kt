package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.calc.TextSanitizer
import com.lucho314.spotter.domain.model.AI_IMPORT_MAX_BASE64_LENGTH
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import com.lucho314.spotter.domain.model.AiImportedRoutine
import com.lucho314.spotter.domain.repository.AiImportRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val ROUTINE_NAME_MAX_LENGTH = 100

/** Ambiguous outcomes (the routine may have been created despite the failure) that warrant a best-effort list refresh. */
private val AMBIGUOUS_ERROR_CODES = setOf(AiImportErrorCodes.TIMEOUT, AiImportErrorCodes.FAILED, AiImportErrorCodes.REJECTED)

/**
 * Imports a routine from a photo of a written program via the `parse-routine-image` edge function.
 * The function's response is never trusted at face value (section 8, B1/B6): the returned
 * `routine_id` must be a UUID and must resolve to a routine actually owned by [userId] before this
 * reports success, and the routine's displayed name always comes from the database, never from the
 * response.
 */
class ImportRoutineFromImageUseCase @Inject constructor(
    private val aiImportRepository: AiImportRepository,
    private val routineRepository: RoutineRepository,
) {
    suspend operator fun invoke(userId: String, base64Jpeg: String): AppResult<AiImportedRoutine> {
        if (base64Jpeg.isEmpty()) return AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
        if (base64Jpeg.length > AI_IMPORT_MAX_BASE64_LENGTH) return AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_TOO_LARGE))

        return when (val result = aiImportRepository.importFromImage(userId, base64Jpeg)) {
            is AppResult.Failure -> {
                if (result.error.isAmbiguous()) routineRepository.refreshRoutines(userId)
                result
            }
            is AppResult.Success -> onSuccess(userId, result.value)
        }
    }

    private suspend fun onSuccess(userId: String, response: AiImportedRoutine): AppResult<AiImportedRoutine> {
        if (!TextSanitizer.isUuid(response.routineId)) {
            routineRepository.refreshRoutines(userId)
            return AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE))
        }

        val outcome = when (routineRepository.refreshRoutine(userId, response.routineId)) {
            is AppResult.Success -> {
                val detail = routineRepository.observeRoutine(userId, response.routineId).first()
                if (detail == null) {
                    AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE))
                } else {
                    AppResult.Success(AiImportedRoutine(response.routineId, detail.name))
                }
            }
            // The function already reported success; a refresh failure (e.g. no network right
            // after) shouldn't turn that into a user-facing failure - the detail screen will retry.
            is AppResult.Failure -> AppResult.Success(
                AiImportedRoutine(response.routineId, TextSanitizer.singleLine(response.routineName, ROUTINE_NAME_MAX_LENGTH)),
            )
        }
        routineRepository.refreshRoutines(userId)
        return outcome
    }

    private fun AppError.isAmbiguous(): Boolean =
        this == AppError.Network || (this is AppError.Server && code in AMBIGUOUS_ERROR_CODES)
}

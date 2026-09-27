package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.calc.SharedRoutineSanitizer
import com.lucho314.spotter.domain.model.SanitizedSharedRoutine
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.model.SharedRoutinePreview
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.domain.repository.SharingRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Previews and imports a routine shared by [ShareCode]. Not transactional server-side, same as
 * [AdoptTemplateUseCase]: if any step after `createRoutine` fails - **including cancellation** -
 * the routine created so far is deleted as a best-effort compensation (see [AdoptTemplateUseCase]'s
 * KDoc for the shared cancellation caveat: if the cancellation lands while `createRoutine` itself is
 * in flight, the routine can be created server-side with no id known here to compensate with; this
 * is mitigated by `BackHandler` in `ImportCodeScreen`, which disables navigating away while
 * importing).
 */
class ImportSharedRoutineUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val routineRepository: RoutineRepository,
    private val timeProvider: TimeProvider,
) {

    suspend fun preview(code: ShareCode): AppResult<SharedRoutinePreview> {
        val sanitized = when (val result = loadSanitized(code)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }
        return AppResult.Success(
            SharedRoutinePreview(
                code = code,
                routineName = sanitized.originalName,
                exerciseCount = sanitized.exercises.size,
                dayCount = sanitized.days.size,
            ),
        )
    }

    /** Re-fetches (the share may have been deactivated/expired since the preview). @return the new routine id. */
    suspend fun importRoutine(code: ShareCode, userId: String): AppResult<String> {
        val sanitized = when (val result = loadSanitized(code)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        var createdRoutineId: String? = null
        try {
            val createResult = routineRepository.createRoutine(userId, sanitized.input)
            val routineId = when (createResult) {
                is AppResult.Success -> createResult.value
                is AppResult.Failure -> return createResult
            }
            createdRoutineId = routineId

            if (sanitized.days.isNotEmpty()) {
                val addDaysResult = routineRepository.addDays(userId, routineId, sanitized.days)
                if (addDaysResult is AppResult.Failure) return compensateAndReturn(userId, routineId, addDaysResult)
            }

            if (sanitized.exercises.isNotEmpty()) {
                val addExercisesResult = routineRepository.addExercises(userId, routineId, sanitized.exercises)
                if (addExercisesResult is AppResult.Failure) return compensateAndReturn(userId, routineId, addExercisesResult)
            }

            return AppResult.Success(routineId)
        } catch (e: CancellationException) {
            createdRoutineId?.let { id -> withContext(NonCancellable) { routineRepository.deleteRoutine(userId, id) } }
            throw e
        }
    }

    private suspend fun compensateAndReturn(userId: String, routineId: String, failure: AppResult.Failure): AppResult.Failure {
        routineRepository.deleteRoutine(userId, routineId)
        return failure
    }

    private suspend fun loadSanitized(code: ShareCode): AppResult<SanitizedSharedRoutine> {
        val content = when (val result = sharingRepository.getSharedRoutine(code)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value ?: return AppResult.Failure(AppError.NotFound)
        }
        val expiresAt = content.expiresAt
        if (expiresAt != null && !expiresAt.isAfter(timeProvider.now())) return AppResult.Failure(AppError.NotFound)

        val sanitized = SharedRoutineSanitizer.sanitize(content)
            ?: return AppResult.Failure(AppError.Validation(ValidationReason.SHARED_ROUTINE_INVALID))
        return AppResult.Success(sanitized)
    }
}

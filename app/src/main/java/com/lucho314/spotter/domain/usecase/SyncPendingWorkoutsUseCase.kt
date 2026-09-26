package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import javax.inject.Inject

/** Outcome for [com.lucho314.spotter.core.work.SyncWorkoutsWorker] to turn into a `Result`. */
sealed interface SyncOutcome {
    /** Every pending row for the current user was either uploaded or permanently marked `FAILED`. */
    data object Done : SyncOutcome

    /** At least one row hit a transient error; the worker should retry with backoff. */
    data object RetryLater : SyncOutcome

    /** Nobody is signed in - nothing to sync. */
    data object NoUser : SyncOutcome
}

/**
 * Uploads every `PENDING` outbox row for the current user (RN bug #5, section 7: the RN queue
 * wasn't scoped to a user at all, so it kept trying - forever - to upload a previous account's
 * workouts under the new one's RLS). [PendingWorkoutRepository.upload] is idempotent
 * (`onConflict = "id", ignoreDuplicates = true`), so retrying a row that actually already made it
 * to the server (e.g. the upload succeeded but the app died before the outbox row was deleted) is
 * always safe.
 */
class SyncPendingWorkoutsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val pendingWorkoutRepository: PendingWorkoutRepository,
) {
    suspend operator fun invoke(): SyncOutcome {
        val userId = authRepository.currentUser()?.id ?: return SyncOutcome.NoUser
        val pending = pendingWorkoutRepository.getPending(userId)
        var shouldRetry = false
        for (workout in pending) {
            when (val result = pendingWorkoutRepository.upload(workout)) {
                is AppResult.Success -> pendingWorkoutRepository.delete(workout.id)
                is AppResult.Failure -> if (isTransient(result.error)) {
                    pendingWorkoutRepository.recordAttempt(workout.id, result.error.storageCode())
                    shouldRetry = true
                } else {
                    pendingWorkoutRepository.markFailed(workout.id, result.error.storageCode())
                }
            }
        }
        return if (shouldRetry) SyncOutcome.RetryLater else SyncOutcome.Done
    }

    /** Network/auth hiccups and unidentified-or-5xx server errors are worth retrying; everything else (a genuine data conflict, a permission error with a known code, a decode failure...) will never succeed on retry alone. */
    private fun isTransient(error: AppError): Boolean = when (error) {
        AppError.Network, AppError.Unauthorized -> true
        is AppError.Server -> error.code == null || error.code.startsWith("5")
        else -> false
    }

    /** A short, stable, PII-free code stored in [PendingWorkout.lastError] - never the raw exception message (see [AppError.Unknown]'s KDoc). */
    private fun AppError.storageCode(): String = when (this) {
        AppError.Network -> "network"
        AppError.Unauthorized -> "unauthorized"
        AppError.NotFound -> "not_found"
        is AppError.Conflict -> "conflict"
        is AppError.Validation -> "validation:${reason.name}"
        is AppError.Server -> "server:${code ?: "unknown"}"
        is AppError.Unknown -> "unknown"
    }
}

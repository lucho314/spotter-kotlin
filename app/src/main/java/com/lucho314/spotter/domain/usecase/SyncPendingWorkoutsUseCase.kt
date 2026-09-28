package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
        val userId = when (val state = resolveNonLoadingAuthState()) {
            // A real, resolved sign-out: nothing to sync, and retrying wouldn't help.
            AuthState.SignedOut -> return SyncOutcome.NoUser
            // Cold process (Initializing) that never resolved within the timeout, or offline with
            // an expired token and no cached user (RefreshFailure -> Loading, see AuthStateMapper):
            // both are transient, not "no user" - the worker must retry instead of giving up via
            // Result.success(), or a queue that filled up while the app was dead never uploads once
            // the network returns (bug: RootViewModel's per-session dedup wouldn't reschedule it).
            AuthState.Loading -> return SyncOutcome.RetryLater
            is AuthState.SignedIn -> state.user.id
        }
        val pending = pendingWorkoutRepository.getPending(userId)
        var shouldRetry = false
        for (workout in pending) {
            when (val result = pendingWorkoutRepository.upload(workout)) {
                is AppResult.Success -> pendingWorkoutRepository.delete(workout.id)
                is AppResult.Failure -> if (isTransient(result.error, workout.attempts)) {
                    pendingWorkoutRepository.recordAttempt(workout.id, result.error.storageCode())
                    shouldRetry = true
                } else {
                    // Best-effort: if the session itself made it to the server but its sets then
                    // failed permanently, don't leave an orphan, set-less session behind. A no-op
                    // if the session was never created either.
                    pendingWorkoutRepository.deleteRemoteSession(workout.id)
                    pendingWorkoutRepository.markFailed(workout.id, result.error.storageCode())
                }
            }
        }
        return if (shouldRetry) SyncOutcome.RetryLater else SyncOutcome.Done
    }

    /**
     * Waits (up to [AUTH_STATE_TIMEOUT_MS]) for [AuthRepository.authState] to settle on a resolved
     * value. A timeout is reported the same as [AuthState.Loading] - the caller retries later
     * instead of treating it as "no user".
     */
    private suspend fun resolveNonLoadingAuthState(): AuthState =
        withTimeoutOrNull(AUTH_STATE_TIMEOUT_MS) {
            authRepository.authState.first { it !is AuthState.Loading }
        } ?: AuthState.Loading

    /**
     * Network errors and unidentified-or-5xx server errors are always worth retrying. `Unauthorized`
     * and `Unknown` are ambiguous (could be a genuinely expired/invalid session, or a bug that will
     * never succeed) so they're retried only up to [MAX_AMBIGUOUS_ATTEMPTS] times before giving up
     * (`FAILED`) - otherwise a permanently broken row would retry forever. Everything else (a
     * genuine data conflict, a permission error with a known code, a decode failure...) will never
     * succeed on retry alone.
     */
    private fun isTransient(error: AppError, attemptsSoFar: Int): Boolean = when (error) {
        AppError.Network -> true
        AppError.Unauthorized -> attemptsSoFar < MAX_AMBIGUOUS_ATTEMPTS
        is AppError.Server -> error.code == null || error.code.startsWith("5")
        is AppError.Unknown -> attemptsSoFar < MAX_AMBIGUOUS_ATTEMPTS
        else -> false
    }

    private companion object {
        const val AUTH_STATE_TIMEOUT_MS = 5_000L
        const val MAX_AMBIGUOUS_ATTEMPTS = 5
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

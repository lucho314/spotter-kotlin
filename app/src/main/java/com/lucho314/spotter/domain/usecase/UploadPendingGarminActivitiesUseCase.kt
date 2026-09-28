package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.calc.GarminActivityPlanner
import com.lucho314.spotter.domain.calc.GarminExerciseMapper
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminExerciseRef
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminSetSnapshot
import com.lucho314.spotter.domain.model.GarminUploadOutcome
import com.lucho314.spotter.domain.model.GarminUploadTask
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.ExerciseRepository
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.GarminActivityRepository
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GarminSyncOutcome {
    data object Done : GarminSyncOutcome
    data object RetryLater : GarminSyncOutcome
    data object NoUser : GarminSyncOutcome
    data object NotConnected : GarminSyncOutcome
    data object NeedsReconnect : GarminSyncOutcome
}

/**
 * Uploads every `PENDING` Garmin row for the current user, run by [com.lucho314.spotter.core.work.GarminUploadWorker].
 * Mirrors [SyncPendingWorkoutsUseCase]'s auth-state/retry shape, but entirely independent of it -
 * a Garmin failure here must never touch `pending_workout` or reschedule `sync_workouts`.
 */
class UploadPendingGarminActivitiesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val garminAccountRepository: GarminAccountRepository,
    private val garminUploadRepository: GarminUploadRepository,
    private val garminActivityRepository: GarminActivityRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val exerciseRepository: ExerciseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): GarminSyncOutcome {
        val userId = when (val state = resolveNonLoadingAuthState()) {
            AuthState.SignedOut -> return GarminSyncOutcome.NoUser
            AuthState.Loading -> return GarminSyncOutcome.RetryLater
            is AuthState.SignedIn -> state.user.id
        }

        when (val connection = garminAccountRepository.getConnection(userId)) {
            GarminConnectionState.NotConnected -> return GarminSyncOutcome.NotConnected
            is GarminConnectionState.Connected -> if (connection.needsReconnect) return GarminSyncOutcome.NeedsReconnect
        }

        var shouldRetry = false
        for (task in garminUploadRepository.getPending(userId)) {
            if (task.snapshotCorrupt) {
                garminUploadRepository.markFailed(task.workoutId, "decode")
                continue
            }

            val snapshot = when (val snapshot = task.snapshot) {
                null -> when (val resolved = resolveFromHistory(task)) {
                    is HistoryResolution.Found -> resolved.snapshot
                    is HistoryResolution.Failed -> {
                        garminUploadRepository.markFailed(task.workoutId, resolved.code)
                        continue
                    }
                    HistoryResolution.Retryable -> {
                        garminUploadRepository.recordAttempt(task.workoutId, "history_network")
                        shouldRetry = true
                        continue
                    }
                }
                else -> snapshot
            }

            val refs = buildExerciseRefs(snapshot)
            val offset = timeProvider.zone().rules.getOffset(snapshot.completedAt).totalSeconds
            val plan = GarminActivityPlanner.plan(snapshot, refs, offset)
            if (plan == null) {
                garminUploadRepository.markFailed(task.workoutId, "empty")
                continue
            }

            when (val result = garminActivityRepository.upload(userId, plan)) {
                is GarminResult.Success -> when (val outcome = result.value) {
                    is GarminUploadOutcome.Uploaded -> garminUploadRepository.markUploaded(task.workoutId, outcome.activityId, outcome.uploadId)
                    is GarminUploadOutcome.AlreadyExists -> garminUploadRepository.markUploaded(task.workoutId, outcome.activityId, null)
                }
                is GarminResult.Failure -> when (val outcome = handleFailure(task, result.error)) {
                    FailureOutcome.CONTINUE -> Unit
                    FailureOutcome.RETRY_LATER -> shouldRetry = true
                    FailureOutcome.STOP_RATE_LIMITED -> return GarminSyncOutcome.RetryLater
                    FailureOutcome.STOP_NEEDS_RECONNECT -> return GarminSyncOutcome.NeedsReconnect
                    FailureOutcome.STOP_NOT_CONNECTED -> return GarminSyncOutcome.NotConnected
                }
            }
        }
        return if (shouldRetry) GarminSyncOutcome.RetryLater else GarminSyncOutcome.Done
    }

    private enum class FailureOutcome { CONTINUE, RETRY_LATER, STOP_RATE_LIMITED, STOP_NEEDS_RECONNECT, STOP_NOT_CONNECTED }

    /** Records/marks [task]'s row (except for the two "stop the whole loop" cases, which leave it untouched) and reports what the caller should do next. */
    private suspend fun handleFailure(task: GarminUploadTask, error: GarminError): FailureOutcome = when (error) {
        GarminError.ReauthRequired -> FailureOutcome.STOP_NEEDS_RECONNECT
        GarminError.NotConnected -> FailureOutcome.STOP_NOT_CONNECTED
        GarminError.RateLimited -> {
            garminUploadRepository.recordAttempt(task.workoutId, error.storageCode())
            FailureOutcome.STOP_RATE_LIMITED
        }
        is GarminError.InvalidFile -> {
            garminUploadRepository.markFailed(task.workoutId, error.storageCode())
            FailureOutcome.CONTINUE
        }
        GarminError.Network, is GarminError.Server -> retryOrFail(task, error, MAX_TRANSIENT_ATTEMPTS)
        is GarminError.ServiceChanged, is GarminError.Unknown -> retryOrFail(task, error, MAX_AMBIGUOUS_ATTEMPTS)
        // Login-only errors never come back from an upload call; treat defensively as ambiguous.
        GarminError.InvalidCredentials, GarminError.InvalidMfaCode, GarminError.MfaSessionExpired, GarminError.CaptchaRequired, GarminError.Blocked ->
            retryOrFail(task, error, MAX_AMBIGUOUS_ATTEMPTS)
    }

    private suspend fun retryOrFail(task: GarminUploadTask, error: GarminError, maxAttempts: Int): FailureOutcome {
        val code = error.storageCode()
        return if (task.attempts + 1 >= maxAttempts) {
            garminUploadRepository.markFailed(task.workoutId, code)
            FailureOutcome.CONTINUE
        } else {
            garminUploadRepository.recordAttempt(task.workoutId, code)
            FailureOutcome.RETRY_LATER
        }
    }

    private sealed interface HistoryResolution {
        data class Found(val snapshot: GarminActivitySnapshot) : HistoryResolution
        data class Failed(val code: String) : HistoryResolution
        data object Retryable : HistoryResolution
    }

    private suspend fun resolveFromHistory(task: GarminUploadTask): HistoryResolution =
        when (val result = workoutHistoryRepository.getSession(task.workoutId)) {
            is AppResult.Success -> HistoryResolution.Found(buildSnapshotFromHistory(task, result.value))
            is AppResult.Failure -> when (result.error) {
                AppError.NotFound -> HistoryResolution.Failed("not_found")
                AppError.Network, is AppError.Server -> HistoryResolution.Retryable
                else -> HistoryResolution.Failed("history_error")
            }
        }

    private suspend fun buildSnapshotFromHistory(task: GarminUploadTask, detail: WorkoutSessionDetail): GarminActivitySnapshot {
        val exerciseOrder = LinkedHashMap<Int, Int>()
        detail.sets.forEach { set -> exerciseOrder.getOrPut(set.exerciseId) { exerciseOrder.size } }
        val weightUnit = preferencesRepository.weightUnit.first()
        val completedAt = detail.completedAt ?: detail.sets.maxOfOrNull { it.completedAt } ?: detail.startedAt
        val sets = detail.sets.map { set ->
            GarminSetSnapshot(
                exerciseId = set.exerciseId,
                exerciseName = set.exerciseName,
                equipment = null,
                exerciseOrder = exerciseOrder.getValue(set.exerciseId),
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                isWarmup = set.isWarmup,
                completedAt = set.completedAt,
            )
        }
        return GarminActivitySnapshot(
            workoutId = task.workoutId,
            userId = task.userId,
            startedAt = detail.startedAt,
            completedAt = completedAt,
            weightUnit = weightUnit,
            sets = sets,
        )
    }

    private suspend fun buildExerciseRefs(snapshot: GarminActivitySnapshot): Map<Int, GarminExerciseRef?> =
        snapshot.sets.groupBy { it.exerciseId }.mapValues { (exerciseId, sets) ->
            val sample = sets.first()
            val exercise = (exerciseRepository.getExercise(exerciseId) as? AppResult.Success)?.value
            GarminExerciseMapper.map(nameEn = exercise?.nameEn, name = sample.exerciseName, equipment = sample.equipment ?: exercise?.equipment)
        }

    private suspend fun resolveNonLoadingAuthState(): AuthState =
        withTimeoutOrNull(AUTH_STATE_TIMEOUT_MS) {
            authRepository.authState.first { it !is AuthState.Loading }
        } ?: AuthState.Loading

    private fun GarminError.storageCode(): String = when (this) {
        GarminError.Network -> "network"
        GarminError.RateLimited -> "rate_limited"
        is GarminError.Server -> "server:${httpStatus ?: "unknown"}"
        is GarminError.InvalidFile -> "invalid_file:$httpStatus"
        is GarminError.ServiceChanged -> "service_changed:$code"
        GarminError.ReauthRequired -> "reauth_required"
        GarminError.NotConnected -> "not_connected"
        else -> "unknown"
    }

    private companion object {
        const val AUTH_STATE_TIMEOUT_MS = 5_000L
        const val MAX_TRANSIENT_ATTEMPTS = 10
        const val MAX_AMBIGUOUS_ATTEMPTS = 3
    }
}

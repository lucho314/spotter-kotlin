package com.lucho314.spotter.domain.model

import java.time.Instant

/**
 * Garmin-specific error type (mirrors [com.lucho314.spotter.core.common.AppError]'s shape).
 * Kept separate from `AppError` on purpose: adding Garmin variants there would break the existing
 * exhaustive `when`s (`toMessageRes`, `storageCode`, `isTransient`).
 *
 * Security rule, same as `ErrorMapper`: never build a message/code from a response body, URL,
 * ticket (`ST-...`), token, email or password. [ServiceChanged.code] and the storage codes derived
 * from this type are fixed, PII-free strings.
 */
sealed interface GarminError {
    data object Network : GarminError
    data object InvalidCredentials : GarminError
    data object InvalidMfaCode : GarminError
    data object MfaSessionExpired : GarminError
    data object CaptchaRequired : GarminError
    data object Blocked : GarminError
    data object RateLimited : GarminError
    data object ReauthRequired : GarminError
    data object NotConnected : GarminError
    data class InvalidFile(val httpStatus: Int) : GarminError
    data class Server(val httpStatus: Int?) : GarminError
    data class ServiceChanged(val code: String) : GarminError
    /** @param cause kept for debug logging only; never surfaced to the user or release logs. */
    data class Unknown(val cause: Throwable? = null) : GarminError
}

sealed interface GarminResult<out T> {
    data class Success<T>(val value: T) : GarminResult<T>
    data class Failure(val error: GarminError) : GarminResult<Nothing>
}

sealed interface GarminConnectionState {
    data object NotConnected : GarminConnectionState
    data class Connected(val displayName: String?, val autoUpload: Boolean, val needsReconnect: Boolean) : GarminConnectionState
}

sealed interface GarminLoginResult {
    data object Connected : GarminLoginResult

    /** [method]: "email" | "sms" | "totp"/other | null (hint for the UI copy). */
    data class MfaRequired(val challengeId: String, val method: String?) : GarminLoginResult
}

enum class GarminUploadStatus { PENDING, UPLOADED, FAILED }
enum class GarminEnqueueResult { ENQUEUED, ALREADY_PENDING, ALREADY_UPLOADED }

/**
 * Self-contained snapshot of a workout for Garmin (independent from the Supabase outbox, which is
 * deleted once synced).
 */
data class GarminActivitySnapshot(
    val workoutId: String,
    val userId: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val weightUnit: WeightUnit,
    val sets: List<GarminSetSnapshot>,
)

data class GarminSetSnapshot(
    val exerciseId: Int,
    val exerciseName: String?,
    val equipment: Equipment?,
    /** Exercise position; used as a tiebreaker for ordering. */
    val exerciseOrder: Int,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val completedAt: Instant,
)

data class GarminUploadRequest(val workoutId: String, val userId: String, val snapshot: GarminActivitySnapshot?)

data class GarminUploadTask(
    val workoutId: String,
    val userId: String,
    val attempts: Int,
    val snapshot: GarminActivitySnapshot?,
    val snapshotCorrupt: Boolean,
)

data class GarminExerciseRef(val category: Int, val subtype: Int?)

enum class GarminSetKind { ACTIVE, REST }

data class GarminPlannedSet(
    val kind: GarminSetKind,
    val startTime: Instant,
    val endTime: Instant,
    /** null = invalid in FIT. */
    val repetitions: Int?,
    /** null = invalid in FIT. */
    val weightKg: Double?,
    val exercise: GarminExerciseRef?,
    /** null for REST sets. */
    val weightUnit: WeightUnit?,
)

data class GarminActivityPlan(
    val workoutId: String,
    val startTime: Instant,
    /** >= startTime + 1 s. */
    val endTime: Instant,
    /** For `activity.local_timestamp`. */
    val utcOffsetSeconds: Int,
    val sets: List<GarminPlannedSet>,
)

sealed interface GarminUploadOutcome {
    data class Uploaded(val activityId: Long?, val uploadId: Long?) : GarminUploadOutcome
    data class AlreadyExists(val activityId: Long?) : GarminUploadOutcome
}

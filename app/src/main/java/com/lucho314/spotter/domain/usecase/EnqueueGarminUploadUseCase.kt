package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.work.GarminUploadScheduler
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminEnqueueResult
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminSetSnapshot
import com.lucho314.spotter.domain.model.GarminUploadRequest
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EnqueueGarminUpload"

/**
 * Queues a finished workout for Garmin upload. Both entry points only ever run *after* the outbox
 * write and its own [com.lucho314.spotter.core.work.SyncScheduler.schedule] have already succeeded
 * - see [FinishWorkoutUseCase]'s KDoc for why Garmin must never be allowed to affect `FinishResult`.
 */
class EnqueueGarminUploadUseCase @Inject constructor(
    private val garminAccountRepository: GarminAccountRepository,
    private val garminUploadRepository: GarminUploadRepository,
    private val garminUploadScheduler: GarminUploadScheduler,
    private val logger: Logger,
) {
    /** Automatic path, called right after finishing a workout. Never throws (except [CancellationException]): a Garmin failure here must never affect [FinishResult]. */
    suspend fun afterFinish(workout: ActiveWorkout, pending: PendingWorkout) {
        try {
            val connection = garminAccountRepository.getConnection(workout.userId)
            if (connection !is GarminConnectionState.Connected || !connection.autoUpload) return
            val request = GarminUploadRequest(pending.id, workout.userId, buildSnapshot(workout, pending))
            garminUploadRepository.enqueue(request, requeueFailed = false)
            garminUploadScheduler.schedule()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.w(TAG, "garmin enqueue failed: ${e::class.simpleName}")
        }
    }

    /** Manual path (history "Subir a Garmin"): [snapshot][GarminUploadRequest.snapshot] is null - the worker looks the session up itself. */
    suspend fun fromHistory(userId: String, sessionId: String): GarminResult<GarminEnqueueResult> {
        val connection = garminAccountRepository.getConnection(userId)
        if (connection !is GarminConnectionState.Connected) return GarminResult.Failure(GarminError.NotConnected)
        return try {
            val result = garminUploadRepository.enqueue(GarminUploadRequest(sessionId, userId, null), requeueFailed = true)
            if (result == GarminEnqueueResult.ENQUEUED || result == GarminEnqueueResult.ALREADY_PENDING) {
                garminUploadScheduler.schedule()
            }
            GarminResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GarminResult.Failure(GarminError.Unknown(e))
        }
    }

    private fun buildSnapshot(workout: ActiveWorkout, pending: PendingWorkout): GarminActivitySnapshot {
        val exercisesById = workout.exercises.associateBy { it.exerciseId }
        val sets = pending.sets.map { set ->
            val exercise = exercisesById[set.exerciseId]
            GarminSetSnapshot(
                exerciseId = set.exerciseId,
                exerciseName = exercise?.name,
                equipment = exercise?.equipment,
                exerciseOrder = exercise?.position ?: Int.MAX_VALUE,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps,
                isWarmup = set.isWarmup,
                completedAt = set.completedAt,
            )
        }
        return GarminActivitySnapshot(
            workoutId = pending.id,
            userId = workout.userId,
            startedAt = workout.startedAt,
            completedAt = pending.completedAt,
            weightUnit = workout.weightUnit,
            sets = sets,
        )
    }
}

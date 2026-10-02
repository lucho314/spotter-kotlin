package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.mapper.toNoteInsertDto
import com.lucho314.spotter.data.mapper.toSessionInsertDto
import com.lucho314.spotter.data.mapper.toSetInsertDto
import com.lucho314.spotter.data.remote.datasource.WorkoutRemoteDataSource
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A Postgres foreign-key violation (23503) on `workout_sessions.routine_id` means the routine was
 * deleted (or, for a shared/imported one, transferred away) after this workout was completed but
 * before it synced - the workout itself is still perfectly valid, just no longer linked to a
 * routine. Extracted as a pure predicate (rather than inlined into a `when` over the raw
 * exception, which would need a real `PostgrestRestException` - hard to construct in a unit test,
 * see [com.lucho314.spotter.core.network.ErrorMapper]'s KDoc) so the decision itself is directly
 * testable.
 */
internal fun isRoutineForeignKeyViolation(error: AppError): Boolean = error is AppError.Server && error.code == "23503"

/**
 * Exercise notes are uploaded only after the session and its sets already made it, so failing them
 * must never fail the workout permanently: `SyncPendingWorkoutsUseCase` reacts to a permanent
 * failure by deleting the remote session. Only errors that a later retry can fix (network, 5xx or
 * an unidentified server error) are reported; anything else (e.g. `workout_exercise_notes` not yet
 * deployed, or a rejected row) drops the notes and lets the workout count as synced.
 */
internal fun isRetryableNoteUploadError(error: AppError): Boolean =
    error == AppError.Network || (error is AppError.Server && (error.code == null || error.code.startsWith("5")))

/** The offline outbox (ADR A3-b): [upload] is idempotent, safe to retry after a partial failure. */
@Singleton
class PendingWorkoutRepositoryImpl @Inject constructor(
    private val dao: PendingWorkoutDao,
    private val remote: WorkoutRemoteDataSource,
) : PendingWorkoutRepository {

    override fun observeCount(userId: String): Flow<Int> = dao.observeCount(userId)

    override fun observeFailed(userId: String): Flow<List<PendingWorkout>> =
        dao.observeFailed(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPending(userId: String): List<PendingWorkout> =
        dao.getPending(userId).map { it.toDomain() }

    override suspend fun upload(workout: PendingWorkout): AppResult<Unit> {
        var result = uploadOnce(workout)
        if (result is AppResult.Failure && workout.routineId != null && isRoutineForeignKeyViolation(result.error)) {
            // Retry once, unlinked, instead of permanently failing a perfectly real workout over a
            // stale foreign key (observation from the FASE 4 review).
            result = uploadOnce(workout.copy(routineId = null))
        }
        if (result is AppResult.Failure) return result
        return uploadExerciseNotes(workout)
    }

    private suspend fun uploadExerciseNotes(workout: PendingWorkout): AppResult<Unit> {
        if (workout.exerciseNotes.isEmpty()) return AppResult.Success(Unit)
        val result = safeCall { remote.uploadExerciseNotes(workout.exerciseNotes.map { it.toNoteInsertDto(workout.id) }) }
        return if (result is AppResult.Failure && isRetryableNoteUploadError(result.error)) result else AppResult.Success(Unit)
    }

    private suspend fun uploadOnce(workout: PendingWorkout): AppResult<Unit> = safeCall {
        remote.uploadSession(workout.toSessionInsertDto())
        remote.uploadSets(workout.sets.map { it.toSetInsertDto(workout.id) })
    }

    override suspend fun deleteRemoteSession(id: String) {
        try {
            remote.deleteSession(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Best-effort cleanup only: an orphan, set-less server session is a cosmetic leftover,
            // not worth failing the sync flow (or masking the real error) over.
        }
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun markFailed(id: String, error: String) {
        dao.markFailed(id, error)
    }

    override suspend fun resetToPending(id: String) {
        dao.resetToPending(id)
    }

    override suspend fun recordAttempt(id: String, error: String) {
        dao.recordAttempt(id, error)
    }
}

package com.lucho314.spotter.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.mapper.toEntity
import com.lucho314.spotter.data.mapper.toSessionEntity
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.ActiveWorkoutStartOutcome
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room is the single source of truth for the in-progress workout (ADR A3-a): it survives process
 * death, unlike the RN app's in-memory Zustand store.
 */
@Singleton
class ActiveWorkoutRepositoryImpl @Inject constructor(
    private val database: SpotterDatabase,
    private val activeWorkoutDao: ActiveWorkoutDao,
    private val pendingWorkoutDao: PendingWorkoutDao,
) : ActiveWorkoutRepository {

    override fun observeActive(userId: String): Flow<ActiveWorkout?> =
        activeWorkoutDao.observeByUser(userId).map { it?.toDomain() }

    override suspend fun getActive(userId: String): ActiveWorkout? =
        activeWorkoutDao.getByUser(userId)?.toDomain()

    override suspend fun start(workout: ActiveWorkout): AppResult<ActiveWorkoutStartOutcome> = safeCall {
        val (session, exercisesWithSets) = workout.toEntities()
        // `startIfAbsent` checks-and-inserts atomically, so this alone already closes the race
        // between two concurrent start() calls (see its KDoc). The SQLiteConstraintException catch
        // below is only a safety net in case that invariant is ever violated some other way (e.g. a
        // future direct `insertSession` caller) - it must never surface as an opaque crash.
        //
        // If the constraint actually fired, some row must be there; re-querying it returning null
        // would mean the constraint was violated by something *other* than the "already active"
        // case this net exists for (e.g. a stale index) - since the transaction rolled back nothing
        // was inserted either. Silently returning Started then would be wrong (nothing was actually
        // started), so this rethrows and lets `safeCall` turn it into a `Failure` instead.
        val existingSessionId = try {
            activeWorkoutDao.startIfAbsent(workout.userId, session, exercisesWithSets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteConstraintException) {
            activeWorkoutDao.getByUser(workout.userId)?.session?.id ?: throw e
        }
        if (existingSessionId != null) {
            ActiveWorkoutStartOutcome.AlreadyActive(existingSessionId)
        } else {
            ActiveWorkoutStartOutcome.Started
        }
    }

    override suspend fun replace(existingSessionId: String, workout: ActiveWorkout): AppResult<Unit> = safeCall {
        val (session, exercisesWithSets) = workout.toEntities()
        activeWorkoutDao.replaceActive(existingSessionId, session, exercisesWithSets)
    }

    private fun ActiveWorkout.toEntities(): Pair<ActiveSessionEntity, List<Pair<ActiveExerciseEntity, List<ActiveSetEntity>>>> {
        val exercisesWithSets = exercises.map { exercise ->
            // `activeExerciseId = 0` is a placeholder: `insertFull` overwrites it with the id Room
            // autogenerates for `exercise` right before inserting these sets.
            exercise.toEntity(sessionId) to exercise.sets.map { it.toEntity(activeExerciseId = 0) }
        }
        return toSessionEntity() to exercisesWithSets
    }

    override suspend fun updateSetInputs(setId: String, weightText: String, repsText: String) {
        activeWorkoutDao.updateSetInputs(setId, weightText, repsText)
    }

    override suspend fun setCompleted(setId: String, completedAt: Instant?) {
        activeWorkoutDao.setCompleted(setId, completedAt?.toEpochMilli())
    }

    override suspend fun updateExerciseNote(exerciseRowId: Long, note: String?) {
        activeWorkoutDao.updateExerciseNote(exerciseRowId, note)
    }

    override suspend fun addSet(exerciseRowId: Long, setId: String, weightText: String, repsText: String) {
        // Atomic (computes the next set_number and inserts in the same transaction): see
        // ActiveWorkoutDao.insertNextSet's KDoc for why a two-step read-then-write isn't safe here.
        activeWorkoutDao.insertNextSet(setId, exerciseRowId, weightText, repsText)
    }

    override suspend fun setCurrentExercise(sessionId: String, index: Int) {
        activeWorkoutDao.setCurrentExercise(sessionId, index)
    }

    override suspend fun setRestTimer(sessionId: String, rest: RestTimer?) {
        activeWorkoutDao.setRestTimer(sessionId, rest?.endsAt?.toEpochMilli(), rest?.totalSeconds)
    }

    override suspend fun clearRestTimerIfMatches(sessionId: String, expectedEndsAt: Instant): Boolean =
        activeWorkoutDao.clearRestTimerIfMatches(sessionId, expectedEndsAt.toEpochMilli()) > 0

    override suspend fun discard(sessionId: String) {
        activeWorkoutDao.deleteSession(sessionId)
    }

    override suspend fun moveToOutbox(sessionId: String, pending: PendingWorkout) {
        database.withTransaction {
            pendingWorkoutDao.insertFull(
                pending.toEntity(),
                pending.sets.map { it.toEntity(pending.id) },
                pending.exerciseNotes.map { it.toEntity(pending.id) },
            )
            activeWorkoutDao.deleteSession(sessionId)
        }
    }
}

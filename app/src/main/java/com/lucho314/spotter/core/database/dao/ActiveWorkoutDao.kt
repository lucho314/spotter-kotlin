package com.lucho314.spotter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionWithExercises
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveWorkoutDao {

    // `user_id` is a unique index (at most one row), but the ordering is still explicit so this
    // stays deterministic even if that constraint were ever relaxed.
    @Transaction
    @Query("SELECT * FROM active_session WHERE user_id = :userId ORDER BY started_at_epoch_ms DESC LIMIT 1")
    fun observeByUser(userId: String): Flow<ActiveSessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM active_session WHERE user_id = :userId ORDER BY started_at_epoch_ms DESC LIMIT 1")
    suspend fun getByUser(userId: String): ActiveSessionWithExercises?

    // ABORT (not REPLACE): a `user_id` unique-index collision must fail loudly, never silently
    // delete the previous row - REPLACE cascades to that row's active_exercise/active_set children,
    // which is exactly the "starting a session wipes the in-progress one" regression this guards
    // against. See ActiveSessionEntity's KDoc.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: ActiveSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(exercise: ActiveExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(set: ActiveSetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSets(sets: List<ActiveSetEntity>)

    @Query("SELECT COALESCE(MAX(set_number), 0) + 1 FROM active_set WHERE active_exercise_id = :activeExerciseId")
    suspend fun nextSetNumber(activeExerciseId: Long): Int

    /**
     * Computes the next `set_number` and inserts the new set in one DB transaction: two
     * back-to-back double taps on "Agregar serie" each call this, and without the transaction both
     * could read the same "current max" before either had inserted, producing two sets with the
     * same `set_number`.
     */
    @Transaction
    suspend fun insertNextSet(id: String, activeExerciseId: Long, weightText: String, repsText: String): Int {
        val setNumber = nextSetNumber(activeExerciseId)
        insertSet(
            ActiveSetEntity(
                id = id,
                activeExerciseId = activeExerciseId,
                setNumber = setNumber,
                weightText = weightText,
                repsText = repsText,
                isWarmup = false,
                completedAtEpochMs = null,
            ),
        )
        return setNumber
    }

    @Query("UPDATE active_set SET weight_text = :weightText, reps_text = :repsText WHERE id = :setId")
    suspend fun updateSetInputs(setId: String, weightText: String, repsText: String)

    @Query("UPDATE active_set SET completed_at_epoch_ms = :completedAtEpochMs WHERE id = :setId")
    suspend fun setCompleted(setId: String, completedAtEpochMs: Long?)

    @Query("UPDATE active_session SET current_exercise_index = :index WHERE id = :sessionId")
    suspend fun setCurrentExercise(sessionId: String, index: Int)

    @Query(
        "UPDATE active_session SET rest_ends_at_epoch_ms = :endsAtEpochMs, rest_total_seconds = :totalSeconds " +
            "WHERE id = :sessionId",
    )
    suspend fun setRestTimer(sessionId: String, endsAtEpochMs: Long?, totalSeconds: Int?)

    @Query("DELETE FROM active_session WHERE id = :id")
    suspend fun deleteSession(id: String)

    /**
     * Inserts a whole session snapshot: the session row, its exercises, and each exercise's sets.
     * Aborts (throwing) without inserting anything if `session.userId` already has an active
     * session - callers that want "start fresh only if none exists" should use [startIfAbsent]
     * instead, which checks and inserts atomically; callers that want to discard an existing one
     * first should use [replaceActive].
     */
    @Transaction
    suspend fun insertFull(session: ActiveSessionEntity, exercisesWithSets: List<Pair<ActiveExerciseEntity, List<ActiveSetEntity>>>) {
        insertSession(session)
        exercisesWithSets.forEach { (exercise, sets) ->
            val exerciseId = insertExercise(exercise)
            insertSets(sets.map { it.copy(activeExerciseId = exerciseId) })
        }
    }

    /**
     * Starts [session] for [userId] only if they have no active session yet, atomically: the
     * "is there one already" check and the insert happen in the same DB transaction, so two
     * concurrent calls can't both see "none" and both insert (Room serializes writer transactions
     * on a single connection, so the second call's [getByUser] here only runs after the first's
     * transaction has committed or rolled back).
     *
     * @return `null` if [session] was inserted, or the existing active session's id if one was
     * already there (in which case nothing is inserted).
     */
    @Transaction
    suspend fun startIfAbsent(
        userId: String,
        session: ActiveSessionEntity,
        exercisesWithSets: List<Pair<ActiveExerciseEntity, List<ActiveSetEntity>>>,
    ): String? {
        val existing = getByUser(userId)
        if (existing != null) return existing.session.id
        insertFull(session, exercisesWithSets)
        return null
    }

    @Query("SELECT user_id FROM active_session WHERE id = :id")
    suspend fun getUserIdForSession(id: String): String?

    /**
     * Atomically discards [existingSessionId] (cascading to its exercises/sets) and starts
     * [session] instead - but only if [existingSessionId] actually belongs to `session.userId`
     * (review carry-over, FASE 2 approval: this used to delete-then-insert unconditionally,
     * trusting the caller to have passed a same-user id). `StartWorkoutUseCase` only ever passes
     * the id [com.lucho314.spotter.domain.model.ActiveWorkoutStartOutcome.AlreadyActive] reported
     * for that same user, so this should never actually fire in practice - it exists as a safety
     * net, the same way [insertFull]'s unique-index `ABORT` is (see [startIfAbsent]'s KDoc),
     * against a future caller passing a stale or wrong id.
     *
     * If [existingSessionId] no longer exists at all (e.g. a concurrent discard already removed
     * it), [getUserIdForSession] returns `null` and this proceeds anyway: there is nothing left to
     * protect, [deleteSession] on a missing id is a harmless no-op, and refusing to start the new
     * session here would strand the caller with neither the old nor the new one active.
     */
    @Transaction
    suspend fun replaceActive(
        existingSessionId: String,
        session: ActiveSessionEntity,
        exercisesWithSets: List<Pair<ActiveExerciseEntity, List<ActiveSetEntity>>>,
    ) {
        val existingUserId = getUserIdForSession(existingSessionId)
        check(existingUserId == null || existingUserId == session.userId) {
            "replaceActive: session $existingSessionId belongs to a different user than ${session.userId}"
        }
        deleteSession(existingSessionId)
        insertFull(session, exercisesWithSets)
    }
}

package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IdGenerator
import com.lucho314.spotter.core.common.notNullOrNotFound
import com.lucho314.spotter.core.common.requirePositiveOrNotFound
import com.lucho314.spotter.core.network.safeCall
import com.lucho314.spotter.data.mapper.toDetail
import com.lucho314.spotter.data.mapper.toDomain
import com.lucho314.spotter.data.mapper.toInstant
import com.lucho314.spotter.data.mapper.toSummary
import com.lucho314.spotter.data.mapper.toTimestampString
import com.lucho314.spotter.data.remote.datasource.WorkoutRemoteDataSource
import com.lucho314.spotter.data.remote.dto.WorkoutExerciseNoteInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetInsertDto
import com.lucho314.spotter.domain.calc.ExerciseNote
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Network-only, no local cache (ADR A3): history is read-mostly and needs an error state when offline. */
@Singleton
class WorkoutHistoryRepositoryImpl @Inject constructor(
    private val remote: WorkoutRemoteDataSource,
    private val idGenerator: IdGenerator,
) : WorkoutHistoryRepository {

    override suspend fun getSessions(userId: String, offset: Int, limit: Int): AppResult<List<WorkoutSessionSummary>> = safeCall {
        val from = offset.toLong()
        val to = from + limit - 1
        remote.getSessions(userId, from, to).map { it.toSummary() }
    }

    override suspend fun getSession(sessionId: String): AppResult<WorkoutSessionDetail> =
        when (val result = safeCall { remote.getSession(sessionId)?.toDetail() }.notNullOrNotFound()) {
            is AppResult.Success -> AppResult.Success(result.value.copy(exerciseNotes = getSessionExerciseNotesOrEmpty(sessionId)))
            is AppResult.Failure -> result
        }

    /** Best-effort, like [getExerciseNoteOrNull]: the session's sets are shown even if this fails. */
    private suspend fun getSessionExerciseNotesOrEmpty(sessionId: String): Map<Int, String> =
        when (val result = safeCall { remote.getSessionExerciseNotes(sessionId) }) {
            is AppResult.Success -> result.value.mapNotNull { row -> ExerciseNote.normalize(row.note)?.let { row.exerciseId to it } }.toMap()
            is AppResult.Failure -> emptyMap()
        }

    override suspend fun setExerciseNote(sessionId: String, exerciseId: Int, note: String?): AppResult<Unit> = safeCall {
        val normalized = ExerciseNote.normalize(note)
        if (normalized == null) {
            remote.deleteExerciseNote(sessionId, exerciseId)
        } else {
            remote.saveExerciseNote(WorkoutExerciseNoteInsertDto(sessionId = sessionId, exerciseId = exerciseId, note = normalized))
        }
    }

    override suspend fun updateSet(setId: String, weightKg: Double, reps: Int): AppResult<Unit> =
        safeCall { remote.updateSet(setId, weightKg, reps) }.requirePositiveOrNotFound()

    override suspend fun addSet(sessionId: String, exerciseId: Int, setNumber: Int, weightKg: Double, reps: Int, completedAt: Instant): AppResult<Unit> = safeCall {
        remote.insertSet(
            WorkoutSetInsertDto(
                id = idGenerator.uuid(),
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = weightKg,
                reps = reps,
                isWarmup = false,
                completedAt = completedAt.toTimestampString(),
            ),
        )
    }

    override suspend fun deleteSet(setId: String): AppResult<Unit> =
        safeCall { remote.deleteSet(setId) }.requirePositiveOrNotFound()

    override suspend fun deleteSession(sessionId: String): AppResult<Unit> =
        when (val result = safeCall { remote.deleteSession(sessionId) }) {
            is AppResult.Success -> if (result.value > 0) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(AppError.Server("not_deleted"))
            }
            is AppResult.Failure -> result
        }

    override suspend fun getLastSession(userId: String, exerciseId: Int): AppResult<LastExerciseSession?> = safeCall {
        val sets = remote.getLastSessionSets(userId, exerciseId).map { it.toDomain() }
        val latestSessionId = sets.firstOrNull()?.sessionId
        if (latestSessionId == null) {
            null
        } else {
            // The query is ordered by completed_at DESC (to find the *latest* session cheaply);
            // within that session, sets must be shown in set_number order, not completion order.
            val sessionSets = sets.filter { it.sessionId == latestSessionId }.sortedBy { it.setNumber }
            LastExerciseSession(
                sessionId = latestSessionId,
                date = sessionSets.minOf { it.completedAt },
                sets = sessionSets,
                note = getExerciseNoteOrNull(latestSessionId, exerciseId),
            )
        }
    }

    /** Best-effort: the sets are what matters here, so a failed note lookup just hides the note. */
    private suspend fun getExerciseNoteOrNull(sessionId: String, exerciseId: Int): String? =
        when (val result = safeCall { remote.getExerciseNote(sessionId, exerciseId) }) {
            is AppResult.Success -> ExerciseNote.normalize(result.value)
            is AppResult.Failure -> null
        }

    override suspend fun getCompletedSince(userId: String, since: Instant): AppResult<Int> = safeCall {
        remote.getCompletedSince(userId, since.toTimestampString())
    }

    override suspend fun getLastCompletedAt(userId: String): AppResult<Instant?> = safeCall {
        remote.getLastCompletedAt(userId)?.toInstant()
    }

    override suspend fun countCompleted(userId: String): AppResult<Int> = safeCall {
        remote.countCompleted(userId)
    }
}

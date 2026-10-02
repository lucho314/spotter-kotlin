package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.WorkoutExerciseNoteInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutExerciseNoteRowDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetInsertDto

/** Wraps every Postgrest call for `workout_sessions`/`workout_sets`/`workout_exercise_notes` (history, progress sets, sync). */
interface WorkoutRemoteDataSource {
    /** [from]/[to] are an inclusive 0-based row range (`range(from, to)`), 30 rows per page. */
    suspend fun getSessions(userId: String, from: Long, to: Long): List<WorkoutSessionDto>
    suspend fun getSession(sessionId: String): WorkoutSessionDto?

    /** @return the number of rows actually updated (0 under RLS if [setId] isn't this caller's set). */
    suspend fun updateSet(setId: String, weightKg: Double, reps: Int): Int
    suspend fun insertSet(dto: WorkoutSetInsertDto)

    /** @return the number of rows actually deleted (0 under RLS if [setId] isn't this caller's set). */
    suspend fun deleteSet(setId: String): Int

    /** @return the number of rows actually deleted (0 if [sessionId] didn't match any row). */
    suspend fun deleteSession(sessionId: String): Int

    /** Non-warmup sets of completed sessions only, most recent first, capped at 200. */
    suspend fun getLastSessionSets(userId: String, exerciseId: Int): List<WorkoutSetDto>

    /** Same filter as [getLastSessionSets] but with a caller-chosen cap (progress: 500). */
    suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int): List<WorkoutSetDto>

    /** The note for [exerciseId] in [sessionId], or null if none was written. */
    suspend fun getExerciseNote(sessionId: String, exerciseId: Int): String?

    /** Every exercise note of [sessionId]. */
    suspend fun getSessionExerciseNotes(sessionId: String): List<WorkoutExerciseNoteRowDto>

    /** Creates or replaces the note of `(dto.sessionId, dto.exerciseId)`. */
    suspend fun saveExerciseNote(dto: WorkoutExerciseNoteInsertDto)

    /** A no-op if there was no note. */
    suspend fun deleteExerciseNote(sessionId: String, exerciseId: Int)

    suspend fun getCompletedSince(userId: String, sinceIso: String): Int
    suspend fun getLastCompletedAt(userId: String): String?
    suspend fun countCompleted(userId: String): Int

    /** Idempotent upserts (`onConflict = "id", ignoreDuplicates = true`), safe to retry. */
    suspend fun uploadSession(dto: WorkoutSessionInsertDto)
    suspend fun uploadSets(dtos: List<WorkoutSetInsertDto>)

    /** Idempotent upsert on `(session_id, exercise_id)`; a no-op for an empty list. */
    suspend fun uploadExerciseNotes(dtos: List<WorkoutExerciseNoteInsertDto>)
}

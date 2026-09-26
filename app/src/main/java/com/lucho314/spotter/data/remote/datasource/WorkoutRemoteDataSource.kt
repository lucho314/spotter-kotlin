package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.WorkoutSessionDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetInsertDto

/** Wraps every Postgrest call for `workout_sessions`/`workout_sets` (history, progress sets, sync). */
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

    suspend fun getCompletedSince(userId: String, sinceIso: String): Int
    suspend fun getLastCompletedAt(userId: String): String?
    suspend fun countCompleted(userId: String): Int

    /** Idempotent upserts (`onConflict = "id", ignoreDuplicates = true`), safe to retry. */
    suspend fun uploadSession(dto: WorkoutSessionInsertDto)
    suspend fun uploadSets(dtos: List<WorkoutSetInsertDto>)
}

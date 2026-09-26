package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.remote.datasource.WorkoutRemoteDataSource
import com.lucho314.spotter.data.remote.dto.WorkoutSessionDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionInsertDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetInsertDto

/** Throws plain exceptions (per ADR A2): the repository wraps every call with `safeCall`. */
class FakeWorkoutRemoteDataSource : WorkoutRemoteDataSource {

    var sessions: List<WorkoutSessionDto> = emptyList()
    var session: WorkoutSessionDto? = null
    var lastSessionSets: List<WorkoutSetDto> = emptyList()
    var exerciseSets: List<WorkoutSetDto> = emptyList()
    var deleteSessionRowsAffected: Int = 1
    var updateSetRowsAffected: Int = 1
    var deleteSetRowsAffected: Int = 1
    var completedSinceCount: Int = 0
    var lastCompletedAt: String? = null
    var countCompletedResult: Int = 0

    var uploadSessionError: Throwable? = null
    var uploadSetsError: Throwable? = null

    val uploadedSessions = mutableListOf<WorkoutSessionInsertDto>()
    val uploadedSets = mutableListOf<WorkoutSetInsertDto>()
    /** Records "session" or "sets" in call order, to verify the upload sequencing. */
    val uploadCallOrder = mutableListOf<String>()
    val getSessionsCalls = mutableListOf<Pair<Long, Long>>()

    override suspend fun getSessions(userId: String, from: Long, to: Long): List<WorkoutSessionDto> {
        getSessionsCalls += from to to
        return sessions
    }

    override suspend fun getSession(sessionId: String): WorkoutSessionDto? = session

    override suspend fun updateSet(setId: String, weightKg: Double, reps: Int): Int = updateSetRowsAffected

    override suspend fun insertSet(dto: WorkoutSetInsertDto) = Unit

    override suspend fun deleteSet(setId: String): Int = deleteSetRowsAffected

    override suspend fun deleteSession(sessionId: String): Int = deleteSessionRowsAffected

    override suspend fun getLastSessionSets(userId: String, exerciseId: Int): List<WorkoutSetDto> = lastSessionSets

    override suspend fun getExerciseSets(userId: String, exerciseId: Int, limit: Int): List<WorkoutSetDto> = exerciseSets

    override suspend fun getCompletedSince(userId: String, sinceIso: String): Int = completedSinceCount

    override suspend fun getLastCompletedAt(userId: String): String? = lastCompletedAt

    override suspend fun countCompleted(userId: String): Int = countCompletedResult

    override suspend fun uploadSession(dto: WorkoutSessionInsertDto) {
        uploadCallOrder += "session"
        uploadSessionError?.let { throw it }
        uploadedSessions += dto
    }

    override suspend fun uploadSets(dtos: List<WorkoutSetInsertDto>) {
        uploadCallOrder += "sets"
        uploadSetsError?.let { throw it }
        uploadedSets += dtos
    }
}

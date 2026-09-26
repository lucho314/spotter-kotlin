package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.testutil.FakeIdGenerator
import com.lucho314.spotter.testutil.FakeWorkoutRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryRepositoryImplTest {

    private lateinit var remote: FakeWorkoutRemoteDataSource
    private lateinit var repository: WorkoutHistoryRepositoryImpl

    private fun set(sessionId: String, setNumber: Int, completedAt: String) = WorkoutSetDto(
        id = "$sessionId-$setNumber", sessionId = sessionId, exerciseId = 42, setNumber = setNumber,
        weightKg = 80.0, reps = 10, isWarmup = false, completedAt = completedAt,
    )

    @Before
    fun setUp() {
        remote = FakeWorkoutRemoteDataSource()
        repository = WorkoutHistoryRepositoryImpl(remote, FakeIdGenerator())
    }

    @Test
    fun `getLastSession groups by the most recent row's session_id, ignoring older sessions in the batch`() = runTest {
        // Ordered most-recent-first, as the real query does; the batch also contains an older
        // session's sets (e.g. from a previous week) that must be excluded from the result.
        remote.lastSessionSets = listOf(
            set("session-new", setNumber = 2, completedAt = "2026-01-15T11:00:00Z"),
            set("session-new", setNumber = 1, completedAt = "2026-01-15T10:55:00Z"),
            set("session-old", setNumber = 1, completedAt = "2026-01-08T10:00:00Z"),
        )

        val result = repository.getLastSession("user-1", exerciseId = 42)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val session = (result as AppResult.Success).value
        assertThat(session?.sessionId).isEqualTo("session-new")
        assertThat(session?.sets).hasSize(2)
    }

    @Test
    fun `getLastSession returns null when there are no matching sets`() = runTest {
        remote.lastSessionSets = emptyList()

        val result = repository.getLastSession("user-1", exerciseId = 42)

        assertThat(result).isEqualTo(AppResult.Success(null))
    }

    @Test
    fun `deleteSession maps 0 rows affected to Server(not_deleted)`() = runTest {
        remote.deleteSessionRowsAffected = 0

        val result = repository.deleteSession("session-1")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server("not_deleted")))
    }

    @Test
    fun `deleteSession succeeds when at least one row is affected`() = runTest {
        remote.deleteSessionRowsAffected = 1

        val result = repository.deleteSession("session-1")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
    }
}

package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.data.remote.dto.WorkoutExerciseNoteRowDto
import com.lucho314.spotter.data.remote.dto.WorkoutSessionDto
import com.lucho314.spotter.data.remote.dto.WorkoutSetDto
import com.lucho314.spotter.testutil.FakeIdGenerator
import com.lucho314.spotter.testutil.FakeWorkoutRemoteDataSource
import java.io.IOException
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
    fun `getLastSession orders sets by setNumber, not by completion order`() = runTest {
        // The remote query is DESC by completed_at (to cheaply find the latest session); within
        // that session the sets must still come back in set_number order for display.
        remote.lastSessionSets = listOf(
            set("session-new", setNumber = 3, completedAt = "2026-01-15T11:10:00Z"),
            set("session-new", setNumber = 1, completedAt = "2026-01-15T11:00:00Z"),
            set("session-new", setNumber = 2, completedAt = "2026-01-15T11:05:00Z"),
        )

        val result = repository.getLastSession("user-1", exerciseId = 42)

        val session = (result as AppResult.Success).value
        assertThat(session?.sets?.map { it.setNumber }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `getLastSession includes that session's note for the exercise`() = runTest {
        remote.lastSessionSets = listOf(set("session-new", setNumber = 1, completedAt = "2026-01-15T11:00:00Z"))
        remote.exerciseNote = "me molestó el hombro"

        val session = (repository.getLastSession("user-1", exerciseId = 42) as AppResult.Success).value

        assertThat(session?.note).isEqualTo("me molestó el hombro")
        assertThat(remote.getExerciseNoteCalls).containsExactly("session-new" to 42)
    }

    @Test
    fun `getLastSession still returns the sets when the note lookup fails`() = runTest {
        remote.lastSessionSets = listOf(set("session-new", setNumber = 1, completedAt = "2026-01-15T11:00:00Z"))
        remote.getExerciseNoteError = IOException("offline")

        val session = (repository.getLastSession("user-1", exerciseId = 42) as AppResult.Success).value

        assertThat(session?.sets).hasSize(1)
        assertThat(session?.note).isNull()
    }

    @Test
    fun `getSession attaches the session's exercise notes, and still loads if they fail`() = runTest {
        remote.session = WorkoutSessionDto(id = "s1", userId = "user-1", startedAt = "2026-01-15T10:00:00Z", status = "completed")
        remote.sessionExerciseNotes = listOf(WorkoutExerciseNoteRowDto(exerciseId = 7, note = "llegué justo"))

        val withNotes = (repository.getSession("s1") as AppResult.Success).value
        assertThat(withNotes.exerciseNotes).containsExactly(7, "llegué justo")

        remote.getSessionExerciseNotesError = IOException("offline")
        val withoutNotes = (repository.getSession("s1") as AppResult.Success).value
        assertThat(withoutNotes.exerciseNotes).isEmpty()
    }

    @Test
    fun `setExerciseNote saves a normalized note, and deletes it when blank`() = runTest {
        repository.setExerciseNote("s1", 7, "  me molestó el hombro ")
        repository.setExerciseNote("s1", 8, "   ")

        assertThat(remote.savedExerciseNotes.map { Triple(it.sessionId, it.exerciseId, it.note) })
            .containsExactly(Triple("s1", 7, "me molestó el hombro"))
        assertThat(remote.deletedExerciseNotes).containsExactly("s1" to 8)
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

    @Test
    fun `getSessions(offset = 30, limit = 30) requests rows 30 to 59`() = runTest {
        repository.getSessions("user-1", offset = 30, limit = 30)

        assertThat(remote.getSessionsCalls.single()).isEqualTo(30L to 59L)
    }

    @Test
    fun `updateSet with 0 rows affected maps to NotFound`() = runTest {
        remote.updateSetRowsAffected = 0

        val result = repository.updateSet("set-1", weightKg = 80.0, reps = 10)

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `updateSet with a positive row count succeeds`() = runTest {
        remote.updateSetRowsAffected = 1

        val result = repository.updateSet("set-1", weightKg = 80.0, reps = 10)

        assertThat(result).isEqualTo(AppResult.Success(Unit))
    }

    @Test
    fun `deleteSet with 0 rows affected maps to NotFound`() = runTest {
        remote.deleteSetRowsAffected = 0

        val result = repository.deleteSet("set-1")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }
}

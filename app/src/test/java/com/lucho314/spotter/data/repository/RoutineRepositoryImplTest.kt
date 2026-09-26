package com.lucho314.spotter.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.network.SupabaseModule
import com.lucho314.spotter.data.remote.dto.RoutineSummaryDto
import com.lucho314.spotter.domain.model.RoutineExercisePatch
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.testutil.FakeRoutineRemoteDataSource
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutineRepositoryImplTest {

    private lateinit var database: SpotterDatabase
    private lateinit var remote: FakeRoutineRemoteDataSource
    private lateinit var repository: RoutineRepositoryImpl

    private val json = SupabaseModule.provideJson()
    private val userId = "user-1"

    private fun summary(id: String) = RoutineSummaryDto(
        id = id, userId = userId, name = "Push", isArchived = false,
        createdAt = "2026-01-15T10:00:00Z", updatedAt = "2026-01-15T10:00:00Z",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        remote = FakeRoutineRemoteDataSource()
        repository = RoutineRepositoryImpl(remote, database.cachedPayloadDao(), json, FakeTimeProvider(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `refreshRoutines fills the cache, observable afterwards`() = runTest {
        remote.routines = listOf(summary("r1"), summary("r2"))

        repository.refreshRoutines(userId)

        val cached = repository.observeRoutines(userId).first()
        assertThat(cached.map { it.id }).containsExactly("r1", "r2")
    }

    @Test
    fun `a refresh network error leaves the previous cache untouched`() = runTest {
        remote.routines = listOf(summary("r1"))
        repository.refreshRoutines(userId)

        remote.getRoutinesError = IOException("offline")
        val result = repository.refreshRoutines(userId)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val cached = repository.observeRoutines(userId).first()
        assertThat(cached.map { it.id }).containsExactly("r1")
    }

    @Test
    fun `reorderExercises stops at the first error and still refreshes from the server`() = runTest {
        remote.routineDetail = null
        remote.updateExerciseSortOrderError = IllegalStateException("conflict")
        remote.failOnCallNumber = 2

        val result = repository.reorderExercises(userId, "r1", listOf("re1", "re2", "re3"))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        // Only the first two calls happen: the second one throws and the third is never attempted.
        assertThat(remote.updateExerciseSortOrderCalls).containsExactly("re1" to 0, "re2" to 1).inOrder()
    }

    @Test
    fun `reorderExercises treats 0 rows affected (RLS mismatch) as a failure too`() = runTest {
        remote.routineDetail = null
        remote.rowsAffected = 0

        val result = repository.reorderExercises(userId, "r1", listOf("re1", "re2"))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        // Aborts after the first call, since it already reported 0 rows affected.
        assertThat(remote.updateExerciseSortOrderCalls).containsExactly("re1" to 0)
    }

    @Test
    fun `refreshRoutine deletes the cached detail when the server no longer has the routine`() = runTest {
        remote.routineDetail = null

        repository.refreshRoutine(userId, "r1")

        assertThat(repository.observeRoutine(userId, "r1").first()).isNull()
    }

    @Test
    fun `getRoutineDetail is always scoped to the current user (section 8, B2)`() = runTest {
        remote.routineDetail = null

        repository.refreshRoutine(userId, "r1")

        assertThat(remote.getRoutineDetailCalls).containsExactly("r1" to userId)
    }

    @Test
    fun `a corrupt cached routine list is treated as empty and the row is dropped`() = runTest {
        database.cachedPayloadDao().upsert(CachedPayloadEntity("routines:active", userId, "{ not valid json for a list }", 0L))

        val cached = repository.observeRoutines(userId).first()

        assertThat(cached).isEmpty()
        assertThat(database.cachedPayloadDao().get("routines:active", userId)).isNull()
    }

    @Test
    fun `a corrupt cached routine detail is treated as null and the row is dropped`() = runTest {
        database.cachedPayloadDao().upsert(CachedPayloadEntity("routine:r1", userId, "{ not valid json }", 0L))

        val cached = repository.observeRoutine(userId, "r1").first()

        assertThat(cached).isNull()
        assertThat(database.cachedPayloadDao().get("routine:r1", userId)).isNull()
    }

    @Test
    fun `deleteRoutine removes the routine from the cached list immediately, without needing a refresh`() = runTest {
        remote.routines = listOf(summary("r1"), summary("r2"))
        repository.refreshRoutines(userId)
        remote.routines = emptyList() // simulate offline: refreshRoutines() called after delete would no-op/fail
        remote.getRoutinesError = IOException("offline")

        val result = repository.deleteRoutine(userId, "r1")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(remote.deletedRoutineIds).containsExactly("r1")
        val cached = repository.observeRoutines(userId).first()
        assertThat(cached.map { it.id }).containsExactly("r2")
    }

    @Test
    fun `updateRoutine maps 0 rows affected to NotFound`() = runTest {
        remote.rowsAffected = 0

        val result = repository.updateRoutine(userId, "r1", RoutineInput("Push", null, null))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.NotFound))
    }

    @Test
    fun `updateRoutineExercise only sends day_number and sort_order when actually changing them`() = runTest {
        remote.routineDetail = null

        repository.updateRoutineExercise(userId, "r1", "re1", RoutineExercisePatch(targetSets = 3, targetReps = 10, restSeconds = 90, dayNumber = null))
        repository.updateRoutineExercise(userId, "r1", "re1", RoutineExercisePatch(targetSets = 3, targetReps = 10, restSeconds = 90, dayNumber = 2, sortOrder = 5))

        assertThat(remote.updateRoutineExerciseCalls).containsExactly(null to null, 2 to 5).inOrder()
    }
}

package com.lucho314.spotter.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class PendingWorkoutDaoTest {

    private lateinit var database: SpotterDatabase
    private lateinit var dao: PendingWorkoutDao

    private fun workout(id: String, userId: String = "user-1", status: String = "PENDING") = PendingWorkoutEntity(
        id = id, userId = userId, routineId = "r1", startedAt = "2026-01-15T10:00:00Z",
        completedAt = "2026-01-15T11:00:00Z", notes = null, status = status, attempts = 0,
        lastError = null, createdAtEpochMs = 1000L,
    )

    private fun set(workoutId: String, id: String = "$workoutId-set1") = PendingWorkoutSetEntity(
        id = id, workoutId = workoutId, exerciseId = 42, setNumber = 1, weightKg = 80.0, reps = 10,
        isWarmup = false, completedAt = "2026-01-15T11:00:00Z",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        dao = database.pendingWorkoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getPending only returns PENDING rows for that user, sets included`() = runTest {
        dao.insertFull(workout("w1"), listOf(set("w1")))
        dao.insertFull(workout("w2", status = "FAILED"), listOf(set("w2")))
        dao.insertFull(workout("w3", userId = "user-2"), listOf(set("w3")))

        val pending = dao.getPending("user-1")

        assertThat(pending.map { it.workout.id }).containsExactly("w1")
        assertThat(pending.single().sets).hasSize(1)
    }

    @Test
    fun `observeCount counts both PENDING and FAILED rows`() = runTest {
        dao.insertFull(workout("w1"), emptyList())
        dao.insertFull(workout("w2", status = "FAILED"), emptyList())
        dao.insertFull(workout("w3", userId = "user-2"), emptyList())

        assertThat(dao.observeCount("user-1").first()).isEqualTo(2)
    }

    @Test
    fun `observeFailed only returns FAILED rows`() = runTest {
        dao.insertFull(workout("w1"), emptyList())
        dao.insertFull(workout("w2", status = "FAILED"), emptyList())

        assertThat(dao.observeFailed("user-1").first().map { it.workout.id }).containsExactly("w2")
    }

    @Test
    fun `deleting a workout cascades to its sets`() = runTest {
        dao.insertFull(workout("w1"), listOf(set("w1")))

        dao.delete("w1")

        assertThat(dao.getPending("user-1")).isEmpty()
    }

    @Test
    fun `markFailed, resetToPending and recordAttempt update status fields`() = runTest {
        dao.insertFull(workout("w1"), emptyList())

        dao.recordAttempt("w1", "network error")
        var pending = dao.getPending("user-1").single()
        assertThat(pending.workout.attempts).isEqualTo(1)
        assertThat(pending.workout.lastError).isEqualTo("network error")

        dao.markFailed("w1", "unrecoverable")
        assertThat(dao.getPending("user-1")).isEmpty()
        assertThat(dao.observeFailed("user-1").first().single().workout.lastError).isEqualTo("unrecoverable")

        dao.resetToPending("w1")
        pending = dao.getPending("user-1").single()
        assertThat(pending.workout.status).isEqualTo("PENDING")
        assertThat(pending.workout.lastError).isNull()
    }
}

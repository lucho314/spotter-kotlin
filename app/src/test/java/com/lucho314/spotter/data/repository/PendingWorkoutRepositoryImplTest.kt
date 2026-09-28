package com.lucho314.spotter.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.testutil.FakeWorkoutRemoteDataSource
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PendingWorkoutRepositoryImplTest {

    private lateinit var database: SpotterDatabase
    private lateinit var remote: FakeWorkoutRemoteDataSource
    private lateinit var repository: PendingWorkoutRepositoryImpl

    private fun workout() = PendingWorkout(
        id = "w1", userId = "user-1", routineId = "r1",
        startedAt = Instant.ofEpochMilli(1000), completedAt = Instant.ofEpochMilli(2000),
        notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        remote = FakeWorkoutRemoteDataSource()
        repository = PendingWorkoutRepositoryImpl(database.pendingWorkoutDao(), remote)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upload sends the session before its sets`() = runTest {
        repository.upload(workout())

        assertThat(remote.uploadCallOrder).containsExactly("session", "sets").inOrder()
    }

    @Test
    fun `upload never attempts the sets when the session upload itself fails`() = runTest {
        remote.uploadSessionError = IOException("offline")

        val result = repository.upload(workout())

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat(remote.uploadCallOrder).containsExactly("session")
        assertThat(remote.uploadedSets).isEmpty()
    }

    /**
     * A network error is not a foreign-key violation - a single, unrecoverable-looking failure
     * must not trigger the `routine_id = null` retry (that retry only makes sense for
     * [isRoutineForeignKeyViolation]; see that function's own tests for the exact decision).
     */
    @Test
    fun `upload does not retry with routineId=null for an unrelated error`() = runTest {
        remote.uploadSessionError = IOException("offline")

        repository.upload(workout())

        assertThat(remote.uploadCallOrder).containsExactly("session") // only one attempt
    }
}

package com.lucho314.spotter.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionWithExercises
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.ActiveWorkoutStartOutcome
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.WeightUnit
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class ActiveWorkoutRepositoryImplTest {

    private lateinit var database: SpotterDatabase
    private lateinit var repository: ActiveWorkoutRepositoryImpl

    private fun workout(sessionId: String = "s1", userId: String = "user-1", withOneSet: Boolean = true) = ActiveWorkout(
        sessionId = sessionId,
        userId = userId,
        routineId = "r1",
        routineName = "Push",
        dayName = "Lunes",
        startedAt = Instant.ofEpochMilli(1000),
        weightUnit = WeightUnit.KG,
        currentExerciseIndex = 0,
        rest = null,
        exercises = listOf(
            ActiveExercise(
                rowId = 0L,
                position = 0,
                exerciseId = 42,
                name = "Bench",
                equipment = Equipment.BARBELL,
                mediaUrl = null,
                imageUrl = null,
                targetSets = 3,
                targetReps = 10,
                restSeconds = 90,
                sets = if (withOneSet) {
                    listOf(ActiveSet(id = "$sessionId-set1", setNumber = 1, weightText = "80", repsText = "10", isWarmup = false, completedAt = null))
                } else {
                    emptyList()
                },
            ),
        ),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        repository = ActiveWorkoutRepositoryImpl(database, database.activeWorkoutDao(), database.pendingWorkoutDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `start succeeds when the user has no active session yet`() = runTest {
        val result = repository.start(workout())

        assertThat(result).isEqualTo(AppResult.Success(ActiveWorkoutStartOutcome.Started))
        assertThat(repository.getActive("user-1")?.sessionId).isEqualTo("s1")
    }

    @Test
    fun `start reports AlreadyActive instead of silently replacing the in-progress workout`() = runTest {
        repository.start(workout(sessionId = "old-session"))

        val result = repository.start(workout(sessionId = "new-session", withOneSet = false))

        assertThat(result).isEqualTo(AppResult.Success(ActiveWorkoutStartOutcome.AlreadyActive("old-session")))
        // The original session, its exercise and its set are all still there.
        val active = repository.getActive("user-1")
        assertThat(active?.sessionId).isEqualTo("old-session")
        assertThat(active?.exercises?.single()?.sets).hasSize(1)
    }

    @Test
    fun `replace atomically discards the existing session and starts the new one`() = runTest {
        repository.start(workout(sessionId = "old-session"))

        val result = repository.replace("old-session", workout(sessionId = "new-session", withOneSet = false))

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        val active = repository.getActive("user-1")
        assertThat(active?.sessionId).isEqualTo("new-session")
        assertThat(active?.exercises?.single()?.sets).isEmpty()
    }

    @Test
    fun `replace fails instead of discarding a session that belongs to a different user`() = runTest {
        repository.start(workout(sessionId = "old-session", userId = "user-1"))

        val result = repository.replace("old-session", workout(sessionId = "new-session", userId = "user-2"))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.Unknown::class.java)
        // Untouched: the transaction rolled back.
        assertThat(repository.getActive("user-1")?.sessionId).isEqualTo("old-session")
        assertThat(repository.getActive("user-2")).isNull()
    }

    @Test
    fun `moveToOutbox is atomic - the outbox row appears and the active session disappears together`() = runTest {
        repository.start(workout())
        val pending = PendingWorkout(
            id = "s1", userId = "user-1", routineId = "r1",
            startedAt = Instant.ofEpochMilli(1000), completedAt = Instant.ofEpochMilli(2000),
            notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
        )

        repository.moveToOutbox("s1", pending)

        assertThat(repository.getActive("user-1")).isNull()
        assertThat(database.pendingWorkoutDao().getPending("user-1").map { it.workout.id }).containsExactly("s1")
    }

    @Test
    fun `observeActive reflects start, then null after discard`() = runTest {
        assertThat(repository.observeActive("user-1").first()).isNull()

        repository.start(workout())
        assertThat(repository.observeActive("user-1").first()?.sessionId).isEqualTo("s1")

        repository.discard("s1")
        assertThat(repository.observeActive("user-1").first()).isNull()
    }

    /**
     * Review carry-over (FASE 2 approval): the [SQLiteConstraintException] safety net in `start()`
     * must rethrow (so `safeCall` turns it into a `Failure`) when its re-query comes back empty -
     * that combination means the transaction rolled back (nothing was inserted) and yet there is no
     * existing session to report either, so silently returning `Started` would be a lie.
     */
    @Test
    fun `start fails instead of reporting Started when the constraint safety net's re-query finds nothing`() = runTest {
        val throwingDao = object : ActiveWorkoutDao by database.activeWorkoutDao() {
            override suspend fun startIfAbsent(
                userId: String,
                session: ActiveSessionEntity,
                exercisesWithSets: List<Pair<ActiveExerciseEntity, List<ActiveSetEntity>>>,
            ): String? = throw SQLiteConstraintException("simulated race")

            override fun observeByUser(userId: String): Flow<ActiveSessionWithExercises?> = flowOf(null)

            override suspend fun getByUser(userId: String): ActiveSessionWithExercises? = null
        }
        val repositoryWithThrowingDao = ActiveWorkoutRepositoryImpl(database, throwingDao, database.pendingWorkoutDao())

        val result = repositoryWithThrowingDao.start(workout())

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.Unknown::class.java)
    }
}

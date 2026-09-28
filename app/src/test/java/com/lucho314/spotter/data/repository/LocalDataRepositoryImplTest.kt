package com.lucho314.spotter.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.database.entity.GarminUploadEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutEntity
import com.lucho314.spotter.core.database.entity.PendingWorkoutSetEntity
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
class LocalDataRepositoryImplTest {

    private lateinit var database: SpotterDatabase
    private lateinit var repository: LocalDataRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        repository = LocalDataRepositoryImpl(
            database, database.activeWorkoutDao(), database.pendingWorkoutDao(), database.cachedPayloadDao(), database.garminUploadDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `clearAll wipes the active session, the outbox and the cache`() = runTest {
        database.activeWorkoutDao().insertFull(
            ActiveSessionEntity(
                id = "s1", userId = "user-1", routineId = "r1", routineName = "Push", dayName = null,
                startedAtEpochMs = 1000, weightUnit = "KG", currentExerciseIndex = 0,
                restEndsAtEpochMs = null, restTotalSeconds = null,
            ),
            listOf(
                ActiveExerciseEntity(
                    id = 0L, sessionId = "s1", position = 0, exerciseId = 42, name = "Bench",
                    equipment = "BARBELL", mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90,
                ) to listOf(
                    ActiveSetEntity(id = "set-1", activeExerciseId = 0L, setNumber = 1, weightText = "80", repsText = "10", isWarmup = false, completedAtEpochMs = null),
                ),
            ),
        )
        database.pendingWorkoutDao().insertFull(
            PendingWorkoutEntity(
                id = "pw-1", userId = "user-1", routineId = null, startedAt = "2026-01-15T10:00:00Z",
                completedAt = "2026-01-15T11:00:00Z", notes = null, status = "PENDING", attempts = 0,
                lastError = null, createdAtEpochMs = 1000,
            ),
            listOf(
                PendingWorkoutSetEntity(
                    id = "pws-1", workoutId = "pw-1", exerciseId = 42, setNumber = 1, weightKg = 80.0,
                    reps = 10, isWarmup = false, completedAt = "2026-01-15T11:00:00Z",
                ),
            ),
        )
        database.cachedPayloadDao().upsert(CachedPayloadEntity(key = "routines:active", userId = "user-1", json = "{}", updatedAtEpochMs = 1000))
        database.garminUploadDao().insert(
            GarminUploadEntity(
                workoutId = "pw-1", userId = "user-1", status = "PENDING", attempts = 0, lastError = null,
                garminActivityId = null, garminUploadId = null, payloadJson = null, createdAtEpochMs = 1000, updatedAtEpochMs = 1000,
            ),
        )

        repository.clearAll()

        assertThat(database.activeWorkoutDao().getByUser("user-1")).isNull()
        assertThat(database.pendingWorkoutDao().getPending("user-1")).isEmpty()
        assertThat(database.cachedPayloadDao().get("routines:active", "user-1")).isNull()
        assertThat(database.garminUploadDao().getPending("user-1")).isEmpty()
        assertThat(database.garminUploadDao().get("pw-1")).isNull()
    }
}

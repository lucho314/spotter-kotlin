package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import com.lucho314.spotter.testutil.FakeLogger
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeSyncScheduler
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class FinishWorkoutUseCaseTest {

    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val syncScheduler = FakeSyncScheduler()
    private val alarmScheduler = FakeRestTimerAlarmScheduler()
    private val timeProvider = FakeTimeProvider(instant = Instant.ofEpochSecond(5000))
    private val garminAccountRepository = FakeGarminAccountRepository()
    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()
    private val enqueueGarminUpload = EnqueueGarminUploadUseCase(garminAccountRepository, garminUploadRepository, garminUploadScheduler, FakeLogger())
    private val useCase = FinishWorkoutUseCase(activeWorkoutRepository, syncScheduler, alarmScheduler, timeProvider, enqueueGarminUpload)

    private fun set(id: String, weightText: String, repsText: String, completedAt: Instant?, isWarmup: Boolean = false) =
        ActiveSet(id = id, setNumber = 1, weightText = weightText, repsText = repsText, isWarmup = isWarmup, completedAt = completedAt)

    private fun workout(unit: WeightUnit, sets: List<ActiveSet>) = ActiveWorkout(
        sessionId = "session-1", userId = USER_ID, routineId = "routine-1", routineName = "Push",
        dayName = null, startedAt = Instant.EPOCH, weightUnit = unit, currentExerciseIndex = 0,
        rest = RestTimer(endsAt = Instant.ofEpochSecond(100), totalSeconds = 90),
        exercises = listOf(
            ActiveExercise(
                rowId = 1L, position = 0, exerciseId = 1, name = "Press banca", equipment = Equipment.BARBELL,
                mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90, sets = sets,
            ),
        ),
    )

    @Test
    fun `converts lb input to kg for the outbox`() = runTest {
        val workout = workout(WeightUnit.LB, listOf(set("set-1", "100", "10", Instant.ofEpochSecond(10))))
        activeWorkoutRepository.start(workout)

        val result = useCase(USER_ID, "session-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isEqualTo(FinishResult.Saved)
        val pending = activeWorkoutRepository.movedToOutbox.single()
        assertThat(pending.sets.single().weightKg).isEqualTo(45.36) // 100 lb -> kg, HALF_UP to 2 decimals
    }

    @Test
    fun `only completed sets are saved, reusing their id`() = runTest {
        val workout = workout(
            WeightUnit.KG,
            listOf(
                set("completed", "80", "10", Instant.ofEpochSecond(10)),
                set("not-completed", "80", "10", completedAt = null),
            ),
        )
        activeWorkoutRepository.start(workout)

        useCase(USER_ID, "session-1")

        val pending = activeWorkoutRepository.movedToOutbox.single()
        assertThat(pending.sets.map { it.id }).containsExactly("completed")
    }

    @Test
    fun `no completed sets reports NothingToSave without touching the outbox`() = runTest {
        val workout = workout(WeightUnit.KG, listOf(set("set-1", "80", "10", completedAt = null)))
        activeWorkoutRepository.start(workout)

        val result = useCase(USER_ID, "session-1")

        assertThat(result).isEqualTo(AppResult.Success(FinishResult.NothingToSave))
        assertThat(activeWorkoutRepository.movedToOutbox).isEmpty()
        assertThat(activeWorkoutRepository.getActive(USER_ID)).isNotNull() // still there: nothing was discarded either
    }

    @Test
    fun `a sessionId that no longer matches the active workout reports SessionGone, not NothingToSave`() = runTest {
        val workout = workout(WeightUnit.KG, listOf(set("set-1", "80", "10", Instant.ofEpochSecond(10))))
        activeWorkoutRepository.start(workout)

        // "session-1" is the real one; a stale caller asking to finish a different (already
        // finished/discarded elsewhere) session must not be told "nothing to save" - that would
        // read as "the user's own empty session was discarded", which may not be true at all.
        val result = useCase(USER_ID, "some-other-stale-session-id")

        assertThat(result).isEqualTo(AppResult.Success(FinishResult.SessionGone))
        assertThat(activeWorkoutRepository.movedToOutbox).isEmpty()
    }

    @Test
    fun `no active workout at all also reports SessionGone`() = runTest {
        val result = useCase(USER_ID, "session-1")

        assertThat(result).isEqualTo(AppResult.Success(FinishResult.SessionGone))
    }

    @Test
    fun `schedules the sync worker and cancels the rest alarm on success`() = runTest {
        val workout = workout(WeightUnit.KG, listOf(set("set-1", "80", "10", Instant.ofEpochSecond(10))))
        activeWorkoutRepository.start(workout)

        useCase(USER_ID, "session-1")

        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
        assertThat(alarmScheduler.cancelCallCount).isEqualTo(1)
    }

    @Test
    fun `with Garmin connected and auto-upload on, the workout is enqueued after the outbox write`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected(
            displayName = "Ada", autoUpload = true, needsReconnect = false,
        )
        val workout = workout(WeightUnit.KG, listOf(set("set-1", "80", "10", Instant.ofEpochSecond(10))))
        activeWorkoutRepository.start(workout)

        val result = useCase(USER_ID, "session-1")

        assertThat(result).isEqualTo(AppResult.Success(FinishResult.Saved))
        assertThat(activeWorkoutRepository.movedToOutbox).hasSize(1)
        assertThat(garminUploadRepository.rows).containsKey("session-1")
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `a Garmin enqueue failure never turns FinishResult into a Failure, and the outbox write still happened`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected(displayName = "Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.enqueueError = RuntimeException("garmin boom")
        val workout = workout(WeightUnit.KG, listOf(set("set-1", "80", "10", Instant.ofEpochSecond(10))))
        activeWorkoutRepository.start(workout)

        val result = useCase(USER_ID, "session-1")

        assertThat(result).isEqualTo(AppResult.Success(FinishResult.Saved))
        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
        assertThat(activeWorkoutRepository.movedToOutbox).hasSize(1)
    }
}

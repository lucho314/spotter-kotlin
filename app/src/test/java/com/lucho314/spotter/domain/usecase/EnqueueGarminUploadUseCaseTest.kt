package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminEnqueueResult
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.model.PendingSet
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import com.lucho314.spotter.testutil.FakeLogger
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class EnqueueGarminUploadUseCaseTest {

    private val garminAccountRepository = FakeGarminAccountRepository()
    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()
    private val logger = FakeLogger()
    private val useCase = EnqueueGarminUploadUseCase(garminAccountRepository, garminUploadRepository, garminUploadScheduler, logger)

    private fun workout() = ActiveWorkout(
        sessionId = "session-1", userId = USER_ID, routineId = null, routineName = "Push", dayName = null,
        startedAt = Instant.ofEpochSecond(1000), weightUnit = WeightUnit.LB, currentExerciseIndex = 0, rest = null,
        exercises = listOf(
            ActiveExercise(
                rowId = 1L, position = 2, exerciseId = 42, name = "Bench", equipment = Equipment.BARBELL,
                mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90, sets = emptyList(),
            ),
        ),
    )

    private fun pending() = PendingWorkout(
        id = "session-1", userId = USER_ID, routineId = null, startedAt = Instant.ofEpochSecond(1000),
        completedAt = Instant.ofEpochSecond(2000), notes = null, status = PendingStatus.PENDING, lastError = null,
        sets = listOf(
            PendingSet(id = "s1", exerciseId = 42, setNumber = 1, weightKg = 80.0, reps = 10, isWarmup = false, completedAt = Instant.ofEpochSecond(1500)),
            PendingSet(id = "s2", exerciseId = 99, setNumber = 1, weightKg = 20.0, reps = 12, isWarmup = false, completedAt = Instant.ofEpochSecond(1600)),
        ),
    )

    @Test
    fun `not connected does not enqueue anything`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.NotConnected

        useCase.afterFinish(workout(), pending())

        assertThat(garminUploadRepository.rows).isEmpty()
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(0)
    }

    @Test
    fun `connected but autoUpload off does not enqueue anything`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = false, needsReconnect = false)

        useCase.afterFinish(workout(), pending())

        assertThat(garminUploadRepository.rows).isEmpty()
    }

    @Test
    fun `connected with autoUpload on enqueues the correct snapshot (names, equipment, order from ActiveWorkout, kg from PendingWorkout) and schedules`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)

        useCase.afterFinish(workout(), pending())

        val row = garminUploadRepository.rows["session-1"]!!
        val snapshot = row.snapshot!!
        assertThat(snapshot.weightUnit).isEqualTo(WeightUnit.LB)
        assertThat(snapshot.startedAt).isEqualTo(Instant.ofEpochSecond(1000))
        assertThat(snapshot.completedAt).isEqualTo(Instant.ofEpochSecond(2000))

        val matched = snapshot.sets.single { it.exerciseId == 42 }
        assertThat(matched.exerciseName).isEqualTo("Bench")
        assertThat(matched.equipment).isEqualTo(Equipment.BARBELL)
        assertThat(matched.exerciseOrder).isEqualTo(2)
        assertThat(matched.weightKg).isEqualTo(80.0)

        val unmatched = snapshot.sets.single { it.exerciseId == 99 }
        assertThat(unmatched.exerciseName).isNull()
        assertThat(unmatched.equipment).isNull()
        assertThat(unmatched.exerciseOrder).isEqualTo(Int.MAX_VALUE)

        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `an exception from the upload repository never propagates`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.enqueueError = RuntimeException("boom")

        useCase.afterFinish(workout(), pending())

        assertThat(logger.warnings).isNotEmpty()
    }

    @Test
    fun `fromHistory when not connected reports NotConnected`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.NotConnected

        val result = useCase.fromHistory(USER_ID, "session-1")

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.NotConnected))
    }

    @Test
    fun `fromHistory on an already-uploaded session reports ALREADY_UPLOADED without scheduling`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed("session-1", USER_ID, GarminUploadStatus.UPLOADED)

        val result = useCase.fromHistory(USER_ID, "session-1")

        assertThat(result).isEqualTo(GarminResult.Success(GarminEnqueueResult.ALREADY_UPLOADED))
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(0)
    }

    @Test
    fun `fromHistory on a FAILED session requeues it and schedules`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed("session-1", USER_ID, GarminUploadStatus.FAILED, attempts = 3)

        val result = useCase.fromHistory(USER_ID, "session-1")

        assertThat(result).isEqualTo(GarminResult.Success(GarminEnqueueResult.ENQUEUED))
        assertThat(garminUploadRepository.rows["session-1"]!!.status).isEqualTo(GarminUploadStatus.PENDING)
        assertThat(garminUploadRepository.rows["session-1"]!!.attempts).isEqualTo(0)
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }
}

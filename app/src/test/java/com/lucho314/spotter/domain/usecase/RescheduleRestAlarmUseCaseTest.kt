package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class RescheduleRestAlarmUseCaseTest {

    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val alarmScheduler = FakeRestTimerAlarmScheduler()
    private val timeProvider = FakeTimeProvider(instant = Instant.ofEpochSecond(1_000))
    private val useCase = RescheduleRestAlarmUseCase(activeWorkoutRepository, alarmScheduler, timeProvider)

    private fun workout(rest: RestTimer?) = ActiveWorkout(
        sessionId = "session-1", userId = USER_ID, routineId = "routine-1", routineName = "Push",
        dayName = null, startedAt = Instant.EPOCH, weightUnit = WeightUnit.KG, currentExerciseIndex = 0, rest = rest,
        exercises = listOf(
            ActiveExercise(
                rowId = 1L, position = 0, exerciseId = 1, name = "Press banca", equipment = Equipment.BARBELL,
                mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90,
                sets = listOf(ActiveSet(id = "set-1", setNumber = 1, weightText = "80", repsText = "10", isWarmup = false, completedAt = null)),
            ),
        ),
    )

    @Test
    fun `re-arms the alarm when the rest period is still running`() = runTest {
        val rest = RestTimer(endsAt = Instant.ofEpochSecond(1_050), totalSeconds = 90)
        activeWorkoutRepository.start(workout(rest))

        useCase(USER_ID)

        assertThat(alarmScheduler.scheduledAt).containsExactly(Instant.ofEpochSecond(1_050))
        assertThat(activeWorkoutRepository.getActive(USER_ID)?.rest).isEqualTo(rest)
    }

    @Test
    fun `clears a stale timer that already elapsed while the app wasn't running`() = runTest {
        val rest = RestTimer(endsAt = Instant.ofEpochSecond(900), totalSeconds = 90)
        activeWorkoutRepository.start(workout(rest))

        useCase(USER_ID)

        assertThat(alarmScheduler.scheduledAt).isEmpty()
        assertThat(activeWorkoutRepository.getActive(USER_ID)?.rest).isNull()
    }

    @Test
    fun `no-op when there is no active workout`() = runTest {
        useCase(USER_ID)

        assertThat(alarmScheduler.scheduledAt).isEmpty()
    }

    @Test
    fun `no-op when the active workout has no rest timer`() = runTest {
        activeWorkoutRepository.start(workout(rest = null))

        useCase(USER_ID)

        assertThat(alarmScheduler.scheduledAt).isEmpty()
    }
}

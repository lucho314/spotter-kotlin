package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class ToggleSetCompletionUseCaseTest {

    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val alarmScheduler = FakeRestTimerAlarmScheduler()
    private val timeProvider = FakeTimeProvider()
    private val useCase = ToggleSetCompletionUseCase(activeWorkoutRepository, alarmScheduler, timeProvider)

    private fun workout(equipment: Equipment = Equipment.BARBELL, weightText: String = "80", repsText: String = "10", sets: List<ActiveSet>? = null) = ActiveWorkout(
        sessionId = "session-1", userId = USER_ID, routineId = "routine-1", routineName = "Push",
        dayName = null, startedAt = Instant.EPOCH, weightUnit = WeightUnit.KG, currentExerciseIndex = 0, rest = null,
        exercises = listOf(
            ActiveExercise(
                rowId = 1L, position = 0, exerciseId = 1, name = "Press banca", equipment = equipment,
                mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90,
                sets = sets ?: listOf(ActiveSet(id = "set-1", setNumber = 1, weightText = weightText, repsText = repsText, isWarmup = false, completedAt = null)),
            ),
        ),
    )

    @Test
    fun `accepts a comma decimal separator`() = runTest {
        val workout = workout(weightText = "72,5")
        activeWorkoutRepository.start(workout)

        val result = useCase(workout, exerciseRowId = 1L, setId = "set-1")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(activeWorkoutRepository.getActive(USER_ID)!!.exercises.single().sets.single().isCompleted).isTrue()
    }

    @Test
    fun `blank weight resolves to 0 for bodyweight exercises`() = runTest {
        val workout = workout(equipment = Equipment.BODYWEIGHT, weightText = "")
        activeWorkoutRepository.start(workout)

        val result = useCase(workout, exerciseRowId = 1L, setId = "set-1")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
    }

    @Test
    fun `blank weight is invalid for a loaded exercise`() = runTest {
        val workout = workout(equipment = Equipment.BARBELL, weightText = "")
        activeWorkoutRepository.start(workout)

        val result = useCase(workout, exerciseRowId = 1L, setId = "set-1")

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val error = (result as AppResult.Failure).error as AppError.Validation
        assertThat(error.reason).isEqualTo(ValidationReason.WEIGHT_INVALID)
    }

    @Test
    fun `completing a set always restarts the rest timer`() = runTest {
        val workout = workout()
        activeWorkoutRepository.start(workout)
        timeProvider.instant = Instant.ofEpochSecond(1000)

        useCase(workout, exerciseRowId = 1L, setId = "set-1")

        val rest = activeWorkoutRepository.getActive(USER_ID)!!.rest
        assertThat(rest).isNotNull()
        assertThat(rest!!.endsAt).isEqualTo(Instant.ofEpochSecond(1000 + 90))
        assertThat(alarmScheduler.scheduledAt).containsExactly(Instant.ofEpochSecond(1000 + 90))
    }

    @Test
    fun `completing a second set restarts the rest timer, even while one is already counting down`() = runTest {
        val workout = workout(
            sets = listOf(
                ActiveSet(id = "set-1", setNumber = 1, weightText = "80", repsText = "10", isWarmup = false, completedAt = null),
                ActiveSet(id = "set-2", setNumber = 2, weightText = "80", repsText = "10", isWarmup = false, completedAt = null),
            ),
        )
        activeWorkoutRepository.start(workout)
        timeProvider.instant = Instant.ofEpochSecond(1000)
        useCase(workout, exerciseRowId = 1L, setId = "set-1")
        val afterFirst = activeWorkoutRepository.getActive(USER_ID)!!
        assertThat(afterFirst.rest!!.endsAt).isEqualTo(Instant.ofEpochSecond(1000 + 90))

        // Rest from the first set is still counting down (only 10s in) when the second completes.
        timeProvider.instant = Instant.ofEpochSecond(1010)
        useCase(afterFirst, exerciseRowId = 1L, setId = "set-2")

        val afterSecond = activeWorkoutRepository.getActive(USER_ID)!!
        assertThat(afterSecond.rest!!.endsAt).isEqualTo(Instant.ofEpochSecond(1010 + 90))
        assertThat(alarmScheduler.scheduledAt).containsExactly(Instant.ofEpochSecond(1090), Instant.ofEpochSecond(1100)).inOrder()
    }

    @Test
    fun `un-completing a set clears completedAt but leaves the timer untouched`() = runTest {
        val workout = workout()
        activeWorkoutRepository.start(workout)
        useCase(workout, exerciseRowId = 1L, setId = "set-1") // complete it first
        val afterFirstComplete = activeWorkoutRepository.getActive(USER_ID)!!

        val result = useCase(afterFirstComplete, exerciseRowId = 1L, setId = "set-1")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        val afterUncomplete = activeWorkoutRepository.getActive(USER_ID)!!
        assertThat(afterUncomplete.exercises.single().sets.single().isCompleted).isFalse()
        assertThat(afterUncomplete.rest).isEqualTo(afterFirstComplete.rest)
        assertThat(alarmScheduler.cancelCallCount).isEqualTo(0)
    }
}

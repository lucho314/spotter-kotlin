package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeIdGenerator
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"
private const val ROUTINE_ID = "routine-1"

private fun exercise(id: Int, name: String) = Exercise(
    id = id, name = name, nameEn = name, muscleGroup = null, equipment = Equipment.BARBELL,
    imageUrl = null, mediaUrl = null, secondaryMuscles = emptyList(), instructions = emptyList(),
    difficulty = null, category = null,
)

private fun routineExercise(id: String, exerciseId: Int, name: String, dayNumber: Int, sortOrder: Int = 0) = RoutineExercise(
    id = id, routineId = ROUTINE_ID, exerciseId = exerciseId, exercise = exercise(exerciseId, name),
    sortOrder = sortOrder, dayNumber = dayNumber, targetSets = 3, targetReps = 10, restSeconds = 90,
)

class StartWorkoutUseCaseTest {

    private val routineRepository = FakeRoutineRepository()
    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val idGenerator = FakeIdGenerator()
    private val timeProvider = FakeTimeProvider()
    private val restTimerAlarmScheduler = FakeRestTimerAlarmScheduler()

    private val useCase = StartWorkoutUseCase(
        routineRepository, activeWorkoutRepository, preferencesRepository, idGenerator, timeProvider, restTimerAlarmScheduler,
    )

    private fun routine(days: List<RoutineDay>, exercises: List<RoutineExercise>) = RoutineDetail(
        id = ROUTINE_ID, userId = USER_ID, name = "Push Pull", description = null,
        daysPerWeek = null, isArchived = false, days = days, exercises = exercises,
    )

    @Test
    fun `filters exercises by the selected day`() = runTest {
        val routine = routine(
            days = listOf(RoutineDay("day-1", ROUTINE_ID, 1, "Lunes"), RoutineDay("day-2", ROUTINE_ID, 2, "Martes")),
            exercises = listOf(
                routineExercise("re-1", 1, "Press banca", dayNumber = 1),
                routineExercise("re-2", 2, "Sentadilla", dayNumber = 2),
            ),
        )
        routineRepository.setRoutineDetail(ROUTINE_ID, routine)

        val result = useCase(USER_ID, ROUTINE_ID, DaySelection.Day(1), replaceExisting = false)

        val started = (result as AppResult.Success).value as StartResult.Started
        val active = activeWorkoutRepository.getActive(USER_ID)!!
        assertThat(active.sessionId).isEqualTo(started.sessionId)
        assertThat(active.dayName).isEqualTo("Lunes")
        assertThat(active.exercises).hasSize(1)
        assertThat(active.exercises.single().exerciseId).isEqualTo(1)
    }

    @Test
    fun `builds a snapshot with target reps prefilled and weight blank`() = runTest {
        val routine = routine(days = emptyList(), exercises = listOf(routineExercise("re-1", 1, "Press banca", dayNumber = UNASSIGNED_DAY_NUMBER)))
        routineRepository.setRoutineDetail(ROUTINE_ID, routine)

        useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = false)

        val sets = activeWorkoutRepository.getActive(USER_ID)!!.exercises.single().sets
        assertThat(sets).hasSize(3) // targetSets
        assertThat(sets.map { it.setNumber }).containsExactly(1, 2, 3).inOrder()
        val set = sets.first()
        assertThat(set.weightText).isEmpty()
        assertThat(set.repsText).isEqualTo("10")
        assertThat(set.isCompleted).isFalse()
    }

    @Test
    fun `fails with NO_EXERCISES when the selected day has nothing`() = runTest {
        val routine = routine(
            days = listOf(RoutineDay("day-1", ROUTINE_ID, 1, "Lunes")),
            exercises = emptyList(),
        )
        routineRepository.setRoutineDetail(ROUTINE_ID, routine)

        val result = useCase(USER_ID, ROUTINE_ID, DaySelection.Day(1), replaceExisting = false)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val error = (result as AppResult.Failure).error as AppError.Validation
        assertThat(error.reason).isEqualTo(ValidationReason.NO_EXERCISES)
    }

    @Test
    fun `reports ActiveWorkoutExists instead of overwriting an in-progress session`() = runTest {
        val routine = routine(days = emptyList(), exercises = listOf(routineExercise("re-1", 1, "Press banca", dayNumber = UNASSIGNED_DAY_NUMBER)))
        routineRepository.setRoutineDetail(ROUTINE_ID, routine)
        val firstStart = useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = false)
        val firstSessionId = ((firstStart as AppResult.Success).value as StartResult.Started).sessionId

        val secondResult = useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = false)

        val exists = (secondResult as AppResult.Success).value as StartResult.ActiveWorkoutExists
        assertThat(exists.existing.sessionId).isEqualTo(firstSessionId)
    }

    @Test
    fun `replaceExisting discards the in-progress session and starts the new one`() = runTest {
        val routine = routine(days = emptyList(), exercises = listOf(routineExercise("re-1", 1, "Press banca", dayNumber = UNASSIGNED_DAY_NUMBER)))
        routineRepository.setRoutineDetail(ROUTINE_ID, routine)
        useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = false)

        val result = useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = true)

        val started = (result as AppResult.Success).value as StartResult.Started
        assertThat(activeWorkoutRepository.getActive(USER_ID)?.sessionId).isEqualTo(started.sessionId)
    }

    @Test
    fun `replaceExisting cancels the discarded session's rest alarm`() = runTest {
        val routine = routine(days = emptyList(), exercises = listOf(routineExercise("re-1", 1, "Press banca", dayNumber = UNASSIGNED_DAY_NUMBER)))
        routineRepository.setRoutineDetail(ROUTINE_ID, routine)
        useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = false)
        assertThat(restTimerAlarmScheduler.cancelCallCount).isEqualTo(0)

        useCase(USER_ID, ROUTINE_ID, DaySelection.All, replaceExisting = true)

        assertThat(restTimerAlarmScheduler.cancelCallCount).isEqualTo(1)
    }
}

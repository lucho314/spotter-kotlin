package com.lucho314.spotter.feature.dashboard

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.usecase.GetDashboardStatsUseCase
import com.lucho314.spotter.feature.common.SectionState
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakePendingWorkoutRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeProgressRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val progressRepository = FakeProgressRepository()
    private val routineRepository = FakeRoutineRepository()
    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val pendingWorkoutRepository = FakePendingWorkoutRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val timeProvider = FakeTimeProvider()

    private fun viewModel() = DashboardViewModel(
        GetDashboardStatsUseCase(workoutHistoryRepository, progressRepository, timeProvider),
        routineRepository, activeWorkoutRepository, pendingWorkoutRepository, preferencesRepository, authRepository, timeProvider,
    )

    private fun routine(id: String, dayName: String?) = RoutineSummary(
        id = id, name = "Routine $id", description = null, daysPerWeek = 3, exerciseCount = 5,
        days = dayName?.let { listOf(RoutineDay(id = "$id-day", routineId = id, dayNumber = 1, name = it)) } ?: emptyList(),
        createdAt = Instant.EPOCH,
    )

    private fun TestScope.collectUiState(vm: DashboardViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `all sections load successfully`() = runTest {
        workoutHistoryRepository.completedSinceResult = AppResult.Success(3)
        workoutHistoryRepository.lastCompletedAtResult = AppResult.Success(Instant.EPOCH)
        progressRepository.latestPersonalRecordResult = AppResult.Success(null)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.sessionsThisWeek).isEqualTo(SectionState.Loaded(3))
        assertThat(vm.uiState.value.latestPr).isEqualTo(SectionState.Loaded<PersonalRecord?>(null))
    }

    @Test
    fun `a partial failure leaves the other two sections loaded`() = runTest {
        workoutHistoryRepository.completedSinceResult = AppResult.Failure(AppError.Network)
        workoutHistoryRepository.lastCompletedAtResult = AppResult.Success(Instant.EPOCH)
        progressRepository.latestPersonalRecordResult = AppResult.Success(null)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.sessionsThisWeek).isInstanceOf(SectionState.Error::class.java)
        assertThat(vm.uiState.value.lastSession).isInstanceOf(SectionState.Loaded::class.java)
        assertThat(vm.uiState.value.latestPr).isInstanceOf(SectionState.Loaded::class.java)
    }

    @Test
    fun `pendingSyncCount comes from observeCount`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        pendingWorkoutRepository.seed(
            PendingWorkout(
                id = "pw-1", userId = "user-1", routineId = null, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH,
                notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
            ),
        )

        assertThat(vm.uiState.value.pendingSyncCount).isEqualTo(1)
    }

    @Test
    fun `the active workout routine name is shown in the banner`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        activeWorkoutRepository.start(
            ActiveWorkout(
                sessionId = "s1", userId = "user-1", routineId = null, routineName = "Push", dayName = null,
                startedAt = Instant.EPOCH, weightUnit = WeightUnit.KG, currentExerciseIndex = 0, rest = null,
                exercises = listOf(
                    ActiveExercise(rowId = 1, position = 0, exerciseId = 1, name = "Press", equipment = Equipment.BARBELL, mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90, sets = emptyList()),
                ),
            ),
        )

        assertThat(vm.uiState.value.activeWorkoutRoutineName).isEqualTo("Push")
    }

    @Test
    fun `the top 3 routines are ordered by their first weekday`() = runTest {
        routineRepository.setRoutines(listOf(routine("a", "Viernes"), routine("b", "Lunes"), routine("c", "Miércoles"), routine("d", "Martes")))
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.topRoutines.map { it.id }).containsExactly("b", "d", "c").inOrder()
    }

    @Test
    fun `a drop in the pending count reloads the stats`() = runTest {
        pendingWorkoutRepository.seed(
            PendingWorkout(
                id = "pw-1", userId = "user-1", routineId = null, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH,
                notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
            ),
        )
        val vm = viewModel()
        collectUiState(vm)
        val callsBefore = workoutHistoryRepository.completedSinceCalls.size

        pendingWorkoutRepository.delete("pw-1")

        assertThat(workoutHistoryRepository.completedSinceCalls.size).isGreaterThan(callsBefore)
    }

    @Test
    fun `refreshOnResume is throttled`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        vm.refreshOnResume() // first call: not throttled yet (no prior resume refresh)
        val callsAfterFirstResume = workoutHistoryRepository.completedSinceCalls.size

        vm.refreshOnResume() // immediately again: throttled, no extra call
        assertThat(workoutHistoryRepository.completedSinceCalls.size).isEqualTo(callsAfterFirstResume)

        timeProvider.instant = timeProvider.instant.plusSeconds(31)
        vm.refreshOnResume() // past the throttle window: triggers a reload
        assertThat(workoutHistoryRepository.completedSinceCalls.size).isGreaterThan(callsAfterFirstResume)
    }
}

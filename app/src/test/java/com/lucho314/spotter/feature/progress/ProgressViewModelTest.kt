package com.lucho314.spotter.feature.progress

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.ExerciseProgressPoint
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.usecase.GetExerciseProgressUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeProgressRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
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
class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progressRepository = FakeProgressRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val timeProvider = FakeTimeProvider()

    private fun viewModel() = ProgressViewModel(
        progressRepository, GetExerciseProgressUseCase(progressRepository), preferencesRepository, authRepository, timeProvider,
    )

    private fun record(id: String, exerciseId: Int) = PersonalRecord(
        id = id, exerciseId = exerciseId, exerciseName = "Press", bestWeightKg = 100.0, bestRepsAtWeight = 5,
        estimated1RmKg = 116.0, achievedAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun TestScope.collectUiState(vm: ProgressViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `loads the personal records`() = runTest {
        progressRepository.personalRecordsResult = AppResult.Success(listOf(record("pr-1", 1)))
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.records.map { it.id }).containsExactly("pr-1")
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `selecting a chip loads the chart with d slash M labels`() = runTest {
        progressRepository.personalRecordsResult = AppResult.Success(listOf(record("pr-1", 1)))
        progressRepository.exerciseSetsResult = AppResult.Success(
            listOf(
                com.lucho314.spotter.domain.model.WorkoutSet(
                    id = "set-1", sessionId = "session-1", exerciseId = 1, exerciseName = "Press", setNumber = 1,
                    weightKg = 100.0, reps = 5, rpe = null, isWarmup = false, completedAt = Instant.parse("2026-01-03T15:00:00Z"),
                ),
            ),
        )
        val vm = viewModel()
        collectUiState(vm)

        vm.onExerciseChipClick(1)

        val chart = vm.uiState.value.chart as ProgressChartState.Loaded
        assertThat(chart.points.single().label).isEqualTo("3/1")
    }

    @Test
    fun `tapping the same chip again hides the chart`() = runTest {
        progressRepository.personalRecordsResult = AppResult.Success(listOf(record("pr-1", 1)))
        val vm = viewModel()
        collectUiState(vm)

        vm.onExerciseChipClick(1)
        vm.onExerciseChipClick(1)

        assertThat(vm.uiState.value.chart).isEqualTo(ProgressChartState.Hidden)
        assertThat(vm.uiState.value.selectedExerciseId).isNull()
    }

    @Test
    fun `a failure loading the records sets loadErrorRes`() = runTest {
        progressRepository.personalRecordsResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.loadErrorRes).isNotNull()
    }

    @Test
    fun `a failure loading the chart reports ChartState Error`() = runTest {
        progressRepository.personalRecordsResult = AppResult.Success(listOf(record("pr-1", 1)))
        progressRepository.exerciseSetsResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        collectUiState(vm)

        vm.onExerciseChipClick(1)

        assertThat(vm.uiState.value.chart).isInstanceOf(ProgressChartState.Error::class.java)
    }
}

package com.lucho314.spotter.feature.profile

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.usecase.GetProfileOverviewUseCase
import com.lucho314.spotter.domain.usecase.SignOutUseCase
import com.lucho314.spotter.domain.usecase.UpdateProfileUseCase
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeLocalDataRepository
import com.lucho314.spotter.testutil.FakeLogger
import com.lucho314.spotter.testutil.FakePendingWorkoutRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeProfileRepository
import com.lucho314.spotter.testutil.FakeProgressRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeSyncScheduler
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)
    private val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
    private val profileRepository = FakeProfileRepository()
    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val progressRepository = FakeProgressRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
    private val pendingWorkoutRepository = FakePendingWorkoutRepository()
    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val restTimerAlarmScheduler = FakeRestTimerAlarmScheduler()
    private val syncScheduler = FakeSyncScheduler()
    private val localDataRepository = FakeLocalDataRepository()

    private val profile = Profile(
        id = "user-1", displayName = "Ada", avatarUrl = null, weightKg = 70.0, heightCm = 170,
        birthDate = LocalDate.of(1990, 1, 1), goal = null, rawGoal = null,
    )

    private fun viewModel() = ProfileViewModel(
        authRepository = authRepository,
        getProfileOverviewUseCase = GetProfileOverviewUseCase(profileRepository, workoutHistoryRepository, progressRepository),
        updateProfileUseCase = UpdateProfileUseCase(profileRepository, timeProvider),
        signOutUseCase = SignOutUseCase(
            authRepository, localDataRepository, preferencesRepository, pendingWorkoutRepository,
            activeWorkoutRepository, restTimerAlarmScheduler, syncScheduler, FakeLogger(),
        ),
        preferencesRepository = preferencesRepository,
        timeProvider = timeProvider,
    )

    private fun TestScope.collectUiState(vm: ProfileViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun `loads the overview and computes the age`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        val vm = viewModel()
        collectUiState(vm)

        val state = vm.uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.profile).isEqualTo(profile)
        assertThat(state.age).isEqualTo(36)
    }

    @Test
    fun `a failed stat count marks statsUnavailable`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        workoutHistoryRepository.countCompletedResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.statsUnavailable).isTrue()
    }

    @Test
    fun `changing the unit calls setWeightUnit`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        val vm = viewModel()
        collectUiState(vm)

        vm.onWeightUnitChange(WeightUnit.LB)

        assertThat(preferencesRepository.weightUnit.value).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `saving the weight successfully updates the profile and emits Message`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onSaveWeight("75")

            assertThat(awaitItem()).isEqualTo(ProfileEvent.Message(R.string.profile_saved))
        }
        assertThat(vm.uiState.value.profile?.weightKg).isEqualTo(75.0)
        assertThat(vm.uiState.value.editing).isNull()
    }

    @Test
    fun `a validation failure keeps the dialog open with an inline error`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        val vm = viewModel()
        collectUiState(vm)

        vm.onEdit(ProfileField.WEIGHT)
        vm.onSaveWeight("abc")

        assertThat(vm.uiState.value.editing).isEqualTo(ProfileField.WEIGHT)
        assertThat(vm.uiState.value.editErrorRes).isNotNull()
    }

    @Test
    fun `onSignOutClick with 2 pending workouts reports the risk`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        pendingWorkoutRepository.seed(pendingWorkout("pw-1"))
        pendingWorkoutRepository.seed(pendingWorkout("pw-2"))
        val vm = viewModel()
        collectUiState(vm)

        vm.onSignOutClick()

        assertThat(vm.uiState.value.signOutPrompt?.unsyncedWorkouts).isEqualTo(2)
    }

    @Test
    fun `confirming sign-out runs the use case, cancelling the alarm and the sync worker`() = runTest {
        profileRepository.profileResult = AppResult.Success(profile)
        val vm = viewModel()
        collectUiState(vm)

        vm.onSignOutConfirmed()

        assertThat(authRepository.signOutCallCount).isEqualTo(1)
        assertThat(restTimerAlarmScheduler.cancelCallCount).isEqualTo(1)
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
    }

    private fun pendingWorkout(id: String) = PendingWorkout(
        id = id, userId = "user-1", routineId = null, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH,
        notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
    )
}

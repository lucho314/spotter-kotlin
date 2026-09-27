package com.lucho314.spotter.feature.importroutine.code

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.model.SharedRoutineExercise
import com.lucho314.spotter.domain.usecase.ImportSharedRoutineUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeSharingRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ImportCodeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)
    private val validContent = SharedRoutineContent(
        routineName = "Push", description = null, daysPerWeek = null,
        days = emptyList(), exercises = listOf(SharedRoutineExercise(1, 1, 0, 3, 10, 90)), expiresAt = null,
    )

    private fun TestScope.collectUiState(vm: ImportCodeViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun viewModel(
        code: String?,
        sharingRepository: FakeSharingRepository = FakeSharingRepository(),
        routineRepository: FakeRoutineRepository = FakeRoutineRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(AuthState.SignedIn(user)),
    ) = ImportCodeViewModel(
        SavedStateHandle(mapOf(RouteArgs.CODE to code)),
        ImportSharedRoutineUseCase(sharingRepository, routineRepository, FakeTimeProvider()),
        authRepository,
    )

    @Test
    fun `an invalid code is Unavailable without calling the use case`() = runTest {
        val sharingRepository = FakeSharingRepository()
        val vm = viewModel("abc", sharingRepository)
        collectUiState(vm)

        val status = vm.uiState.value.status as ImportCodeStatus.Unavailable
        assertThat(status.titleRes).isEqualTo(R.string.import_code_invalid_title)
        assertThat(sharingRepository.getSharedRoutineCallCount).isEqualTo(0)
    }

    @Test
    fun `a valid code loads the preview with sanitized counts`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(validContent) }
        val vm = viewModel("K7MN3QXP", sharingRepository)
        collectUiState(vm)

        val status = vm.uiState.value.status as ImportCodeStatus.Ready
        assertThat(status.preview.routineName).isEqualTo("Push")
        assertThat(status.preview.exerciseCount).isEqualTo(1)
    }

    @Test
    fun `NotFound is Unavailable`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(null) }
        val vm = viewModel("K7MN3QXP", sharingRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.status).isInstanceOf(ImportCodeStatus.Unavailable::class.java)
    }

    @Test
    fun `a network failure is a retryable LoadError, retry can then succeed`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel("K7MN3QXP", sharingRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.status).isInstanceOf(ImportCodeStatus.LoadError::class.java)

        sharingRepository.sharedRoutineResult = AppResult.Success(validContent)
        vm.retry()
        runCurrent()

        assertThat(vm.uiState.value.status).isInstanceOf(ImportCodeStatus.Ready::class.java)
    }

    @Test
    fun `importing succeeds and emits Imported with the new routine id`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(validContent) }
        val routineRepository = FakeRoutineRepository()
        val vm = viewModel("K7MN3QXP", sharingRepository, routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportCodeEvent.Imported("new-routine-id"))
        }
    }

    @Test
    fun `NotFound while importing switches status to Unavailable`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(validContent) }
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Failure(AppError.NotFound) }
        val vm = viewModel("K7MN3QXP", sharingRepository, routineRepository)
        collectUiState(vm)

        vm.onImportClick()
        runCurrent()

        assertThat(vm.uiState.value.status).isInstanceOf(ImportCodeStatus.Unavailable::class.java)
    }

    @Test
    fun `a network failure while importing reports ActionFailed and resets importing`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(validContent) }
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel("K7MN3QXP", sharingRepository, routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportCodeEvent.ActionFailed(R.string.error_network))
        }
        assertThat(vm.uiState.value.importing).isFalse()
    }

    @Test
    fun `a double tap only calls createRoutine once`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(validContent) }
        val routineRepository = FakeRoutineRepository()
        val vm = viewModel("K7MN3QXP", sharingRepository, routineRepository)
        collectUiState(vm)

        vm.onImportClick()
        vm.onImportClick()
        runCurrent()

        assertThat(routineRepository.createRoutineCalls).hasSize(1)
    }

    @Test
    fun `importing without a signed-in user reports ActionFailed`() = runTest {
        val sharingRepository = FakeSharingRepository().apply { sharedRoutineResult = AppResult.Success(validContent) }
        val vm = viewModel("K7MN3QXP", sharingRepository, authRepository = FakeAuthRepository(AuthState.SignedOut))
        collectUiState(vm)

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportCodeEvent.ActionFailed(R.string.error_unauthorized))
        }
    }
}

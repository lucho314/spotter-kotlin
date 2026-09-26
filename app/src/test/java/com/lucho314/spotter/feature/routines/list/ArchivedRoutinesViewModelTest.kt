package com.lucho314.spotter.feature.routines.list

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedRoutinesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun routine(id: String) = RoutineSummary(
        id = id, name = "Routine $id", description = null, daysPerWeek = null,
        exerciseCount = 0, days = emptyList(), createdAt = Instant.EPOCH,
    )

    private fun TestScope.collectUiState(vm: ArchivedRoutinesViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun viewModel(routineRepository: FakeRoutineRepository = FakeRoutineRepository()) =
        ArchivedRoutinesViewModel(routineRepository, FakeAuthRepository(AuthState.SignedIn(user)))

    @Test
    fun `shows the cached archived routines`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setArchivedRoutines(listOf(routine("a1"), routine("a2"))) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.routines.map { it.id }).containsExactly("a1", "a2")
    }

    @Test
    fun `restoring successfully unarchives the routine`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onRestore("a1")
        runCurrent()

        assertThat(routineRepository.setArchivedCalls).containsExactly("a1" to false)
    }

    @Test
    fun `a failed restore emits a one-shot ActionFailed event`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setArchivedResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onRestore("a1")
            val event = awaitItem() as ArchivedRoutinesEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(R.string.error_network)
        }
    }

    @Test
    fun `a refresh failure with nothing cached surfaces a persistent load error`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { refreshArchivedResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.loadErrorRes).isEqualTo(R.string.error_network)
    }
}

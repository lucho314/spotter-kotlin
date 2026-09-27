package com.lucho314.spotter.feature.history.list

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.PendingStatus
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakePendingWorkoutRepository
import com.lucho314.spotter.testutil.FakeSyncScheduler
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
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val pendingWorkoutRepository = FakePendingWorkoutRepository()
    private val syncScheduler = FakeSyncScheduler()
    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val timeProvider = FakeTimeProvider()

    private fun viewModel() = HistoryViewModel(workoutHistoryRepository, pendingWorkoutRepository, syncScheduler, authRepository, timeProvider)

    private fun session(id: String) = WorkoutSessionSummary(id = id, routineId = "r1", routineName = "Push", startedAt = Instant.EPOCH, completedAt = Instant.EPOCH.plusSeconds(3600))

    private fun TestScope.collectUiState(vm: HistoryViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `the first load requests offset 0`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        assertThat(workoutHistoryRepository.getSessionsCalls.first()).isEqualTo(0 to 30)
    }

    @Test
    fun `loadMore uses offset equal to the current session count and concatenates without duplicates`() = runTest {
        workoutHistoryRepository.sessionsProvider = { offset, _ ->
            if (offset == 0) AppResult.Success((1..30).map { session("s$it") }) else AppResult.Success(listOf(session("s30"), session("s31")))
        }
        val vm = viewModel()
        collectUiState(vm)

        vm.loadMore()

        assertThat(workoutHistoryRepository.getSessionsCalls.last()).isEqualTo(30 to 30)
        assertThat(vm.uiState.value.sessions.map { it.id }).hasSize(31)
        assertThat(vm.uiState.value.sessions.map { it.id }.distinct()).hasSize(31)
    }

    @Test
    fun `a page smaller than the page size means there is no more`() = runTest {
        workoutHistoryRepository.sessionsResult = AppResult.Success(listOf(session("s1")))
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `deleting a session successfully removes it and emits SessionDeleted`() = runTest {
        workoutHistoryRepository.sessionsResult = AppResult.Success(listOf(session("s1")))
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onDeleteSession("s1")
            assertThat(awaitItem()).isEqualTo(HistoryEvent.SessionDeleted)
        }
        assertThat(vm.uiState.value.sessions).isEmpty()
    }

    @Test
    fun `a failed delete keeps the item and emits ActionFailed`() = runTest {
        workoutHistoryRepository.sessionsResult = AppResult.Success(listOf(session("s1")))
        workoutHistoryRepository.deleteSessionResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onDeleteSession("s1")
            assertThat(awaitItem()).isInstanceOf(HistoryEvent.ActionFailed::class.java)
        }
        assertThat(vm.uiState.value.sessions.map { it.id }).containsExactly("s1")
    }

    @Test
    fun `retrying a failed workout resets it to pending and schedules a sync`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        vm.onRetryFailed("pw-1")

        assertThat(pendingWorkoutRepository.resetIds).containsExactly("pw-1")
        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `discarding a failed workout deletes it`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        vm.onDiscardFailed("pw-1")

        assertThat(pendingWorkoutRepository.deletedIds).containsExactly("pw-1")
    }

    @Test
    fun `a drop in the pending count triggers a reload`() = runTest {
        pendingWorkoutRepository.seed(pendingWorkout("pw-1"))
        pendingWorkoutRepository.seed(pendingWorkout("pw-2"))
        val vm = viewModel()
        collectUiState(vm)
        val callsBefore = workoutHistoryRepository.getSessionsCalls.size

        pendingWorkoutRepository.delete("pw-1")

        assertThat(workoutHistoryRepository.getSessionsCalls.size).isGreaterThan(callsBefore)
    }

    @Test
    fun `an error on the first page with nothing on screen sets loadErrorRes`() = runTest {
        workoutHistoryRepository.sessionsResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.loadErrorRes).isNotNull()
    }

    private fun pendingWorkout(id: String) = PendingWorkout(
        id = id, userId = "user-1", routineId = null, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH,
        notes = null, sets = emptyList(), status = PendingStatus.PENDING, lastError = null,
    )
}

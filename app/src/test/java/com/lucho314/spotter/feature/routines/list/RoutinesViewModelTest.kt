package com.lucho314.spotter.feature.routines.list

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun routine(id: String, dayNames: List<String> = emptyList()) = RoutineSummary(
        id = id,
        name = "Routine $id",
        description = null,
        daysPerWeek = null,
        exerciseCount = 0,
        days = dayNames.mapIndexed { index, name -> RoutineDay(id = "$id-day-$index", routineId = id, dayNumber = index + 1, name = name) },
        createdAt = Instant.EPOCH,
    )

    private fun TestScope.collectUiState(vm: RoutinesViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun viewModel(
        routineRepository: com.lucho314.spotter.domain.repository.RoutineRepository = FakeRoutineRepository(),
        timeProvider: FakeTimeProvider = FakeTimeProvider(),
        activeWorkoutRepository: FakeActiveWorkoutRepository = FakeActiveWorkoutRepository(),
    ) = RoutinesViewModel(routineRepository, timeProvider, activeWorkoutRepository, FakeAuthRepository(AuthState.SignedIn(user)))

    @Test
    fun `routines are ordered by their earliest weekday`() = runTest {
        val routineRepository = FakeRoutineRepository()
        routineRepository.setRoutines(
            listOf(
                routine("wed-first", listOf("Miércoles")),
                routine("no-days"),
                routine("mon-first", listOf("Lunes", "Jueves")),
            ),
        )
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.routines.map { it.id }).containsExactly("mon-first", "wed-first", "no-days").inOrder()
    }

    @Test
    fun `archiving successfully does not emit a failure event`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onArchive("routine-1")
        runCurrent()

        assertThat(routineRepository.setArchivedCalls).containsExactly("routine-1" to true)
    }

    @Test
    fun `a failed archive emits a one-shot ActionFailed event`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setArchivedResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onArchive("routine-1")
            val event = awaitItem() as RoutinesEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(R.string.error_network)
        }
    }

    @Test
    fun `two identical consecutive archive failures each produce their own event`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setArchivedResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onArchive("routine-1")
            assertThat((awaitItem() as RoutinesEvent.ActionFailed).messageRes).isEqualTo(R.string.error_network)
            vm.onArchive("routine-1")
            assertThat((awaitItem() as RoutinesEvent.ActionFailed).messageRes).isEqualTo(R.string.error_network)
        }
    }

    @Test
    fun `an invalid import code is rejected without producing a ShareCode to navigate with`() {
        val vm = viewModel()

        assertThat(vm.parseImportCode("not a code!")).isNull()
        assertThat(vm.parseImportCode("K7MN3QXP")?.value).isEqualTo("K7MN3QXP")
    }

    @Test
    fun `loading is true until the initial refresh completes with no cached routines yet`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val delegate = FakeRoutineRepository()
        val routineRepository = object : com.lucho314.spotter.domain.repository.RoutineRepository by delegate {
            override suspend fun refreshRoutines(userId: String): AppResult<Unit> {
                gate.await()
                return AppResult.Success(Unit)
            }
        }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.loading).isTrue()

        gate.complete(Unit)
        runCurrent()

        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `a refresh failure with nothing cached surfaces a persistent load error, not just an empty list`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { refreshRoutinesResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.routines).isEmpty()
        assertThat(vm.uiState.value.loadErrorRes).isEqualTo(R.string.error_network)
    }

    @Test
    fun `a refresh failure with routines already cached keeps showing them, without a persistent load error`() = runTest {
        val routineRepository = FakeRoutineRepository().apply {
            setRoutines(listOf(routine("r1")))
            refreshRoutinesResult = AppResult.Failure(AppError.Network)
        }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.routines.map { it.id }).containsExactly("r1")
        assertThat(vm.uiState.value.loadErrorRes).isNull()
    }

    @Test
    fun `refreshOnResume refreshes again after the throttle window passes`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val timeProvider = FakeTimeProvider(instant = Instant.EPOCH)
        val vm = viewModel(routineRepository, timeProvider)
        collectUiState(vm)
        val refreshCountAfterInit = routineRepository.refreshRoutinesCallCount

        timeProvider.instant = Instant.EPOCH.plus(Duration.ofSeconds(10))
        vm.refreshOnResume()
        runCurrent()

        assertThat(routineRepository.refreshRoutinesCallCount).isEqualTo(refreshCountAfterInit + 1)
    }

    @Test
    fun `a user-initiated refresh failure with routines already cached emits an ActionFailed event`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutines(listOf(routine("r1"))) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        routineRepository.refreshRoutinesResult = AppResult.Failure(AppError.Network)
        vm.events.test {
            vm.refresh()
            val event = awaitItem() as RoutinesEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(R.string.error_network)
        }
    }

    @Test
    fun `a silent resume refresh failure with routines already cached does not emit any event`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutines(listOf(routine("r1"))) }
        val timeProvider = FakeTimeProvider(instant = Instant.EPOCH)
        val vm = viewModel(routineRepository, timeProvider)
        collectUiState(vm)

        routineRepository.refreshRoutinesResult = AppResult.Failure(AppError.Network)
        timeProvider.instant = Instant.EPOCH.plus(Duration.ofSeconds(10))
        vm.events.test {
            vm.refreshOnResume()
            runCurrent()
            expectNoEvents()
        }
        // The cached list is still shown - only the (silent) attempt to refresh it failed.
        assertThat(vm.uiState.value.routines.map { it.id }).containsExactly("r1")
    }

    @Test
    fun `refreshOnResume is throttled right after a previous refresh`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val timeProvider = FakeTimeProvider(instant = Instant.EPOCH)
        val vm = viewModel(routineRepository, timeProvider)
        collectUiState(vm)
        val refreshCountAfterInit = routineRepository.refreshRoutinesCallCount

        timeProvider.instant = Instant.EPOCH.plusMillis(500)
        vm.refreshOnResume()
        runCurrent()

        assertThat(routineRepository.refreshRoutinesCallCount).isEqualTo(refreshCountAfterInit)
    }

    @Test
    fun `exposes the active workout's routine name for the resume banner`() = runTest {
        val activeWorkoutRepository = FakeActiveWorkoutRepository()
        val vm = viewModel(activeWorkoutRepository = activeWorkoutRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.activeWorkoutRoutineName).isNull()

        activeWorkoutRepository.start(
            com.lucho314.spotter.domain.model.ActiveWorkout(
                sessionId = "s1", userId = user.id, routineId = "r1", routineName = "Push Pull", dayName = null,
                startedAt = Instant.EPOCH, weightUnit = com.lucho314.spotter.domain.model.WeightUnit.KG,
                currentExerciseIndex = 0, rest = null, exercises = emptyList(),
            ),
        )
        runCurrent()

        assertThat(vm.uiState.value.activeWorkoutRoutineName).isEqualTo("Push Pull")
    }
}

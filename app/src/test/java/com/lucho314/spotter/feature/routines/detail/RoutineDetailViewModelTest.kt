package com.lucho314.spotter.feature.routines.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.RoutineExercisePatch
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.domain.usecase.DaySelection
import com.lucho314.spotter.domain.usecase.StartWorkoutUseCase
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeIdGenerator
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun exercise(id: String, exerciseId: Int, sortOrder: Int, dayNumber: Int) = RoutineExercise(
        id = id,
        routineId = "r1",
        exerciseId = exerciseId,
        exercise = null,
        sortOrder = sortOrder,
        dayNumber = dayNumber,
        targetSets = 3,
        targetReps = 10,
        restSeconds = 90,
    )

    private fun TestScope.collectUiState(vm: RoutineDetailViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun viewModel(
        routineRepository: com.lucho314.spotter.domain.repository.RoutineRepository,
        activeWorkoutRepository: FakeActiveWorkoutRepository = FakeActiveWorkoutRepository(),
        timeProvider: FakeTimeProvider = FakeTimeProvider(),
        sharingRepository: com.lucho314.spotter.testutil.FakeSharingRepository = com.lucho314.spotter.testutil.FakeSharingRepository(),
    ) = RoutineDetailViewModel(
        SavedStateHandle(mapOf("routineId" to "r1")),
        routineRepository,
        StartWorkoutUseCase(routineRepository, activeWorkoutRepository, FakePreferencesRepository(), FakeIdGenerator(), timeProvider, FakeRestTimerAlarmScheduler()),
        com.lucho314.spotter.domain.usecase.ShareRoutineUseCase(sharingRepository, java.security.SecureRandom()),
        timeProvider,
        FakeAuthRepository(AuthState.SignedIn(user)),
    )

    @Test
    fun `groups exercises by day and puts orphans (including the unassigned sentinel) in their own group`() = runTest {
        val monday = RoutineDay("d1", "r1", 1, "Lunes")
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = 1, isArchived = false,
            days = listOf(monday),
            exercises = listOf(
                exercise("a", 1, sortOrder = 0, dayNumber = 1),
                exercise("b", 2, sortOrder = 0, dayNumber = UNASSIGNED_DAY_NUMBER),
                // day 9 has no matching row (its day was deleted) - still an orphan, grouped with "unassigned".
                exercise("c", 3, sortOrder = 1, dayNumber = 9),
            ),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.dayGroups).hasSize(2)
        assertThat(vm.uiState.value.dayGroups[0].day?.name).isEqualTo("Lunes")
        assertThat(vm.uiState.value.dayGroups[0].exercises.map { it.id }).containsExactly("a")
        assertThat(vm.uiState.value.dayGroups[1].day).isNull()
        assertThat(vm.uiState.value.dayGroups[1].exercises.map { it.id }).containsExactly("b", "c")
    }

    @Test
    fun `used day names are exposed so the day picker can disable them`() = runTest {
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = 2, isArchived = false,
            days = listOf(RoutineDay("d1", "r1", 1, "Lunes"), RoutineDay("d2", "r1", 2, "Jueves")),
            exercises = emptyList(),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.usedDayNames).containsExactly("Lunes", "Jueves")
    }

    @Test
    fun `moving an exercise to another day sends its dayNumber in the patch`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onUpdateExercise(routineExerciseId = "re1", targetSets = 3, targetReps = 10, restSeconds = 90, newDayNumber = 2)
        runCurrent()

        val (routineId, exerciseId, patch) = routineRepository.updateRoutineExerciseCalls.single()
        assertThat(routineId).isEqualTo("r1")
        assertThat(exerciseId).isEqualTo("re1")
        assertThat(patch).isEqualTo(RoutineExercisePatch(targetSets = 3, targetReps = 10, restSeconds = 90, dayNumber = 2))
    }

    @Test
    fun `moving an exercise to a real day appends it at that day's next free sort_order`() = runTest {
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = 2, isArchived = false,
            days = listOf(RoutineDay("d1", "r1", 1, "Lunes"), RoutineDay("d2", "r1", 2, "Jueves")),
            exercises = listOf(
                exercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = 2),
                exercise("re2", exerciseId = 2, sortOrder = 1, dayNumber = 2),
                exercise("re3", exerciseId = 3, sortOrder = 0, dayNumber = 1), // the one being moved
            ),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onUpdateExercise(routineExerciseId = "re3", targetSets = 3, targetReps = 10, restSeconds = 90, newDayNumber = 2)
        runCurrent()

        val (_, _, patch) = routineRepository.updateRoutineExerciseCalls.single()
        assertThat(patch).isEqualTo(RoutineExercisePatch(targetSets = 3, targetReps = 10, restSeconds = 90, dayNumber = 2, sortOrder = 2))
    }

    @Test
    fun `moving to unassigned in a routine that already has real days resolves to the sentinel day number`() = runTest {
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = 1, isArchived = false,
            days = listOf(RoutineDay("d1", "r1", 1, "Lunes")),
            exercises = listOf(
                exercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = 1), // the one being moved
                exercise("re2", exerciseId = 2, sortOrder = 3, dayNumber = UNASSIGNED_DAY_NUMBER),
            ),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onUpdateExercise(routineExerciseId = "re1", targetSets = 3, targetReps = 10, restSeconds = 90, newDayNumber = UNASSIGNED_DAY_NUMBER)
        runCurrent()

        val (_, _, patch) = routineRepository.updateRoutineExerciseCalls.single()
        assertThat(patch).isEqualTo(RoutineExercisePatch(targetSets = 3, targetReps = 10, restSeconds = 90, dayNumber = UNASSIGNED_DAY_NUMBER, sortOrder = 4))
    }

    @Test
    fun `moving to unassigned in a legacy dayless routine resolves to day_number 1, same as AddExercise's flat bucket`() = runTest {
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Legacy", description = null, daysPerWeek = null, isArchived = false,
            days = emptyList(),
            exercises = listOf(
                exercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = 1),
                exercise("re2", exerciseId = 2, sortOrder = 1, dayNumber = 1), // the one being moved (a no-op in practice, but exercises the resolution)
            ),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onUpdateExercise(routineExerciseId = "re2", targetSets = 3, targetReps = 10, restSeconds = 90, newDayNumber = UNASSIGNED_DAY_NUMBER)
        runCurrent()

        val (_, _, patch) = routineRepository.updateRoutineExerciseCalls.single()
        assertThat(patch.dayNumber).isEqualTo(1)
        assertThat(patch.sortOrder).isEqualTo(2)
    }

    @Test
    fun `a conflict while moving an exercise (including to unassigned) emits a clear, specific one-shot message`() = runTest {
        val routineRepository = FakeRoutineRepository().apply {
            updateRoutineExerciseResult = AppResult.Failure(AppError.Conflict("duplicate"))
        }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onUpdateExercise(routineExerciseId = "re1", targetSets = 3, targetReps = 10, restSeconds = 90, newDayNumber = UNASSIGNED_DAY_NUMBER)
            val event = awaitItem() as RoutineDetailEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(R.string.error_exercise_already_in_day)
        }
    }

    @Test
    fun `reordering surfaces a one-shot error event on failure and bumps the reorder revision`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { reorderExercisesResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)
        val revisionBefore = vm.uiState.value.reorderRevision

        vm.events.test {
            vm.onReorderDayGroup(listOf("re1", "re2"))
            val event = awaitItem() as RoutineDetailEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(R.string.error_network)
        }
        assertThat(routineRepository.reorderExercisesCalls).containsExactly(listOf("re1", "re2"))
        // The revision bump is what lets the screen discard its stale optimistic drag order even
        // when the (failed) reorder left the cached `dayGroups` byte-for-byte identical.
        assertThat(vm.uiState.value.reorderRevision).isEqualTo(revisionBefore + 1)
    }

    @Test
    fun `two identical consecutive action failures each produce their own event (no StateFlow-style dedup)`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { reorderExercisesResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onReorderDayGroup(listOf("re1"))
            assertThat((awaitItem() as RoutineDetailEvent.ActionFailed).messageRes).isEqualTo(R.string.error_network)
            vm.onReorderDayGroup(listOf("re1"))
            assertThat((awaitItem() as RoutineDetailEvent.ActionFailed).messageRes).isEqualTo(R.string.error_network)
        }
    }

    @Test
    fun `adding the first day of a legacy dayless routine reuses the exercises' shared day number`() = runTest {
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Legacy", description = null, daysPerWeek = null, isArchived = false,
            days = emptyList(),
            exercises = listOf(exercise("a", 1, sortOrder = 0, dayNumber = 1), exercise("b", 2, sortOrder = 1, dayNumber = 1)),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.onAddDay("Lunes")
        runCurrent()

        assertThat(routineRepository.addDaysCalls.single()).containsExactly(1 to "Lunes")
    }

    @Test
    fun `adding a day when all 7 slots are taken shows a validation message instead of guessing a number`() = runTest {
        val days = (1..7).map { RoutineDay("d$it", "r1", it, "Day $it") }
        val detail = RoutineDetail(
            id = "r1", userId = "user-1", name = "Full", description = null, daysPerWeek = 7, isArchived = false,
            days = days, exercises = emptyList(),
        )
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", detail) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onAddDay("Otro día")
            val event = awaitItem() as RoutineDetailEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(R.string.validation_days_max_reached)
        }
        assertThat(routineRepository.addDaysCalls).isEmpty()
    }

    @Test
    fun `an initial load failure with no cached routine surfaces a persistent load error`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { refreshRoutineResult = AppResult.Failure(AppError.Network) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.routine).isNull()
        assertThat(vm.uiState.value.loading).isFalse()
        assertThat(vm.uiState.value.loadErrorRes).isEqualTo(R.string.error_network)
    }

    @Test
    fun `loading stays true while the initial refresh is still in flight`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val delegate = FakeRoutineRepository()
        val routineRepository = object : com.lucho314.spotter.domain.repository.RoutineRepository by delegate {
            override suspend fun refreshRoutine(userId: String, routineId: String): AppResult<Unit> {
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

    private fun routineWithOneExercise(dayNumber: Int = UNASSIGNED_DAY_NUMBER) = RoutineDetail(
        id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = null, isArchived = false,
        days = emptyList(),
        exercises = listOf(exercise("re1", 1, sortOrder = 0, dayNumber = dayNumber)),
    )

    @Test
    fun `onStartWorkout emits WorkoutStarted on success`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", routineWithOneExercise()) }
        val vm = viewModel(routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onStartWorkout(DaySelection.All)
            assertThat(awaitItem()).isEqualTo(RoutineDetailEvent.WorkoutStarted)
        }
    }

    @Test
    fun `onStartWorkout reports WorkoutAlreadyActive instead of overwriting an in-progress session`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", routineWithOneExercise()) }
        val activeWorkoutRepository = FakeActiveWorkoutRepository()
        val vm = viewModel(routineRepository, activeWorkoutRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onStartWorkout(DaySelection.All)
            assertThat(awaitItem()).isEqualTo(RoutineDetailEvent.WorkoutStarted)

            vm.onStartWorkout(DaySelection.All)
            val event = awaitItem() as RoutineDetailEvent.WorkoutAlreadyActive
            assertThat(event.existing.routineName).isEqualTo("Push")
        }
    }

    @Test
    fun `onStartWorkout with replaceExisting discards the in-progress session`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", routineWithOneExercise()) }
        val activeWorkoutRepository = FakeActiveWorkoutRepository()
        val vm = viewModel(routineRepository, activeWorkoutRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onStartWorkout(DaySelection.All)
            assertThat(awaitItem()).isEqualTo(RoutineDetailEvent.WorkoutStarted)

            vm.onStartWorkout(DaySelection.All, replaceExisting = true)
            assertThat(awaitItem()).isEqualTo(RoutineDetailEvent.WorkoutStarted)
        }
    }

    @Test
    fun `todayWeekdayName reflects the injected TimeProvider's zone`() {
        val timeProvider = FakeTimeProvider(
            instant = java.time.Instant.parse("2026-01-19T10:00:00Z"), // a Monday in America/Argentina/Buenos_Aires
        )
        val vm = viewModel(FakeRoutineRepository(), timeProvider = timeProvider)

        assertThat(vm.todayWeekdayName()).isEqualTo("Lunes")
    }

    @Test
    fun `onShareClick sends ShareCodeReady with the routine name and code on success`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", routineWithOneExercise()) }
        val sharingRepository = com.lucho314.spotter.testutil.FakeSharingRepository().apply {
            findActiveShareResult = AppResult.Success(requireNotNull(com.lucho314.spotter.domain.model.ShareCode.parse("K7MN3QXP")))
        }
        val vm = viewModel(routineRepository, sharingRepository = sharingRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onShareClick()
            val event = awaitItem() as RoutineDetailEvent.ShareCodeReady
            assertThat(event.code).isEqualTo("K7MN3QXP")
        }
    }

    @Test
    fun `onShareClick failure reports the mapped error`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", routineWithOneExercise()) }
        val sharingRepository = com.lucho314.spotter.testutil.FakeSharingRepository().apply {
            findActiveShareResult = AppResult.Failure(AppError.Network)
        }
        val vm = viewModel(routineRepository, sharingRepository = sharingRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onShareClick()
            assertThat(awaitItem()).isEqualTo(RoutineDetailEvent.ActionFailed(R.string.error_network))
        }
    }

    @Test
    fun `onShareClick ignores a second call while the first is still in flight`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { setRoutineDetail("r1", routineWithOneExercise()) }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val sharingRepository = com.lucho314.spotter.testutil.FakeSharingRepository().apply { createShareGate = gate }
        val vm = viewModel(routineRepository, sharingRepository = sharingRepository)
        collectUiState(vm)

        vm.onShareClick()
        runCurrent()
        assertThat(vm.uiState.value.sharing).isTrue()
        vm.onShareClick()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertThat(sharingRepository.createShareCodes).hasSize(1)
        assertThat(vm.uiState.value.sharing).isFalse()
    }

    @Test
    fun `onShareClick does nothing without a loaded routine`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val sharingRepository = com.lucho314.spotter.testutil.FakeSharingRepository()
        val vm = viewModel(routineRepository, sharingRepository = sharingRepository)
        collectUiState(vm)

        vm.onShareClick()
        runCurrent()

        assertThat(sharingRepository.findActiveShareCallCount).isEqualTo(0)
    }
}

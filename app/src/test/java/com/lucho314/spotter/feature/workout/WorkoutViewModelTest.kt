package com.lucho314.spotter.feature.workout

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.RestTimer
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.usecase.DiscardWorkoutUseCase
import com.lucho314.spotter.domain.usecase.EnqueueGarminUploadUseCase
import com.lucho314.spotter.domain.usecase.FinishWorkoutUseCase
import com.lucho314.spotter.domain.usecase.ToggleSetCompletionUseCase
import com.lucho314.spotter.domain.usecase.UpdateSetInputUseCase
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import com.lucho314.spotter.testutil.FakeLogger
import com.lucho314.spotter.testutil.FakeNetworkMonitor
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeSyncScheduler
import com.lucho314.spotter.testutil.FakeIdGenerator
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

/**
 * Uses an explicit, shared [StandardTestDispatcher] (not [MainDispatcherRule]'s default
 * `UnconfinedTestDispatcher()`) for both `Dispatchers.Main` and `runTest`'s own scope: this test
 * exercises real `delay()`-based virtual time (the debounce and the session/rest ticker), which
 * needs `advanceTimeBy`/`runCurrent` here to actually drive the coroutines `viewModelScope.launch`
 * schedules on `Dispatchers.Main` - two independently-created `UnconfinedTestDispatcher()`s (the
 * rule's default, and `runTest`'s own) would each carry their *own* [kotlinx.coroutines.test.TestCoroutineScheduler],
 * so advancing one would never affect delays scheduled on the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val activeWorkoutRepository = FakeActiveWorkoutRepository()
    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val restTimerAlarmScheduler = FakeRestTimerAlarmScheduler()
    private val networkMonitor = FakeNetworkMonitor()
    private val idGenerator = FakeIdGenerator()
    private val timeProvider = FakeTimeProvider()

    private fun viewModel() = WorkoutViewModel(
        activeWorkoutRepository = activeWorkoutRepository,
        workoutHistoryRepository = workoutHistoryRepository,
        authRepository = authRepository,
        updateSetInputUseCase = UpdateSetInputUseCase(activeWorkoutRepository),
        toggleSetCompletionUseCase = ToggleSetCompletionUseCase(activeWorkoutRepository, restTimerAlarmScheduler, timeProvider),
        finishWorkoutUseCase = FinishWorkoutUseCase(
            activeWorkoutRepository, FakeSyncScheduler(), restTimerAlarmScheduler, timeProvider,
            EnqueueGarminUploadUseCase(FakeGarminAccountRepository(), FakeGarminUploadRepository(), FakeGarminUploadScheduler(), FakeLogger()),
        ),
        discardWorkoutUseCase = DiscardWorkoutUseCase(activeWorkoutRepository, restTimerAlarmScheduler),
        restTimerAlarmScheduler = restTimerAlarmScheduler,
        networkMonitor = networkMonitor,
        idGenerator = idGenerator,
        timeProvider = timeProvider,
        ioDispatcher = testDispatcher,
    )

    private fun workout(rest: RestTimer? = null, weightText: String = "80", repsText: String = "10") = ActiveWorkout(
        sessionId = "session-1", userId = USER.id, routineId = "routine-1", routineName = "Push",
        dayName = null, startedAt = Instant.EPOCH, weightUnit = WeightUnit.KG, currentExerciseIndex = 0, rest = rest,
        exercises = listOf(
            ActiveExercise(
                rowId = 1L, position = 0, exerciseId = 1, name = "Press banca", equipment = Equipment.BARBELL,
                mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90,
                sets = listOf(ActiveSet(id = "set-1", setNumber = 1, weightText = weightText, repsText = repsText, isWarmup = false, completedAt = null)),
            ),
        ),
    )

    private fun TestScope.collectUiState(vm: WorkoutViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `restores the in-progress workout already in Room (survives process death)`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout())
        val vm = viewModel()

        collectUiState(vm)

        val state = vm.uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.workout?.sessionId).isEqualTo("session-1")
        assertThat(state.currentExercise?.name).isEqualTo("Press banca")
        assertThat(state.currentExercise?.sets?.single()?.weightText).isEqualTo("80")
    }

    @Test
    fun `weight input is persisted after the debounce window, not before`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout(weightText = ""))
        val vm = viewModel()
        collectUiState(vm)

        vm.onWeightChange("set-1", "72,5")
        advanceTimeBy(299)
        runCurrent()
        assertThat(activeWorkoutRepository.getActive(USER.id)!!.exercises.single().sets.single().weightText).isEqualTo("")

        advanceTimeBy(50)
        runCurrent()
        assertThat(activeWorkoutRepository.getActive(USER.id)!!.exercises.single().sets.single().weightText).isEqualTo("72,5")
    }

    @Test
    fun `the overlaid draft shows immediately, ahead of the debounced persist`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout(weightText = ""))
        val vm = viewModel()
        collectUiState(vm)

        vm.onWeightChange("set-1", "72,5")
        runCurrent()

        val displayedSet = vm.display(vm.uiState.value.currentExercise!!.sets.single())
        assertThat(displayedSet.weightText).isEqualTo("72,5")
    }

    @Test
    fun `rest reaching zero emits RestFinished and clears the timer`() = runTest(testDispatcher) {
        val rest = RestTimer(endsAt = Instant.ofEpochSecond(10), totalSeconds = 90)
        activeWorkoutRepository.start(workout(rest = rest))
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            timeProvider.instant = Instant.ofEpochSecond(11) // past endsAt
            advanceTimeBy(1_000) // one ticker tick, so the new instant is actually observed
            runCurrent()

            assertThat(awaitItem()).isEqualTo(WorkoutEvent.RestFinished)
        }
        assertThat(activeWorkoutRepository.getActive(USER.id)!!.rest).isNull()
    }

    @Test
    fun `onShowLastSession populates a formatted date alongside the session`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout())
        val vm = viewModel()
        collectUiState(vm)
        val lastSession = com.lucho314.spotter.domain.model.LastExerciseSession(
            sessionId = "old-session",
            date = Instant.parse("2026-01-15T10:00:00Z"),
            sets = listOf(
                com.lucho314.spotter.domain.model.WorkoutSet(
                    id = "s1", sessionId = "old-session", exerciseId = 1, exerciseName = "Press banca",
                    setNumber = 1, weightKg = 80.0, reps = 10, rpe = null, isWarmup = false,
                    completedAt = Instant.parse("2026-01-15T10:00:00Z"),
                ),
            ),
        )
        workoutHistoryRepository.lastSessionResult = com.lucho314.spotter.core.common.AppResult.Success(lastSession)

        vm.onShowLastSession(1)
        runCurrent()

        val state = vm.lastSessionState.value
        assertThat(state).isInstanceOf(LastSessionUiState.Loaded::class.java)
        val loaded = state as LastSessionUiState.Loaded
        assertThat(loaded.session).isEqualTo(lastSession)
        assertThat(loaded.dateText).isNotNull()
        assertThat(loaded.dateText).isNotEmpty()
    }

    @Test
    fun `onStop flushes an in-flight draft immediately, without waiting the debounce window`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout(weightText = ""))
        val vm = viewModel()
        collectUiState(vm)

        vm.onWeightChange("set-1", "72,5")
        vm.onStop()
        runCurrent()

        assertThat(activeWorkoutRepository.getActive(USER.id)!!.exercises.single().sets.single().weightText).isEqualTo("72,5")
    }

    @Test
    fun `onAddSet flushes an in-flight draft before copying the last set's values`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout(weightText = "80"))
        val vm = viewModel()
        collectUiState(vm)

        vm.onWeightChange("set-1", "90")
        vm.onAddSet(1L) // within the 300ms debounce window: the draft isn't persisted yet
        runCurrent()

        val sets = activeWorkoutRepository.getActive(USER.id)!!.exercises.single().sets
        assertThat(sets).hasSize(2)
        assertThat(sets[0].weightText).isEqualTo("90") // the just-typed edit was flushed first...
        assertThat(sets[1].weightText).isEqualTo("90") // ...so the new set copies it, not the stale "80"
    }

    @Test
    fun `discarding emits Discarded`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout())
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onDiscard()
            assertThat(awaitItem()).isEqualTo(WorkoutEvent.Discarded)
        }
        assertThat(activeWorkoutRepository.getActive(USER.id)).isNull()
    }

    /**
     * Review carry-over: [FinishWorkoutUseCase] reports [com.lucho314.spotter.domain.usecase.FinishResult.SessionGone]
     * (not [com.lucho314.spotter.domain.usecase.FinishResult.NothingToSave]) when the session was
     * already finished/discarded elsewhere - that must not surface as if *this* screen discarded it.
     */
    @Test
    fun `finishing a session that vanished elsewhere emits NoActiveWorkout, not Discarded`() = runTest(testDispatcher) {
        activeWorkoutRepository.start(workout())
        val vm = viewModel()
        collectUiState(vm)
        activeWorkoutRepository.useGetActiveOverride = true
        activeWorkoutRepository.getActiveOverride = null

        vm.events.test {
            vm.onFinish()
            assertThat(awaitItem()).isEqualTo(WorkoutEvent.NoActiveWorkout)
        }
    }

    /**
     * Review carry-over 3: finishing moves the session out of Room ([FinishWorkoutUseCase]'s
     * `moveToOutbox`), which - without the `closing` guard - would otherwise also fire
     * `WorkoutEvent.NoActiveWorkout` right after `Finished`, racing the nav host's relay.
     */
    @Test
    fun `finishing with a completed set emits only Finished, never NoActiveWorkout`() = runTest(testDispatcher) {
        val workout = workout().let { w ->
            w.copy(exercises = w.exercises.map { it.copy(sets = it.sets.map { set -> set.copy(completedAt = Instant.ofEpochSecond(5)) }) })
        }
        activeWorkoutRepository.start(workout)
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onFinish()
            runCurrent()
            assertThat(awaitItem()).isEqualTo(WorkoutEvent.Finished(true))

            advanceTimeBy(2_000)
            runCurrent()
            expectNoEvents()
        }
    }
}

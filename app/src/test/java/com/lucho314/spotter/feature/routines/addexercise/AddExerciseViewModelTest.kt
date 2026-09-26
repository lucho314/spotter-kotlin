package com.lucho314.spotter.feature.routines.addexercise

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeExerciseRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddExerciseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun exercise(id: Int, name: String) = Exercise(
        id = id, name = name, nameEn = name, muscleGroup = null, equipment = Equipment.BARBELL,
        imageUrl = null, mediaUrl = null, secondaryMuscles = emptyList(), instructions = emptyList(),
        difficulty = null, category = null,
    )

    private fun routineExercise(id: String, exerciseId: Int, sortOrder: Int, dayNumber: Int) = RoutineExercise(
        id = id, routineId = "r1", exerciseId = exerciseId, exercise = null, sortOrder = sortOrder,
        dayNumber = dayNumber, targetSets = 3, targetReps = 10, restSeconds = 90,
    )

    private fun routineDetail(exercises: List<RoutineExercise>) = RoutineDetail(
        id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = null,
        isArchived = false, days = emptyList(), exercises = exercises,
    )

    private fun TestScope.collectUiState(vm: AddExerciseViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun vm(
        routineId: String = "r1",
        dayNumber: Int? = null,
        exerciseRepository: FakeExerciseRepository = FakeExerciseRepository(),
        routineRepository: RoutineRepository = FakeRoutineRepository(),
    ) = AddExerciseViewModel(
        SavedStateHandle(mapOf("routineId" to routineId, "dayNumber" to dayNumber)),
        exerciseRepository,
        routineRepository,
        FakeAuthRepository(AuthState.SignedIn(user)),
    )

    @Test
    fun `search matches regardless of accents or case`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply {
            setCatalog(listOf(exercise(1, "Press Francés"), exercise(2, "Sentadilla")))
        }
        val vm = vm(exerciseRepository = exerciseRepository)
        collectUiState(vm)

        vm.onSearchQueryChange("frances")

        assertThat(vm.uiState.value.items.map { it.exercise.name }).containsExactly("Press Francés")
    }

    @Test
    fun `an exercise already on the routine is flagged as already added`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply {
            setCatalog(listOf(exercise(1, "Press Francés"), exercise(2, "Sentadilla")))
        }
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail("r1", routineDetail(listOf(routineExercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = UNASSIGNED_DAY_NUMBER))))
        }
        val vm = vm(exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        val byId = vm.uiState.value.items.associateBy { it.exercise.id }
        assertThat(byId.getValue(1).alreadyAdded).isTrue()
        assertThat(byId.getValue(2).alreadyAdded).isFalse()
    }

    @Test
    fun `sort order is the next free slot within the target day only`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply { setCatalog(listOf(exercise(3, "Curl"))) }
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail(
                "r1",
                routineDetail(
                    listOf(
                        routineExercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = 1),
                        routineExercise("re2", exerciseId = 2, sortOrder = 1, dayNumber = 1),
                        routineExercise("re3", exerciseId = 5, sortOrder = 0, dayNumber = 2),
                    ),
                ),
            )
        }
        val vm = vm(dayNumber = 1, exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        vm.onExerciseSelected(exercise(3, "Curl"))
        vm.onConfirmAdd()
        runCurrent()

        val (input, sortOrder) = routineRepository.addExerciseCalls.single()
        assertThat(input.dayNumber).isEqualTo(1)
        assertThat(sortOrder).isEqualTo(2) // day 1 already has sortOrder 0 and 1; day 2's row must not affect this.
    }

    @Test
    fun `a 23505 conflict from the repository emits a one-shot already-added event`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply { setCatalog(listOf(exercise(1, "Curl"))) }
        val routineRepository = FakeRoutineRepository().apply { addExerciseResult = AppResult.Failure(AppError.Conflict("dup")) }
        val vm = vm(exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onExerciseSelected(exercise(1, "Curl"))
            vm.onConfirmAdd()
            val event = awaitItem() as AddExerciseEvent.ActionFailed
            assertThat(event.messageRes).isEqualTo(com.lucho314.spotter.R.string.add_exercise_error_conflict)
        }
    }

    @Test
    fun `the same exercise already on a different day is not flagged as already added`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply { setCatalog(listOf(exercise(1, "Curl"))) }
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail("r1", routineDetail(listOf(routineExercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = 1))))
        }
        // Target day is 2; the existing "re1" row sits on day 1 (allowed by UNIQUE(routine, exercise, day)).
        val vm = vm(dayNumber = 2, exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.items.single().alreadyAdded).isFalse()
    }

    @Test
    fun `a catalog refresh failure with nothing cached surfaces a persistent load error`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply { refreshCatalogResult = AppResult.Failure(AppError.Network) }
        val vm = vm(exerciseRepository = exerciseRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.loadErrorRes).isEqualTo(R.string.error_network)
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `legacy dayless routine - adding via the flat list appends at day_number 1 and flags the existing one`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply {
            setCatalog(listOf(exercise(1, "Press banca"), exercise(2, "Curl")))
        }
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail(
                "r1",
                routineDetail(
                    (0..4).map { i -> routineExercise("re$i", exerciseId = 1, sortOrder = i, dayNumber = 1) },
                ),
            )
        }
        // "+" from the flat list (no real days yet) sends dayNumber = null.
        val vm = vm(dayNumber = null, exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.items.single { it.exercise.id == 1 }.alreadyAdded).isTrue()
        assertThat(vm.uiState.value.items.single { it.exercise.id == 2 }.alreadyAdded).isFalse()

        vm.onExerciseSelected(exercise(2, "Curl"))
        vm.onConfirmAdd()
        runCurrent()

        val (input, sortOrder) = routineRepository.addExerciseCalls.single()
        assertThat(input.dayNumber).isEqualTo(1)
        assertThat(sortOrder).isEqualTo(5)
    }

    @Test
    fun `routine with real days - adding to the unassigned bucket only considers 0 and orphaned exercises`() = runTest {
        val exerciseRepository = FakeExerciseRepository().apply { setCatalog(listOf(exercise(1, "On day 1"), exercise(2, "Unassigned"), exercise(3, "New"))) }
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail(
                "r1",
                RoutineDetail(
                    id = "r1", userId = "user-1", name = "Push", description = null, daysPerWeek = 1, isArchived = false,
                    days = listOf(com.lucho314.spotter.domain.model.RoutineDay("d1", "r1", 1, "Lunes")),
                    exercises = listOf(
                        routineExercise("re1", exerciseId = 1, sortOrder = 0, dayNumber = 1),
                        routineExercise("re2", exerciseId = 2, sortOrder = 2, dayNumber = UNASSIGNED_DAY_NUMBER),
                        // Orphan: no RoutineDay row has dayNumber 9 (e.g. its day was deleted).
                        routineExercise("re3", exerciseId = 4, sortOrder = 5, dayNumber = 9),
                    ),
                ),
            )
        }
        val vm = vm(dayNumber = null, exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.items.single { it.exercise.id == 1 }.alreadyAdded).isFalse()
        assertThat(vm.uiState.value.items.single { it.exercise.id == 2 }.alreadyAdded).isTrue()

        vm.onExerciseSelected(exercise(3, "New"))
        vm.onConfirmAdd()
        runCurrent()

        val (input, sortOrder) = routineRepository.addExerciseCalls.single()
        assertThat(input.dayNumber).isEqualTo(UNASSIGNED_DAY_NUMBER)
        assertThat(sortOrder).isEqualTo(6) // max(sortOrder 2, 5) + 1, ignoring the day-1 exercise's sortOrder 0.
    }

    @Test
    fun `Agregar is disabled - and onConfirmAdd is a no-op - until the routine detail has emitted once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val delegate = FakeRoutineRepository().apply {
            setRoutineDetail("r1", routineDetail(emptyList()))
        }
        // Wraps the real fake so `observeRoutine` doesn't emit until `gate` completes, simulating
        // the window between subscribing and the underlying cache/DB actually delivering a value.
        val routineRepository = object : RoutineRepository by delegate {
            override fun observeRoutine(userId: String, routineId: String): Flow<RoutineDetail?> = flow {
                gate.await()
                emitAll(delegate.observeRoutine(userId, routineId))
            }
        }
        val exerciseRepository = FakeExerciseRepository().apply { setCatalog(listOf(exercise(1, "Curl"))) }
        val vm = vm(exerciseRepository = exerciseRepository, routineRepository = routineRepository)
        collectUiState(vm)

        assertThat(vm.uiState.value.routineLoaded).isFalse()

        // A very fast tap right after opening must not add anything: the screen's own `enabled =
        // uiState.routineLoaded` should prevent this call in the UI, but onConfirmAdd() also
        // guards itself (defense in depth).
        vm.onExerciseSelected(exercise(1, "Curl"))
        vm.onConfirmAdd()
        runCurrent()
        assertThat(delegate.addExerciseCalls).isEmpty()

        gate.complete(Unit)
        runCurrent()
        assertThat(vm.uiState.value.routineLoaded).isTrue()

        vm.onConfirmAdd()
        runCurrent()
        assertThat(delegate.addExerciseCalls).hasSize(1)
    }
}

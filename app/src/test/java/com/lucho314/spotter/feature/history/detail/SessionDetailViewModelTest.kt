package com.lucho314.spotter.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ExportFormat
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.domain.usecase.BuildWorkoutExportUseCase
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.FakeWorkoutExportRepository
import com.lucho314.spotter.testutil.FakeWorkoutHistoryRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val workoutHistoryRepository = FakeWorkoutHistoryRepository()
    private val preferencesRepository = FakePreferencesRepository()
    private val timeProvider = FakeTimeProvider()
    private val workoutExportRepository = FakeWorkoutExportRepository()

    private fun viewModel() = SessionDetailViewModel(
        SavedStateHandle(mapOf("sessionId" to "s1")), workoutHistoryRepository, preferencesRepository, timeProvider,
        BuildWorkoutExportUseCase(timeProvider), workoutExportRepository,
    )

    private fun set(id: String, exerciseId: Int, exerciseName: String?, setNumber: Int, weightKg: Double = 80.0, reps: Int = 10, completedAt: Instant = Instant.EPOCH) =
        WorkoutSet(id = id, sessionId = "s1", exerciseId = exerciseId, exerciseName = exerciseName, setNumber = setNumber, weightKg = weightKg, reps = reps, rpe = null, isWarmup = false, completedAt = completedAt)

    private fun detail(sets: List<WorkoutSet>) = WorkoutSessionDetail(
        id = "s1", routineName = "Push", startedAt = Instant.EPOCH, completedAt = Instant.EPOCH.plusSeconds(3600), notes = null, sets = sets,
    )

    private fun TestScope.collectUiState(vm: SessionDetailViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `blocks are ordered by their earliest completedAt, sets by setNumber`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(
            detail(
                listOf(
                    set("a2", exerciseId = 1, exerciseName = "Press", setNumber = 2, completedAt = Instant.ofEpochSecond(20)),
                    set("a1", exerciseId = 1, exerciseName = "Press", setNumber = 1, completedAt = Instant.ofEpochSecond(10)),
                    set("b1", exerciseId = 2, exerciseName = "Squat", setNumber = 1, completedAt = Instant.ofEpochSecond(5)),
                ),
            ),
        )
        val vm = viewModel()
        collectUiState(vm)

        val blocks = vm.uiState.value.blocks
        assertThat(blocks.map { it.exerciseId }).containsExactly(2, 1).inOrder()
        assertThat(blocks.first { it.exerciseId == 1 }.sets.map { it.id }).containsExactly("a1", "a2").inOrder()
    }

    @Test
    fun `addSet uses max setNumber plus 1, copies weight and reps, and uses the session completedAt`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(
            detail(listOf(set("a1", exerciseId = 1, exerciseName = "Press", setNumber = 1, weightKg = 85.0, reps = 8))),
        )
        val vm = viewModel()
        collectUiState(vm)

        vm.onAddSet(1)
        runCurrent()

        val call = workoutHistoryRepository.addSetCalls.single()
        assertThat(call.setNumber).isEqualTo(2)
        assertThat(call.weightKg).isEqualTo(85.0)
        assertThat(call.reps).isEqualTo(8)
        assertThat(call.completedAt).isEqualTo(Instant.EPOCH.plusSeconds(3600))
    }

    @Test
    fun `addSet falls back to startedAt when the session has no completedAt`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(
            WorkoutSessionDetail(id = "s1", routineName = null, startedAt = Instant.EPOCH, completedAt = null, notes = null, sets = listOf(set("a1", 1, "Press", 1))),
        )
        val vm = viewModel()
        collectUiState(vm)

        vm.onAddSet(1)
        runCurrent()

        assertThat(workoutHistoryRepository.addSetCalls.single().completedAt).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun `editing with an invalid weight sets editErrorRes without calling the repository`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        val vm = viewModel()
        collectUiState(vm)

        vm.onEditSet("a1")
        vm.onEditConfirm("abc", "10")
        runCurrent()

        assertThat(vm.uiState.value.editErrorRes).isNotNull()
        assertThat(vm.uiState.value.editing).isNotNull()
        assertThat(workoutHistoryRepository.updateSetCalls).isEmpty()
    }

    @Test
    fun `editing in lb converts to kg`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        preferencesRepository.setWeightUnit(WeightUnit.LB)
        val vm = viewModel()
        collectUiState(vm)

        vm.onEditSet("a1")
        vm.onEditConfirm("220.46", "10")
        runCurrent()

        assertThat(workoutHistoryRepository.updateSetCalls.single().second).isWithin(0.01).of(100.0)
    }

    @Test
    fun `saving is guarded per row - a second call while pending is ignored, then the set updates`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1, weightKg = 80.0, reps = 10))))
        val gate = CompletableDeferred<Unit>()
        workoutHistoryRepository.updateSetGate = gate
        val vm = viewModel()
        collectUiState(vm)

        vm.onEditSet("a1")
        vm.onEditConfirm("90", "8")
        runCurrent()
        assertThat(vm.uiState.value.savingSetIds).containsExactly("a1")

        // Reopening the dialog for the same still-saving row and confirming again must be ignored.
        vm.onEditSet("a1")
        vm.onEditConfirm("100", "5")
        runCurrent()
        assertThat(workoutHistoryRepository.updateSetCalls).hasSize(1)

        gate.complete(Unit)
        runCurrent()

        assertThat(vm.uiState.value.savingSetIds).isEmpty()
        val updatedSet = vm.uiState.value.blocks.single().sets.single()
        assertThat(updatedSet.weightKg).isEqualTo(90.0)
        assertThat(updatedSet.reps).isEqualTo(8)
    }

    @Test
    fun `deleting a set removes it from the local detail`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        val vm = viewModel()
        collectUiState(vm)

        vm.onDeleteSet("a1")
        runCurrent()

        assertThat(vm.uiState.value.blocks).isEmpty()
    }

    @Test
    fun `a load failure sets loadErrorRes`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.loadErrorRes).isNotNull()
    }

    @Test
    fun `onExport PDF emits ShareFile and clears exporting afterwards`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        workoutExportRepository.result = AppResult.Success(com.lucho314.spotter.domain.model.ExportedFile("content://f", "application/pdf"))
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onExport(ExportFormat.PDF)
            val event = awaitItem() as SessionDetailEvent.ShareFile
            assertThat(event.uri).isEqualTo("content://f")
            assertThat(event.mimeType).isEqualTo("application/pdf")
        }
        assertThat(vm.uiState.value.exporting).isNull()
    }

    @Test
    fun `onExport failure reports ActionFailed`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        workoutExportRepository.result = AppResult.Failure(com.lucho314.spotter.core.common.AppError.Unknown())
        val vm = viewModel()
        collectUiState(vm)

        vm.events.test {
            vm.onExport(ExportFormat.PDF)
            assertThat(awaitItem()).isEqualTo(SessionDetailEvent.ActionFailed(com.lucho314.spotter.R.string.share_workout_error))
        }
    }

    @Test
    fun `a second onExport while one is in flight is ignored`() = runTest(testDispatcher) {
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        val gate = CompletableDeferred<Unit>()
        workoutExportRepository.gate = gate
        val vm = viewModel()
        collectUiState(vm)

        vm.onExport(ExportFormat.PDF)
        runCurrent()
        vm.onExport(ExportFormat.STORY)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertThat(workoutExportRepository.exported).hasSize(1)
    }

    @Test
    fun `exporting with LB preferences uses the lb unit label`() = runTest(testDispatcher) {
        preferencesRepository.setWeightUnit(WeightUnit.LB)
        workoutHistoryRepository.sessionResult = AppResult.Success(detail(listOf(set("a1", 1, "Press", 1))))
        val vm = viewModel()
        collectUiState(vm)

        vm.onExport(ExportFormat.PDF)
        runCurrent()

        assertThat(workoutExportRepository.exported.single().first.unitLabel).isEqualTo("lb")
    }
}

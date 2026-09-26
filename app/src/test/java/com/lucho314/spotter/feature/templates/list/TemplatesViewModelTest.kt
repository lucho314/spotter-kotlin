package com.lucho314.spotter.feature.templates.list

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.domain.repository.TemplateRepository
import com.lucho314.spotter.testutil.FakeTemplateRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun summary(id: String, goal: TemplateGoal = TemplateGoal.STRENGTH, daysPerWeek: Int = 3) = RoutineTemplateSummary(
        id = id,
        name = "Template $id",
        description = null,
        goal = goal,
        difficulty = Difficulty.BEGINNER,
        daysPerWeek = daysPerWeek,
    )

    @Test
    fun `loads templates with no filters on start`() = runTest {
        val repository = FakeTemplateRepository().apply {
            templatesResult = AppResult.Success(listOf(summary("1")))
        }

        val vm = TemplatesViewModel(repository)

        assertThat(vm.uiState.value.loading).isFalse()
        assertThat(vm.uiState.value.templates.map { it.id }).containsExactly("1")
        assertThat(repository.getTemplatesCalls).containsExactly(null to null)
    }

    @Test
    fun `selecting a goal re-queries with that filter`() = runTest {
        val repository = FakeTemplateRepository()
        val vm = TemplatesViewModel(repository)

        vm.onGoalFilterChanged(TemplateGoal.HYPERTROPHY)

        assertThat(vm.uiState.value.goalFilter).isEqualTo(TemplateGoal.HYPERTROPHY)
        assertThat(repository.getTemplatesCalls.last()).isEqualTo(TemplateGoal.HYPERTROPHY to null)
    }

    @Test
    fun `selecting days re-queries with that filter, independent from the goal filter`() = runTest {
        val repository = FakeTemplateRepository()
        val vm = TemplatesViewModel(repository)

        vm.onGoalFilterChanged(TemplateGoal.STRENGTH)
        vm.onDaysFilterChanged(4)

        assertThat(vm.uiState.value.daysFilter).isEqualTo(4)
        assertThat(repository.getTemplatesCalls.last()).isEqualTo(TemplateGoal.STRENGTH to 4)
    }

    @Test
    fun `a repository failure surfaces as an error message and clears loading`() = runTest {
        val repository = FakeTemplateRepository().apply {
            templatesResult = AppResult.Failure(AppError.Network)
        }

        val vm = TemplatesViewModel(repository)

        assertThat(vm.uiState.value.loading).isFalse()
        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.error_network)
        assertThat(vm.uiState.value.templates).isEmpty()
    }

    @Test
    fun `retry reloads with the current filters`() = runTest {
        val repository = FakeTemplateRepository().apply {
            templatesResult = AppResult.Failure(AppError.Network)
        }
        val vm = TemplatesViewModel(repository)
        repository.templatesResult = AppResult.Success(listOf(summary("2")))

        vm.retry()

        assertThat(vm.uiState.value.templates.map { it.id }).containsExactly("2")
        assertThat(vm.uiState.value.errorMessageRes).isNull()
    }

    @Test
    fun `changing the filter cancels a slower, still in-flight previous load`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var callCount = 0
        val repository = object : TemplateRepository {
            override suspend fun getTemplates(goal: TemplateGoal?, daysPerWeek: Int?): AppResult<List<RoutineTemplateSummary>> {
                callCount++
                return if (callCount == 1) {
                    gate.await()
                    AppResult.Success(listOf(summary("stale")))
                } else {
                    AppResult.Success(listOf(summary("fresh")))
                }
            }

            override suspend fun getTemplate(id: String): AppResult<TemplateDetail> = error("not used in this test")
        }
        val vm = TemplatesViewModel(repository)
        // The initial load (from init) is now suspended awaiting `gate`.

        vm.onGoalFilterChanged(TemplateGoal.STRENGTH) // second call: resolves immediately with "fresh".
        runCurrent()
        assertThat(vm.uiState.value.templates.map { it.id }).containsExactly("fresh")

        // Let the first call's continuation try to run. If `load()` didn't cancel it when the
        // second one started, this would overwrite the state with its stale "stale" result.
        gate.complete(Unit)
        runCurrent()

        assertThat(vm.uiState.value.templates.map { it.id }).containsExactly("fresh")
    }
}

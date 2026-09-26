package com.lucho314.spotter.feature.templates.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.navigation.TemplateDetailRoute
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.domain.usecase.AdoptTemplateUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.FakeTemplateRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun detail(dayCount: Int = 2) = TemplateDetail(
        summary = RoutineTemplateSummary(
            id = "template-1",
            name = "Fuerza total",
            description = null,
            goal = TemplateGoal.STRENGTH,
            difficulty = Difficulty.INTERMEDIATE,
            daysPerWeek = dayCount,
        ),
        days = (1..dayCount).map { dayNumber ->
            com.lucho314.spotter.domain.model.TemplateDay(
                id = "day-$dayNumber", dayNumber = dayNumber, name = "Día $dayNumber", description = null, exercises = emptyList(),
            )
        },
    )

    private fun vm(
        templateRepository: FakeTemplateRepository = FakeTemplateRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(AuthState.SignedIn(user)),
        routineRepository: FakeRoutineRepository = FakeRoutineRepository(),
    ) = TemplateDetailViewModel(
        SavedStateHandle(mapOf("templateId" to "template-1")),
        templateRepository,
        authRepository,
        AdoptTemplateUseCase(routineRepository),
    )

    @Test
    fun `loads the template detail for the id in the route`() = runTest {
        val templateRepository = FakeTemplateRepository().apply { templateDetailResult = AppResult.Success(detail()) }

        val viewModel = vm(templateRepository)

        assertThat(templateRepository.getTemplateCalls).containsExactly("template-1")
        assertThat(viewModel.uiState.value.loading).isFalse()
        assertThat(viewModel.uiState.value.template?.summary?.id).isEqualTo("template-1")
    }

    @Test
    fun `a load failure surfaces an error message`() = runTest {
        val templateRepository = FakeTemplateRepository().apply { templateDetailResult = AppResult.Failure(AppError.NotFound) }

        val viewModel = vm(templateRepository)

        assertThat(viewModel.uiState.value.template).isNull()
        assertThat(viewModel.uiState.value.errorMessageRes).isNotNull()
    }

    @Test
    fun `adopting successfully emits RoutinesCreated`() = runTest {
        val templateRepository = FakeTemplateRepository().apply { templateDetailResult = AppResult.Success(detail()) }
        val viewModel = vm(templateRepository)

        viewModel.onAdoptConfirmed()

        assertThat(viewModel.events.first()).isEqualTo(TemplateDetailEvent.RoutinesCreated)
        assertThat(viewModel.uiState.value.adopting).isFalse()
    }

    @Test
    fun `a failed adoption emits AdoptFailed with the mapped error and does not create routines`() = runTest {
        val templateRepository = FakeTemplateRepository().apply { templateDetailResult = AppResult.Success(detail()) }
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Failure(AppError.Network) }
        val viewModel = vm(templateRepository, routineRepository = routineRepository)

        viewModel.onAdoptConfirmed()

        val event = viewModel.events.first()
        assertThat(event).isInstanceOf(TemplateDetailEvent.AdoptFailed::class.java)
        assertThat(viewModel.uiState.value.adopting).isFalse()
    }
}

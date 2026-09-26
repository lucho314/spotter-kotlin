package com.lucho314.spotter.feature.routines.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.TemplateGoal
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
class RoutineEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun vm(
        routineId: String? = null,
        routineRepository: FakeRoutineRepository = FakeRoutineRepository(),
        templateRepository: FakeTemplateRepository = FakeTemplateRepository(),
    ) = RoutineEditViewModel(
        SavedStateHandle(mapOf("routineId" to routineId)),
        routineRepository,
        templateRepository,
        FakeAuthRepository(AuthState.SignedIn(user)),
    )

    private fun routineDetail(id: String) = RoutineDetail(
        id = id,
        userId = "user-1",
        name = "Push day",
        description = "Chest and triceps",
        daysPerWeek = 3,
        isArchived = false,
        days = emptyList(),
        exercises = emptyList(),
    )

    @Test
    fun `create mode shows the real template count`() = runTest {
        val templateRepository = FakeTemplateRepository().apply {
            templatesResult = AppResult.Success(
                listOf(
                    RoutineTemplateSummary("t1", "T1", null, TemplateGoal.STRENGTH, Difficulty.BEGINNER, 3),
                    RoutineTemplateSummary("t2", "T2", null, TemplateGoal.STRENGTH, Difficulty.BEGINNER, 4),
                ),
            )
        }

        val viewModel = vm(routineId = null, templateRepository = templateRepository)

        assertThat(viewModel.uiState.value.isEditing).isFalse()
        assertThat(viewModel.uiState.value.templateCount).isEqualTo(2)
    }

    @Test
    fun `saving with an empty name is rejected locally, without calling the repository`() = runTest {
        val routineRepository = FakeRoutineRepository()
        val viewModel = vm(routineRepository = routineRepository)

        viewModel.events.test {
            viewModel.onSaveClick()
            assertThat(awaitItem()).isInstanceOf(RoutineEditEvent.SaveFailed::class.java)
        }
        assertThat(routineRepository.createRoutineCalls).isEmpty()
    }

    @Test
    fun `creating a valid routine emits Saved with the new id`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Success("new-id") }
        val viewModel = vm(routineRepository = routineRepository)

        viewModel.onNameChange("Push day")
        viewModel.onSaveClick()

        assertThat(viewModel.events.first()).isEqualTo(RoutineEditEvent.Saved("new-id"))
        assertThat(routineRepository.createRoutineCalls.single().first.name).isEqualTo("Push day")
    }

    @Test
    fun `edit mode preloads the existing routine's fields`() = runTest {
        val routineRepository = FakeRoutineRepository()
        routineRepository.setRoutineDetail("r1", routineDetail("r1"))

        val viewModel = vm(routineId = "r1", routineRepository = routineRepository)

        assertThat(viewModel.uiState.value.isEditing).isTrue()
        assertThat(viewModel.uiState.value.loading).isFalse()
        assertThat(viewModel.uiState.value.name).isEqualTo("Push day")
        assertThat(viewModel.uiState.value.description).isEqualTo("Chest and triceps")
        assertThat(viewModel.uiState.value.daysPerWeek).isEqualTo(3)
    }

    @Test
    fun `saving in edit mode updates the same routine id and emits Saved with it`() = runTest {
        val routineRepository = FakeRoutineRepository()
        routineRepository.setRoutineDetail("r1", routineDetail("r1"))
        val viewModel = vm(routineId = "r1", routineRepository = routineRepository)

        viewModel.onSaveClick()

        assertThat(viewModel.events.first()).isEqualTo(RoutineEditEvent.Saved("r1"))
        assertThat(routineRepository.updateRoutineCalls.single().first).isEqualTo("r1")
    }

    @Test
    fun `a repository failure emits SaveFailed and resets saving`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Failure(AppError.Network) }
        val viewModel = vm(routineRepository = routineRepository)
        viewModel.onNameChange("Push day")

        viewModel.events.test {
            viewModel.onSaveClick()
            val event = awaitItem() as RoutineEditEvent.SaveFailed
            assertThat(event.messageRes).isEqualTo(R.string.error_network)
        }
        assertThat(viewModel.uiState.value.saving).isFalse()
    }

    @Test
    fun `saving stays true after a successful save (until the screen navigates away)`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { createRoutineResult = AppResult.Success("new-id") }
        val viewModel = vm(routineRepository = routineRepository)
        viewModel.onNameChange("Push day")

        viewModel.events.test {
            viewModel.onSaveClick()
            assertThat(awaitItem()).isEqualTo(RoutineEditEvent.Saved("new-id"))
        }
        assertThat(viewModel.uiState.value.saving).isTrue()
    }

    @Test
    fun `edit mode with no cache and a failed refresh blocks the form with a load error`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { refreshRoutineResult = AppResult.Failure(AppError.Network) }

        val viewModel = vm(routineId = "r1", routineRepository = routineRepository)

        assertThat(viewModel.uiState.value.loadErrorRes).isEqualTo(R.string.error_network)
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `edit mode falls back to stale cached data when the refresh fails but a cache exists`() = runTest {
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail("r1", routineDetail("r1"))
            refreshRoutineResult = AppResult.Failure(AppError.Network)
        }

        val viewModel = vm(routineId = "r1", routineRepository = routineRepository)

        assertThat(viewModel.uiState.value.loadErrorRes).isNull()
        assertThat(viewModel.uiState.value.name).isEqualTo("Push day")
    }

    @Test
    fun `retryLoad recovers from a load error once the repository succeeds`() = runTest {
        val routineRepository = FakeRoutineRepository().apply { refreshRoutineResult = AppResult.Failure(AppError.Network) }
        val viewModel = vm(routineId = "r1", routineRepository = routineRepository)
        assertThat(viewModel.uiState.value.loadErrorRes).isEqualTo(R.string.error_network)

        routineRepository.refreshRoutineResult = AppResult.Success(Unit)
        routineRepository.setRoutineDetail("r1", routineDetail("r1"))
        viewModel.retryLoad()

        assertThat(viewModel.uiState.value.loadErrorRes).isNull()
        assertThat(viewModel.uiState.value.name).isEqualTo("Push day")
    }
}

package com.lucho314.spotter.feature.importroutine.image

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import com.lucho314.spotter.domain.model.AiImportedRoutine
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.usecase.ImportRoutineFromImageUseCase
import com.lucho314.spotter.testutil.FakeAiImportRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeImageRepository
import com.lucho314.spotter.testutil.FakeNetworkMonitor
import com.lucho314.spotter.testutil.FakeRoutineRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportImageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher()
    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun TestScope.collectUiState(vm: ImportImageViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        imageRepository: FakeImageRepository = FakeImageRepository(),
        aiImportRepository: FakeAiImportRepository = FakeAiImportRepository(),
        routineRepository: FakeRoutineRepository = FakeRoutineRepository(),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(online = true),
    ) = ImportImageViewModel(
        savedStateHandle,
        imageRepository,
        ImportRoutineFromImageUseCase(aiImportRepository, routineRepository),
        FakeAuthRepository(AuthState.SignedIn(user)),
        networkMonitor,
        testDispatcher,
    )

    @Test
    fun `a gallery result sets imageUri in the SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val vm = viewModel(savedStateHandle)
        collectUiState(vm)

        vm.onGalleryResult("content://gallery/1")

        assertThat(vm.uiState.value.imageUri).isEqualTo("content://gallery/1")
        assertThat(savedStateHandle.get<String>("image_uri")).isEqualTo("content://gallery/1")
    }

    @Test
    fun `onCameraClick emits LaunchCamera and stores the pending uri, onCameraResult true adopts it`() = runTest {
        val imageRepository = FakeImageRepository().apply { captureUriResult = AppResult.Success("content://camera/1") }
        val vm = viewModel(imageRepository = imageRepository)
        collectUiState(vm)

        vm.events.test {
            vm.onCameraClick()
            assertThat(awaitItem()).isEqualTo(ImportImageEvent.LaunchCamera("content://camera/1"))
        }
        vm.onCameraResult(true)
        assertThat(vm.uiState.value.imageUri).isEqualTo("content://camera/1")
    }

    @Test
    fun `onCameraResult false leaves any previous image untouched`() = runTest {
        val vm = viewModel()
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.onCameraResult(false)

        assertThat(vm.uiState.value.imageUri).isEqualTo("content://gallery/1")
    }

    @Test
    fun `offline shows OFFLINE without encoding`() = runTest {
        val imageRepository = FakeImageRepository()
        val networkMonitor = FakeNetworkMonitor(online = false)
        val vm = viewModel(imageRepository = imageRepository, networkMonitor = networkMonitor)
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportImageEvent.ShowError(AiImportErrorKind.OFFLINE))
        }
        assertThat(imageRepository.encodeCalls).isEmpty()
    }

    @Test
    fun `an unreadable image reports IMAGE_UNREADABLE`() = runTest {
        val imageRepository = FakeImageRepository().apply {
            encodeResult = AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
        }
        val vm = viewModel(imageRepository = imageRepository)
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportImageEvent.ShowError(AiImportErrorKind.IMAGE_UNREADABLE))
        }
    }

    @Test
    fun `a Server TIMEOUT from the AI repository shows TIMEOUT_MAYBE_CREATED`() = runTest {
        val aiImportRepository = FakeAiImportRepository().apply { result = AppResult.Failure(AppError.Server(AiImportErrorCodes.TIMEOUT)) }
        val vm = viewModel(aiImportRepository = aiImportRepository)
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportImageEvent.ShowError(AiImportErrorKind.TIMEOUT_MAYBE_CREATED))
        }
    }

    @Test
    fun `a Server REJECTED shows NOT_RECOGNIZED`() = runTest {
        val aiImportRepository = FakeAiImportRepository().apply { result = AppResult.Failure(AppError.Server(AiImportErrorCodes.REJECTED)) }
        val vm = viewModel(aiImportRepository = aiImportRepository)
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportImageEvent.ShowError(AiImportErrorKind.NOT_RECOGNIZED))
        }
    }

    @Test
    fun `success emits Imported with id and name`() = runTest {
        val aiImportRepository = FakeAiImportRepository().apply {
            result = AppResult.Success(AiImportedRoutine("550e8400-e29b-41d4-a716-446655440000", "Push"))
        }
        val routineRepository = FakeRoutineRepository().apply {
            setRoutineDetail(
                "550e8400-e29b-41d4-a716-446655440000",
                com.lucho314.spotter.domain.model.RoutineDetail(
                    id = "550e8400-e29b-41d4-a716-446655440000", userId = "user-1", name = "Push", description = null,
                    daysPerWeek = null, isArchived = false, days = emptyList(), exercises = emptyList(),
                ),
            )
        }
        val vm = viewModel(aiImportRepository = aiImportRepository, routineRepository = routineRepository)
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.events.test {
            vm.onImportClick()
            assertThat(awaitItem()).isEqualTo(ImportImageEvent.Imported("550e8400-e29b-41d4-a716-446655440000", "Push"))
        }
    }

    @Test
    fun `a double tap only encodes once`() = runTest {
        val imageRepository = FakeImageRepository()
        val vm = viewModel(imageRepository = imageRepository)
        collectUiState(vm)
        vm.onGalleryResult("content://gallery/1")

        vm.onImportClick()
        vm.onImportClick()
        runCurrent()

        assertThat(imageRepository.encodeCalls).hasSize(1)
    }

    @Test
    fun `restoring from a SavedStateHandle with a pre-set image_uri exposes it`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("image_uri" to "content://x"))
        val vm = viewModel(savedStateHandle)
        collectUiState(vm)

        assertThat(vm.uiState.value.imageUri).isEqualTo("content://x")
    }
}

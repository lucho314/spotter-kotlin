package com.lucho314.spotter.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.usecase.EnqueueGarminUploadUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import com.lucho314.spotter.testutil.FakeLogger
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)
private const val SESSION_ID = "session-1"

@OptIn(ExperimentalCoroutinesApi::class)
class GarminSessionUploadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val garminAccountRepository = FakeGarminAccountRepository()
    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()

    private fun viewModel() = GarminSessionUploadViewModel(
        SavedStateHandle(mapOf(RouteArgs.SESSION_ID to SESSION_ID)),
        authRepository, garminAccountRepository, garminUploadRepository,
        EnqueueGarminUploadUseCase(garminAccountRepository, garminUploadRepository, garminUploadScheduler, FakeLogger()),
    )

    private fun TestScope.collectState(vm: GarminSessionUploadViewModel) {
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
    }

    @Test
    fun `not connected is HIDDEN`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.NotConnected
        val vm = viewModel()
        collectState(vm)

        assertThat(vm.state.value).isEqualTo(GarminSessionAction.HIDDEN)
    }

    @Test
    fun `connected with no row is AVAILABLE`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        val vm = viewModel()
        collectState(vm)

        assertThat(vm.state.value).isEqualTo(GarminSessionAction.AVAILABLE)
    }

    @Test
    fun `connected with a FAILED row is also AVAILABLE (can retry)`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed(SESSION_ID, USER.id, GarminUploadStatus.FAILED)
        val vm = viewModel()
        collectState(vm)

        assertThat(vm.state.value).isEqualTo(GarminSessionAction.AVAILABLE)
    }

    @Test
    fun `PENDING row reports PENDING`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed(SESSION_ID, USER.id, GarminUploadStatus.PENDING)
        val vm = viewModel()
        collectState(vm)

        assertThat(vm.state.value).isEqualTo(GarminSessionAction.PENDING)
    }

    @Test
    fun `UPLOADED row reports UPLOADED`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed(SESSION_ID, USER.id, GarminUploadStatus.UPLOADED)
        val vm = viewModel()
        collectState(vm)

        assertThat(vm.state.value).isEqualTo(GarminSessionAction.UPLOADED)
    }

    @Test
    fun `clicking upload on a fresh session sends the queued message`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        val vm = viewModel()
        collectState(vm)

        vm.events.test {
            vm.onUploadClick()
            assertThat((awaitItem() as GarminSessionUploadEvent.Message).messageRes).isEqualTo(R.string.garmin_upload_queued)
        }
    }

    @Test
    fun `clicking upload on an already-uploaded session sends the already-uploaded message`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed(SESSION_ID, USER.id, GarminUploadStatus.UPLOADED)
        val vm = viewModel()
        collectState(vm)

        vm.events.test {
            vm.onUploadClick()
            assertThat((awaitItem() as GarminSessionUploadEvent.Message).messageRes).isEqualTo(R.string.garmin_upload_already)
        }
    }

    @Test
    fun `a failure enqueueing reports the mapped error message`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.NotConnected
        val vm = viewModel()
        collectState(vm)

        vm.events.test {
            vm.onUploadClick()
            assertThat((awaitItem() as GarminSessionUploadEvent.Message).messageRes).isEqualTo(R.string.garmin_error_not_connected)
        }
    }
}

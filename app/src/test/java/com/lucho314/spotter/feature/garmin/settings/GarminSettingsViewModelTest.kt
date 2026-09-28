package com.lucho314.spotter.feature.garmin.settings

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.usecase.DisconnectGarminUseCase
import com.lucho314.spotter.domain.usecase.RetryFailedGarminUploadsUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

@OptIn(ExperimentalCoroutinesApi::class)
class GarminSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val garminAccountRepository = FakeGarminAccountRepository()
    private val garminUploadRepository = FakeGarminUploadRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()

    private fun viewModel() = GarminSettingsViewModel(
        authRepository, garminAccountRepository, garminUploadRepository,
        DisconnectGarminUseCase(garminUploadScheduler, garminUploadRepository, garminAccountRepository),
        RetryFailedGarminUploadsUseCase(garminUploadRepository, garminUploadScheduler),
    )

    private fun TestScope.collectUiState(vm: GarminSettingsViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `maps the connection state and the failed count`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        garminUploadRepository.seed("w1", USER.id, GarminUploadStatus.FAILED)
        val vm = viewModel()
        collectUiState(vm)

        assertThat(vm.uiState.value.connection).isEqualTo(GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false))
        assertThat(vm.uiState.value.failedCount).isEqualTo(1)
    }

    @Test
    fun `toggling auto-upload calls setAutoUpload`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        val vm = viewModel()
        collectUiState(vm)

        vm.onAutoUploadChange(false)
        runCurrent()

        assertThat(garminAccountRepository.setAutoUploadCalls).containsExactly(USER.id to false)
    }

    @Test
    fun `the disconnect flow shows a prompt, then disconnects on confirm`() = runTest {
        garminAccountRepository.connection.value = GarminConnectionState.Connected("Ada", autoUpload = true, needsReconnect = false)
        val vm = viewModel()
        collectUiState(vm)

        vm.onDisconnectClick()
        assertThat(vm.uiState.value.disconnectPrompt).isTrue()

        vm.onDisconnectConfirmed()
        runCurrent()

        assertThat(vm.uiState.value.disconnectPrompt).isFalse()
        assertThat(garminAccountRepository.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `dismissing the disconnect prompt does not disconnect`() = runTest {
        val vm = viewModel()
        collectUiState(vm)

        vm.onDisconnectClick()
        vm.onDisconnectDismiss()

        assertThat(vm.uiState.value.disconnectPrompt).isFalse()
        assertThat(garminAccountRepository.disconnectCallCount).isEqualTo(0)
    }

    @Test
    fun `retrying failed uploads resets them and schedules the worker`() = runTest {
        garminUploadRepository.seed("w1", USER.id, GarminUploadStatus.FAILED)
        val vm = viewModel()
        collectUiState(vm)

        vm.onRetryFailed()
        runCurrent()

        assertThat(garminUploadRepository.rows["w1"]!!.status).isEqualTo(GarminUploadStatus.PENDING)
        assertThat(garminUploadScheduler.scheduleCallCount).isEqualTo(1)
    }
}

package com.lucho314.spotter.feature.garmin.connect

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.usecase.ConnectGarminUseCase
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeGarminAccountRepository
import com.lucho314.spotter.testutil.FakeGarminUploadScheduler
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val USER = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

@OptIn(ExperimentalCoroutinesApi::class)
class GarminConnectViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository(AuthState.SignedIn(USER))
    private val garminAccountRepository = FakeGarminAccountRepository()
    private val garminUploadScheduler = FakeGarminUploadScheduler()

    private fun viewModel() = GarminConnectViewModel(authRepository, ConnectGarminUseCase(garminAccountRepository, garminUploadScheduler))

    @Test
    fun `a blank or invalid email reports the validation error without calling login`() = runTest {
        val vm = viewModel()
        vm.onEmailChange("not-an-email")
        vm.onPasswordChange("pw")

        vm.onSubmitCredentials()

        assertThat(vm.uiState.value.errorRes).isEqualTo(R.string.garmin_error_email_invalid)
        assertThat(garminAccountRepository.loginCalls).isEmpty()
    }

    @Test
    fun `a blank password reports the validation error`() = runTest {
        val vm = viewModel()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("  ")

        vm.onSubmitCredentials()

        assertThat(vm.uiState.value.errorRes).isEqualTo(R.string.garmin_error_password_empty)
        assertThat(garminAccountRepository.loginCalls).isEmpty()
    }

    @Test
    fun `a short MFA code reports the validation error`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.MfaRequired("c1", "email"))
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("pw")
        vm.onSubmitCredentials()

        vm.onMfaCodeChange("12")
        vm.onSubmitMfa()

        assertThat(vm.uiState.value.errorRes).isEqualTo(R.string.garmin_error_mfa_empty)
    }

    @Test
    fun `a successful direct login emits Connected`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.Connected)
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("pw")

        vm.events.test {
            vm.onSubmitCredentials()
            assertThat(awaitItem()).isEqualTo(GarminConnectEvent.Connected)
        }
    }

    @Test
    fun `MFA required moves to the Mfa step`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.MfaRequired("challenge-1", "sms"))
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("pw")

        vm.onSubmitCredentials()

        val step = vm.uiState.value.step as GarminConnectStep.Mfa
        assertThat(step.challengeId).isEqualTo("challenge-1")
        assertThat(step.method).isEqualTo("sms")
    }

    @Test
    fun `an invalid MFA code stays on the Mfa step`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.MfaRequired("challenge-1", "email"))
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("pw")
        vm.onSubmitCredentials()

        garminAccountRepository.verifyResult = GarminResult.Failure(GarminError.InvalidMfaCode)
        vm.onMfaCodeChange("000000")
        vm.onSubmitMfa()

        assertThat(vm.uiState.value.step).isInstanceOf(GarminConnectStep.Mfa::class.java)
        assertThat(vm.uiState.value.errorRes).isEqualTo(R.string.garmin_error_invalid_mfa)
    }

    @Test
    fun `an expired MFA session goes back to Credentials`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.MfaRequired("challenge-1", "email"))
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("pw")
        vm.onSubmitCredentials()

        garminAccountRepository.verifyResult = GarminResult.Failure(GarminError.MfaSessionExpired)
        vm.onMfaCodeChange("123456")
        vm.onSubmitMfa()

        assertThat(vm.uiState.value.step).isEqualTo(GarminConnectStep.Credentials)
        assertThat(vm.uiState.value.errorRes).isEqualTo(R.string.garmin_error_mfa_expired)
    }

    @Test
    fun `a double submit while already submitting is ignored`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.Connected)
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("pw")

        vm.onSubmitCredentials()
        vm.onSubmitCredentials()

        assertThat(garminAccountRepository.loginCalls).hasSize(1)
    }

    @Test
    fun `the password is cleared after a successful submit`() = runTest {
        val vm = viewModel()
        garminAccountRepository.loginResult = GarminResult.Success(GarminLoginResult.Connected)
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("super-secret")

        vm.onSubmitCredentials()

        assertThat(vm.uiState.value.password).isEmpty()
    }
}

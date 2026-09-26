package com.lucho314.spotter.feature.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.config.AppConfig
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import com.lucho314.spotter.testutil.networkFailure
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val nonceGenerator = NonceGenerator(SecureRandom())

    private fun viewModel(
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        googleWebClientId: String? = "web-client-id",
    ) = LoginViewModel(
        authRepository = authRepository,
        appConfig = AppConfig(supabaseUrl = "https://example.test", supabaseAnonKey = "key", googleWebClientId = googleWebClientId),
        nonceGenerator = nonceGenerator,
    )

    @Test
    fun `with a client id, clicking Google emits LaunchCredentialRequest`() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onGoogleClick()
            val event = awaitItem()
            assertThat(event).isInstanceOf(LoginEvent.LaunchCredentialRequest::class.java)
        }
        assertThat(vm.uiState.value.loading).isTrue()
    }

    @Test
    fun `without a client id, clicking Google starts OAuth directly`() = runTest {
        val authRepository = FakeAuthRepository()
        val vm = viewModel(authRepository, googleWebClientId = null)

        vm.onGoogleClick()

        assertThat(authRepository.startGoogleOAuthCallCount).isEqualTo(1)
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `Success calls signInWithGoogleIdToken with the raw nonce`() = runTest {
        val authRepository = FakeAuthRepository()
        val vm = viewModel(authRepository)

        vm.events.test {
            vm.onGoogleClick()
            val event = awaitItem() as LoginEvent.LaunchCredentialRequest
            vm.onCredentialResult(GoogleIdResult.Success(idToken = "id-token-1"))

            assertThat(authRepository.signInWithIdTokenCalls).hasSize(1)
            val (idToken, rawNonce) = authRepository.signInWithIdTokenCalls.single()
            assertThat(idToken).isEqualTo("id-token-1")
            assertThat(NonceGenerator.sha256Hex(rawNonce)).isEqualTo(event.hashedNonce)
        }
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `NoCredential falls back to OAuth`() = runTest {
        val authRepository = FakeAuthRepository()
        val vm = viewModel(authRepository)

        vm.onGoogleClick()
        vm.onCredentialResult(GoogleIdResult.NoCredential)

        assertThat(authRepository.startGoogleOAuthCallCount).isEqualTo(1)
        assertThat(authRepository.signInWithIdTokenCalls).isEmpty()
    }

    @Test
    fun `Failure falls back to OAuth`() = runTest {
        val authRepository = FakeAuthRepository()
        val vm = viewModel(authRepository)

        vm.onGoogleClick()
        vm.onCredentialResult(GoogleIdResult.Failure("boom"))

        assertThat(authRepository.startGoogleOAuthCallCount).isEqualTo(1)
    }

    @Test
    fun `Cancelled does nothing`() = runTest {
        val authRepository = FakeAuthRepository()
        val vm = viewModel(authRepository)

        vm.onGoogleClick()
        vm.onCredentialResult(GoogleIdResult.Cancelled)

        assertThat(authRepository.startGoogleOAuthCallCount).isEqualTo(0)
        assertThat(authRepository.signInWithIdTokenCalls).isEmpty()
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `a repository failure surfaces an error message`() = runTest {
        val authRepository = FakeAuthRepository().apply {
            startGoogleOAuthResult = networkFailure()
        }
        val vm = viewModel(authRepository, googleWebClientId = null)

        vm.onGoogleClick()

        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.error_network)
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `an Unauthorized sign-in failure uses the login-specific message, not the expired-session one`() = runTest {
        val authRepository = FakeAuthRepository().apply {
            startGoogleOAuthResult = AppResult.Failure(AppError.Unauthorized)
        }
        val vm = viewModel(authRepository, googleWebClientId = null)

        vm.onGoogleClick()

        assertThat(vm.uiState.value.errorMessageRes).isEqualTo(R.string.error_login_failed)
        assertThat(vm.uiState.value.errorMessageRes).isNotEqualTo(R.string.error_unauthorized)
    }
}

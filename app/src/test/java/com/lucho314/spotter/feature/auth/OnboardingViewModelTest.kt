package com.lucho314.spotter.feature.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    @Test
    fun `onStartClick marks onboarding done for the current user and emits Done`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val preferencesRepository = FakePreferencesRepository()
        val vm = OnboardingViewModel(authRepository, preferencesRepository)

        vm.events.test {
            vm.onStartClick()
            assertThat(awaitItem()).isEqualTo(OnboardingEvent.Done)
        }
        assertThat(preferencesRepository.isOnboardingDone(user.id).value).isTrue()
    }

    @Test
    fun `onStartClick with no signed-in user still emits Done`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedOut)
        val vm = OnboardingViewModel(authRepository, FakePreferencesRepository())

        vm.events.test {
            vm.onStartClick()
            assertThat(awaitItem()).isEqualTo(OnboardingEvent.Done)
        }
    }
}

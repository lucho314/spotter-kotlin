package com.lucho314.spotter.feature.root

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.config.AppConfig
import com.lucho314.spotter.core.navigation.DeepLink
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.AuthUser
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.usecase.RescheduleRestAlarmUseCase
import com.lucho314.spotter.testutil.CountingLazy
import com.lucho314.spotter.testutil.FakeActiveWorkoutRepository
import com.lucho314.spotter.testutil.FakeAuthRepository
import com.lucho314.spotter.testutil.FakeNetworkMonitor
import com.lucho314.spotter.testutil.FakePreferencesRepository
import com.lucho314.spotter.testutil.FakeRestTimerAlarmScheduler
import com.lucho314.spotter.testutil.FakeSyncScheduler
import com.lucho314.spotter.testutil.FakeTimeProvider
import com.lucho314.spotter.testutil.MainDispatcherRule
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val validConfig = AppConfig(supabaseUrl = "https://example.test", supabaseAnonKey = "key", googleWebClientId = null)
    private val invalidConfig = AppConfig(supabaseUrl = "", supabaseAnonKey = "", googleWebClientId = null)

    private val user = AuthUser(id = "user-1", email = "a@b.com", displayName = "Ada", avatarUrl = null)

    private fun lazyOf(repository: AuthRepository): Lazy<AuthRepository> = Lazy { repository }

    private fun rootViewModel(
        config: AppConfig,
        authRepositoryLazy: Lazy<AuthRepository>,
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        syncScheduler: FakeSyncScheduler = FakeSyncScheduler(),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(),
        rescheduleRestAlarmUseCase: RescheduleRestAlarmUseCase = RescheduleRestAlarmUseCase(
            FakeActiveWorkoutRepository(), FakeRestTimerAlarmScheduler(), FakeTimeProvider(),
        ),
    ) = RootViewModel(config, authRepositoryLazy, preferencesRepository, syncScheduler, rescheduleRestAlarmUseCase, networkMonitor)

    /**
     * [RootViewModel.uiState] is a `WhileSubscribed` StateFlow: it needs an active collector to
     * run. `backgroundScope` (unlike a plain `launch`) is cancelled automatically by `runTest`.
     */
    private fun TestScope.collectUiState(vm: RootViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `invalid config always reports ConfigError`() = runTest {
        val vm = rootViewModel(invalidConfig, lazyOf(FakeAuthRepository(AuthState.SignedIn(user))))

        collectUiState(vm)

        assertThat(vm.uiState.value).isEqualTo(RootUiState.ConfigError)
    }

    @Test
    fun `invalid config never accesses the auth repository`() = runTest {
        val countingLazy = CountingLazy<AuthRepository> { FakeAuthRepository(AuthState.SignedIn(user)) }

        val vm = rootViewModel(invalidConfig, countingLazy)
        collectUiState(vm)
        // Reading uiState.value again to make sure nothing lazily triggers access later either.
        assertThat(vm.uiState.value).isEqualTo(RootUiState.ConfigError)

        assertThat(countingLazy.getCallCount).isEqualTo(0)
    }

    @Test
    fun `isOnline mirrors the network monitor, for a global offline banner`() = runTest {
        val networkMonitor = FakeNetworkMonitor(online = true)
        val vm = rootViewModel(validConfig, lazyOf(FakeAuthRepository(AuthState.SignedIn(user))), networkMonitor = networkMonitor)
        val collected = mutableListOf<Boolean>()
        val job = launch { vm.isOnline.collect { collected += it } }
        runCurrent()

        networkMonitor.onlineFlow.value = false
        runCurrent()

        assertThat(collected).containsExactly(true, false).inOrder()
        job.cancel()
    }

    @Test
    fun `SignedOut auth state maps to SignedOut ui state`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedOut)
        val vm = rootViewModel(validConfig, lazyOf(authRepository))

        collectUiState(vm)

        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedOut)
    }

    @Test
    fun `SignedIn without onboarding done needs onboarding`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val vm = rootViewModel(validConfig, lazyOf(authRepository))

        collectUiState(vm)

        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedIn(needsOnboarding = true))
    }

    @Test
    fun `SignedIn after onboarding done does not need onboarding`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val prefs = FakePreferencesRepository()
        prefs.setOnboardingDone(user.id)
        val vm = rootViewModel(validConfig, lazyOf(authRepository), prefs)

        collectUiState(vm)

        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedIn(needsOnboarding = false))
    }

    @Test
    fun `a transient Loading after sign-in keeps the app mounted (backgrounding the app)`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val preferences = FakePreferencesRepository().apply { setOnboardingDone(user.id) }
        val vm = rootViewModel(validConfig, lazyOf(authRepository), preferencesRepository = preferences)
        collectUiState(vm)
        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedIn(needsOnboarding = false))

        // What supabase-kt does on every app background/foreground cycle.
        authRepository.state.value = AuthState.Loading
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedIn(needsOnboarding = false))

        authRepository.state.value = AuthState.SignedIn(user)
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedIn(needsOnboarding = false))
    }

    @Test
    fun `a real sign-out still goes through after the app was signed in`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val vm = rootViewModel(validConfig, lazyOf(authRepository))
        collectUiState(vm)

        authRepository.state.value = AuthState.SignedOut
        runCurrent()

        assertThat(vm.uiState.value).isEqualTo(RootUiState.SignedOut)
    }

    @Test
    fun `Loading is shown until auth resolves for the first time`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.Loading)
        val vm = rootViewModel(validConfig, lazyOf(authRepository))
        collectUiState(vm)

        assertThat(vm.uiState.value).isEqualTo(RootUiState.Loading)
    }

    @Test
    fun `signing in syncs the display name once per session`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedOut)
        val vm = rootViewModel(validConfig, lazyOf(authRepository))
        collectUiState(vm)

        authRepository.state.value = AuthState.SignedIn(user)
        runCurrent()
        authRepository.state.value = AuthState.SignedIn(user) // same user again: no extra sync
        runCurrent()

        assertThat(authRepository.syncProfileDisplayNameCalls).hasSize(1)
    }

    @Test
    fun `signing out then back in as a different user syncs again`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val vm = rootViewModel(validConfig, lazyOf(authRepository))
        collectUiState(vm)
        runCurrent()

        authRepository.state.value = AuthState.SignedOut
        runCurrent()
        val otherUser = user.copy(id = "user-2")
        authRepository.state.value = AuthState.SignedIn(otherUser)
        runCurrent()

        assertThat(authRepository.syncProfileDisplayNameCalls.map { it.id }).containsExactly(user.id, otherUser.id)
    }

    @Test
    fun `signing in schedules the pending-workouts sync worker once per session`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedOut)
        val syncScheduler = FakeSyncScheduler()
        val vm = rootViewModel(validConfig, lazyOf(authRepository), syncScheduler = syncScheduler)
        collectUiState(vm)

        authRepository.state.value = AuthState.SignedIn(user)
        runCurrent()
        authRepository.state.value = AuthState.SignedIn(user) // same user again: no extra schedule
        runCurrent()

        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `a pending deep link is exposed once and cleared after consumeDeepLink`() = runTest {
        val vm = rootViewModel(validConfig, lazyOf(FakeAuthRepository(AuthState.SignedOut)))
        val code = requireNotNull(ShareCode.parse("K7MN3QXP"))

        assertThat(vm.pendingImportCode.value).isNull()

        vm.onDeepLink(DeepLink.ImportRoutine(code))
        assertThat(vm.pendingImportCode.value).isEqualTo(code)

        vm.consumeDeepLink()
        assertThat(vm.pendingImportCode.value).isNull()
    }

    @Test
    fun `a pending deep link does not survive a sign-out`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val vm = rootViewModel(validConfig, lazyOf(authRepository))
        collectUiState(vm)
        val code = requireNotNull(ShareCode.parse("K7MN3QXP"))
        vm.onDeepLink(DeepLink.ImportRoutine(code))
        assertThat(vm.pendingImportCode.value).isEqualTo(code)

        authRepository.state.value = AuthState.SignedOut
        runCurrent()

        assertThat(vm.pendingImportCode.value).isNull()
    }

    @Test
    fun `an auth callback deep link is ignored by onDeepLink`() = runTest {
        val vm = rootViewModel(validConfig, lazyOf(FakeAuthRepository(AuthState.SignedOut)))

        vm.onDeepLink(DeepLink.AuthCallback)

        assertThat(vm.pendingImportCode.value).isNull()
    }

    @Test
    fun `a pending open-workout request is exposed once and cleared after consumeOpenWorkout`() = runTest {
        val vm = rootViewModel(validConfig, lazyOf(FakeAuthRepository(AuthState.SignedOut)))

        assertThat(vm.pendingOpenWorkout.value).isFalse()

        vm.onOpenWorkoutRequested()
        assertThat(vm.pendingOpenWorkout.value).isTrue()

        vm.consumeOpenWorkout()
        assertThat(vm.pendingOpenWorkout.value).isFalse()
    }

    @Test
    fun `a pending open-workout request does not survive a sign-out`() = runTest {
        val authRepository = FakeAuthRepository(AuthState.SignedIn(user))
        val vm = rootViewModel(validConfig, lazyOf(authRepository))
        collectUiState(vm)
        vm.onOpenWorkoutRequested()
        assertThat(vm.pendingOpenWorkout.value).isTrue()

        authRepository.state.value = AuthState.SignedOut
        runCurrent()

        assertThat(vm.pendingOpenWorkout.value).isFalse()
    }
}

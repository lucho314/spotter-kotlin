package com.lucho314.spotter.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.config.AppConfig
import com.lucho314.spotter.core.navigation.DeepLink
import com.lucho314.spotter.core.work.SyncScheduler
import com.lucho314.spotter.domain.model.AuthState
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Drives [com.lucho314.spotter.feature.root.SpotterRoot]'s top-level decision (splash/login/app). */
sealed interface RootUiState {
    data object Loading : RootUiState
    data object ConfigError : RootUiState
    data object SignedOut : RootUiState
    data class SignedIn(val needsOnboarding: Boolean) : RootUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    private val appConfig: AppConfig,
    // Lazy: merely creating RootViewModel (which always happens, to decide ConfigError vs. the
    // rest) must not construct AuthRepository -> Auth -> SupabaseClient when config is invalid.
    private val authRepositoryLazy: Lazy<AuthRepository>,
    private val preferencesRepository: PreferencesRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _pendingImportCode = MutableStateFlow<ShareCode?>(null)

    /** Import deep link received before/while the nav host wasn't ready to consume it yet. */
    val pendingImportCode: StateFlow<ShareCode?> = _pendingImportCode.asStateFlow()

    private val _pendingOpenWorkout = MutableStateFlow(false)

    /** Set when the user taps the "Descanso terminado" notification (see [com.lucho314.spotter.core.notifications.RestTimerReceiver]) before the nav host is ready to consume it. */
    val pendingOpenWorkout: StateFlow<Boolean> = _pendingOpenWorkout.asStateFlow()

    private var lastSyncedUserId: String? = null

    val uiState: StateFlow<RootUiState> = if (!appConfig.isValid) {
        MutableStateFlow(RootUiState.ConfigError)
    } else {
        authRepositoryLazy.get().authState
            .flatMapLatest { state ->
                when (state) {
                    AuthState.Loading -> flowOf(RootUiState.Loading)
                    AuthState.SignedOut -> flowOf(RootUiState.SignedOut)
                    is AuthState.SignedIn -> preferencesRepository.isOnboardingDone(state.user.id)
                        .map { done -> RootUiState.SignedIn(needsOnboarding = !done) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState.Loading)
    }

    init {
        if (appConfig.isValid) {
            // Once per session: sync the display name (in case it changed at the provider) and
            // kick off the pending-workouts sync worker (RN bug #3, section 7: the RN app only
            // synced on the offline-to-online *transition*, never on a plain sign-in/app open with
            // a queue already waiting). Also resets per-session bookkeeping and any stale deep link
            // when signing out, so the next account on this device starts clean.
            authRepositoryLazy.get().authState
                .onEach { state ->
                    when (state) {
                        is AuthState.SignedIn -> {
                            if (lastSyncedUserId != state.user.id) {
                                lastSyncedUserId = state.user.id
                                authRepositoryLazy.get().syncProfileDisplayName(state.user)
                                syncScheduler.schedule()
                            }
                        }

                        AuthState.SignedOut -> {
                            lastSyncedUserId = null
                            _pendingImportCode.value = null
                            _pendingOpenWorkout.value = false
                        }

                        AuthState.Loading -> Unit
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    /** Called from [com.lucho314.spotter.MainActivity] when a `spotter://import/{code}` link arrives. */
    fun onDeepLink(link: DeepLink) {
        if (link is DeepLink.ImportRoutine) {
            _pendingImportCode.value = link.code
        }
    }

    /** Called once the pending import code has been consumed (navigated to) by the nav host. */
    fun consumeDeepLink() {
        _pendingImportCode.value = null
    }

    /** Called from [com.lucho314.spotter.MainActivity] when the rest-finished notification is tapped. */
    fun onOpenWorkoutRequested() {
        _pendingOpenWorkout.value = true
    }

    /** Called once the pending "open workout" request has been consumed (navigated to) by the nav host. */
    fun consumeOpenWorkout() {
        _pendingOpenWorkout.value = false
    }
}

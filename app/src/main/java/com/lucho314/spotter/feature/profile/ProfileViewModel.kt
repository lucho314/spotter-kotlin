package com.lucho314.spotter.feature.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.core.work.SyncScheduler
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Minimal profile screen for phase 1 (just identity + sign-out). Stats, physical data and the
 * weight unit preference are added in a later phase.
 *
 * [displayName] is null when unknown; the UI resolves the fallback (`R.string.onboarding_default_name`).
 */
data class ProfileUiState(
    val displayName: String? = null,
    val email: String? = null,
    val signingOut: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        authRepository.currentUser().let { user ->
            ProfileUiState(displayName = user?.displayName, email = user?.email)
        },
    )
    val uiState = _uiState.asStateFlow()

    /**
     * Full Room/per-user-preferences cleanup is `SignOutUseCase`'s job (migration plan section
     * 9.5, phase 5) - but the rest-timer alarm and the sync worker are cancelled here already
     * (review carry-over: "at minimum cancel alarms/work on sign-out"), since leaving either
     * running for a session that's about to sign out is a concrete, observable bug on its own: a
     * stale alarm could fire "Descanso terminado" for the *next* account on this device, and the
     * sync worker could try to upload an outbox it doesn't own once `SignOutUseCase` clears Room
     * out from under it.
     */
    fun onSignOutConfirmed() {
        _uiState.update { it.copy(signingOut = true, errorMessageRes = null) }
        restTimerAlarmScheduler.cancel()
        syncScheduler.cancel()
        viewModelScope.launch {
            // AuthRepositoryImpl already makes a best-effort attempt to force the local session to
            // signed-out even when the server call fails (see its signOut() for the rare case
            // where that forced clear itself throws too). A Failure here mostly means the server
            // wasn't notified; surfaced so the user isn't left wondering why the sync looked off.
            when (val result = authRepository.signOut()) {
                is AppResult.Success -> _uiState.update { it.copy(signingOut = false) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(signingOut = false, errorMessageRes = result.error.toMessageRes())
                }
            }
        }
    }
}

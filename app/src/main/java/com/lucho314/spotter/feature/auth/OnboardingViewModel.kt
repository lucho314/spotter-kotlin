package com.lucho314.spotter.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** [displayName] is null when unknown; the UI resolves the fallback (`R.string.onboarding_default_name`). */
data class OnboardingUiState(val displayName: String? = null)

/** One-shot effect: onboarding is done, navigate to the dashboard. */
sealed interface OnboardingEvent {
    data object Done : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val currentUser = authRepository.currentUser()

    private val _uiState = MutableStateFlow(OnboardingUiState(displayName = currentUser?.displayName))
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = eventChannel.receiveAsFlow()

    /**
     * Marks onboarding as done for the current user, then emits [OnboardingEvent.Done] for the
     * screen to navigate on (review carry-over: this used to take an `onDone: () -> Unit`
     * composition lambda and invoke it directly from inside `viewModelScope.launch` - a lambda
     * captured from a *previous* composition that can be stale after a configuration change, the
     * same class of bug `ObserveAsEvents` exists to avoid for every other screen's async results).
     */
    fun onStartClick() {
        viewModelScope.launch {
            currentUser?.id?.let { preferencesRepository.setOnboardingDone(it) }
            eventChannel.send(OnboardingEvent.Done)
        }
    }
}

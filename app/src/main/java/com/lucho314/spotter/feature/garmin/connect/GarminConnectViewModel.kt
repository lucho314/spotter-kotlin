package com.lucho314.spotter.feature.garmin.connect

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminLoginResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.usecase.ConnectGarminUseCase
import com.lucho314.spotter.feature.garmin.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GarminConnectStep {
    data object Credentials : GarminConnectStep
    data class Mfa(val challengeId: String, val method: String?) : GarminConnectStep
}

data class GarminConnectUiState(
    val email: String = "",
    val password: String = "",
    val mfaCode: String = "",
    val step: GarminConnectStep = GarminConnectStep.Credentials,
    val submitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

sealed interface GarminConnectEvent {
    data object Connected : GarminConnectEvent
}

private const val MIN_MFA_DIGITS = 4
private const val MAX_MFA_DIGITS = 10

@HiltViewModel
class GarminConnectViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectGarminUseCase: ConnectGarminUseCase,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val _uiState = MutableStateFlow(GarminConnectUiState())
    val uiState: StateFlow<GarminConnectUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<GarminConnectEvent>(Channel.BUFFERED)
    val events: Flow<GarminConnectEvent> = eventChannel.receiveAsFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorRes = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorRes = null) }
    }

    fun onMfaCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(MAX_MFA_DIGITS)
        _uiState.update { it.copy(mfaCode = digits, errorRes = null) }
    }

    fun onSubmitCredentials() {
        val state = _uiState.value
        if (state.submitting) return
        val email = state.email.trim()
        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(errorRes = R.string.garmin_error_email_invalid) }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.garmin_error_password_empty) }
            return
        }
        _uiState.update { it.copy(submitting = true, errorRes = null) }
        viewModelScope.launch {
            when (val result = connectGarminUseCase.login(userId, email, state.password)) {
                is GarminResult.Success -> when (val value = result.value) {
                    GarminLoginResult.Connected -> {
                        _uiState.update { it.copy(submitting = false, password = "") }
                        eventChannel.send(GarminConnectEvent.Connected)
                    }
                    is GarminLoginResult.MfaRequired -> _uiState.update {
                        it.copy(submitting = false, password = "", mfaCode = "", step = GarminConnectStep.Mfa(value.challengeId, value.method))
                    }
                }
                // The password is only kept around so the user doesn't have to retype it after a
                // wrong-password error; any other failure clears it immediately.
                is GarminResult.Failure -> _uiState.update {
                    it.copy(
                        submitting = false,
                        password = if (result.error == GarminError.InvalidCredentials) it.password else "",
                        errorRes = result.error.toMessageRes(),
                    )
                }
            }
        }
    }

    fun onSubmitMfa() {
        val state = _uiState.value
        if (state.submitting) return
        val step = state.step as? GarminConnectStep.Mfa ?: return
        if (state.mfaCode.length < MIN_MFA_DIGITS) {
            _uiState.update { it.copy(errorRes = R.string.garmin_error_mfa_empty) }
            return
        }
        _uiState.update { it.copy(submitting = true, errorRes = null) }
        viewModelScope.launch {
            when (val result = connectGarminUseCase.verifyMfa(userId, step.challengeId, state.mfaCode)) {
                is GarminResult.Success -> {
                    _uiState.update { it.copy(submitting = false) }
                    eventChannel.send(GarminConnectEvent.Connected)
                }
                is GarminResult.Failure -> if (result.error == GarminError.MfaSessionExpired) {
                    _uiState.update {
                        it.copy(submitting = false, step = GarminConnectStep.Credentials, mfaCode = "", errorRes = result.error.toMessageRes())
                    }
                } else {
                    _uiState.update { it.copy(submitting = false, errorRes = result.error.toMessageRes()) }
                }
            }
        }
    }

    fun onBackToCredentials() {
        _uiState.update { it.copy(step = GarminConnectStep.Credentials, mfaCode = "", errorRes = null) }
    }

    override fun onCleared() {
        _uiState.update { it.copy(password = "") }
        super.onCleared()
    }
}

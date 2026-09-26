package com.lucho314.spotter.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.config.AppConfig
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.feature.common.toLoginMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

/** One-shot UI effects that need Activity/Context to execute (Credential Manager). */
sealed interface LoginEvent {
    data class LaunchCredentialRequest(val hashedNonce: String) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appConfig: AppConfig,
    private val nonceGenerator: NonceGenerator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = eventChannel.receiveAsFlow()

    /** Raw nonce for the in-flight Credential Manager request; consumed once the result arrives. */
    private var pendingRawNonce: String? = null

    val googleWebClientId: String? get() = appConfig.googleWebClientId

    fun onGoogleClick() {
        _uiState.update { it.copy(loading = true, errorMessageRes = null) }
        val clientId = appConfig.googleWebClientId
        if (clientId.isNullOrBlank()) {
            viewModelScope.launch { startOAuthFallback() }
            return
        }
        val nonce = nonceGenerator.generate()
        pendingRawNonce = nonce.rawNonce
        viewModelScope.launch { eventChannel.send(LoginEvent.LaunchCredentialRequest(nonce.hashedNonce)) }
    }

    fun onCredentialResult(result: GoogleIdResult) {
        when (result) {
            is GoogleIdResult.Success -> {
                val rawNonce = pendingRawNonce
                pendingRawNonce = null
                if (rawNonce == null) {
                    _uiState.update { it.copy(loading = false, errorMessageRes = R.string.error_unknown) }
                    return
                }
                viewModelScope.launch {
                    when (val outcome = authRepository.signInWithGoogleIdToken(result.idToken, rawNonce)) {
                        is AppResult.Success -> _uiState.update { it.copy(loading = false) }
                        is AppResult.Failure -> _uiState.update {
                            it.copy(loading = false, errorMessageRes = outcome.error.toLoginMessageRes())
                        }
                    }
                }
            }

            GoogleIdResult.NoCredential, is GoogleIdResult.Failure -> {
                pendingRawNonce = null
                viewModelScope.launch { startOAuthFallback() }
            }

            GoogleIdResult.Cancelled -> {
                pendingRawNonce = null
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun startOAuthFallback() {
        when (val outcome = authRepository.startGoogleOAuth()) {
            is AppResult.Success -> _uiState.update { it.copy(loading = false) }
            is AppResult.Failure -> _uiState.update { it.copy(loading = false, errorMessageRes = outcome.error.toLoginMessageRes()) }
        }
    }
}

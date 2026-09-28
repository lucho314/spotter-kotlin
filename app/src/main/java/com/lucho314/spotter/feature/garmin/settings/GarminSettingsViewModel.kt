package com.lucho314.spotter.feature.garmin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import com.lucho314.spotter.domain.usecase.DisconnectGarminUseCase
import com.lucho314.spotter.domain.usecase.RetryFailedGarminUploadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GarminSettingsUiState(
    val connection: GarminConnectionState = GarminConnectionState.NotConnected,
    val failedCount: Int = 0,
    val disconnectPrompt: Boolean = false,
    val disconnecting: Boolean = false,
)

@HiltViewModel
class GarminSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val garminAccountRepository: GarminAccountRepository,
    garminUploadRepository: GarminUploadRepository,
    private val disconnectGarminUseCase: DisconnectGarminUseCase,
    private val retryFailedGarminUploadsUseCase: RetryFailedGarminUploadsUseCase,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val disconnectPrompt = MutableStateFlow(false)
    private val disconnecting = MutableStateFlow(false)

    val uiState: StateFlow<GarminSettingsUiState> = combine(
        garminAccountRepository.observeConnection(userId),
        garminUploadRepository.observeFailedCount(userId),
        disconnectPrompt,
        disconnecting,
    ) { connection, failedCount, prompt, isDisconnecting ->
        GarminSettingsUiState(connection, failedCount, prompt, isDisconnecting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarminSettingsUiState())

    fun onAutoUploadChange(enabled: Boolean) {
        viewModelScope.launch { garminAccountRepository.setAutoUpload(userId, enabled) }
    }

    fun onDisconnectClick() {
        disconnectPrompt.value = true
    }

    fun onDisconnectDismiss() {
        disconnectPrompt.value = false
    }

    fun onDisconnectConfirmed() {
        disconnectPrompt.value = false
        if (disconnecting.value) return
        disconnecting.value = true
        viewModelScope.launch {
            disconnectGarminUseCase(userId)
            disconnecting.value = false
        }
    }

    fun onRetryFailed() {
        viewModelScope.launch { retryFailedGarminUploadsUseCase(userId) }
    }
}

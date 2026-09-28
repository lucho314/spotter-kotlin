package com.lucho314.spotter.feature.history.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.model.GarminConnectionState
import com.lucho314.spotter.domain.model.GarminEnqueueResult
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminUploadStatus
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import com.lucho314.spotter.domain.usecase.EnqueueGarminUploadUseCase
import com.lucho314.spotter.feature.garmin.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GarminSessionAction { HIDDEN, AVAILABLE, PENDING, UPLOADED }

sealed interface GarminSessionUploadEvent {
    data class Message(@StringRes val messageRes: Int) : GarminSessionUploadEvent
}

/**
 * Drives the "Subir a Garmin" action in [SessionDetailScreen]. A separate ViewModel (not
 * [SessionDetailViewModel]) so the existing screen/tests don't need to change.
 */
@HiltViewModel
class GarminSessionUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    garminAccountRepository: GarminAccountRepository,
    garminUploadRepository: GarminUploadRepository,
    private val enqueueGarminUpload: EnqueueGarminUploadUseCase,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[RouteArgs.SESSION_ID])
    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val uploading = MutableStateFlow(false)

    private val eventChannel = Channel<GarminSessionUploadEvent>(Channel.BUFFERED)
    val events: Flow<GarminSessionUploadEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<GarminSessionAction> = combine(
        garminAccountRepository.observeConnection(userId),
        garminUploadRepository.observeStatus(sessionId),
    ) { connection, status ->
        when {
            connection !is GarminConnectionState.Connected -> GarminSessionAction.HIDDEN
            status == GarminUploadStatus.UPLOADED -> GarminSessionAction.UPLOADED
            status == GarminUploadStatus.PENDING -> GarminSessionAction.PENDING
            else -> GarminSessionAction.AVAILABLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarminSessionAction.HIDDEN)

    fun onUploadClick() {
        if (uploading.value) return
        uploading.value = true
        viewModelScope.launch {
            when (val result = enqueueGarminUpload.fromHistory(userId, sessionId)) {
                is GarminResult.Success -> eventChannel.send(GarminSessionUploadEvent.Message(result.value.toMessageRes()))
                is GarminResult.Failure -> eventChannel.send(GarminSessionUploadEvent.Message(result.error.toMessageRes()))
            }
            uploading.value = false
        }
    }

    private fun GarminEnqueueResult.toMessageRes(): Int = when (this) {
        GarminEnqueueResult.ENQUEUED -> R.string.garmin_upload_queued
        GarminEnqueueResult.ALREADY_PENDING -> R.string.garmin_upload_pending
        GarminEnqueueResult.ALREADY_UPLOADED -> R.string.garmin_upload_already
    }
}

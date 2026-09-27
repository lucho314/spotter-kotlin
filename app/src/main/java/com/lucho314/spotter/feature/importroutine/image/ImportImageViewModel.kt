package com.lucho314.spotter.feature.importroutine.image

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.network.NetworkMonitor
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.ImageRepository
import com.lucho314.spotter.domain.usecase.ImportRoutineFromImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportImageUiState(val imageUri: String? = null, val importing: Boolean = false)

sealed interface ImportImageEvent {
    data class LaunchCamera(val uri: String) : ImportImageEvent
    data class Imported(val routineId: String, val routineName: String?) : ImportImageEvent
    data class ShowError(val kind: AiImportErrorKind) : ImportImageEvent
}

/**
 * [ImportImageUiState.imageUri] lives in [savedStateHandle] (survives process death, unlike a plain
 * `MutableStateFlow`) so a picked/captured photo isn't lost if the process is killed while the
 * camera/gallery activity is in front.
 */
@HiltViewModel
class ImportImageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageRepository: ImageRepository,
    private val importRoutineFromImage: ImportRoutineFromImageUseCase,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val imageUri = savedStateHandle.getStateFlow<String?>(KEY_IMAGE_URI, null)
    private val importing = MutableStateFlow(false)

    private val eventChannel = Channel<ImportImageEvent>(Channel.BUFFERED)
    val events: Flow<ImportImageEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<ImportImageUiState> = combine(imageUri, importing, ::ImportImageUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportImageUiState())

    fun onCameraClick() {
        if (importing.value) return
        viewModelScope.launch {
            when (val result = imageRepository.createCameraCaptureUri()) {
                is AppResult.Success -> {
                    savedStateHandle[KEY_PENDING_CAMERA_URI] = result.value
                    eventChannel.send(ImportImageEvent.LaunchCamera(result.value))
                }
                is AppResult.Failure -> eventChannel.send(ImportImageEvent.ShowError(AiImportErrorKind.GENERIC))
            }
        }
    }

    fun onCameraResult(success: Boolean) {
        val pending = savedStateHandle.get<String>(KEY_PENDING_CAMERA_URI)
        savedStateHandle.remove<String>(KEY_PENDING_CAMERA_URI)
        if (success && pending != null) {
            savedStateHandle[KEY_IMAGE_URI] = pending
        }
    }

    fun onGalleryResult(uri: String?) {
        if (uri != null) savedStateHandle[KEY_IMAGE_URI] = uri
    }

    fun onClearImage() {
        if (importing.value) return
        savedStateHandle[KEY_IMAGE_URI] = null
    }

    fun onImportClick() {
        val uri = imageUri.value ?: return
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser()?.id
                if (userId == null) {
                    eventChannel.send(ImportImageEvent.ShowError(AiImportErrorKind.SESSION_EXPIRED))
                    return@launch
                }
                if (!networkMonitor.isOnline.first()) {
                    eventChannel.send(ImportImageEvent.ShowError(AiImportErrorKind.OFFLINE))
                    return@launch
                }
                when (val encoded = imageRepository.encodeForAiImport(uri)) {
                    is AppResult.Failure -> eventChannel.send(ImportImageEvent.ShowError(encoded.error.toAiImportErrorKind()))
                    is AppResult.Success -> when (val imported = importRoutineFromImage(userId, encoded.value)) {
                        is AppResult.Success -> eventChannel.send(ImportImageEvent.Imported(imported.value.routineId, imported.value.routineName))
                        is AppResult.Failure -> eventChannel.send(ImportImageEvent.ShowError(imported.error.toAiImportErrorKind()))
                    }
                }
            } finally {
                importing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob() + ioDispatcher).launch { imageRepository.clearCameraCaptures() }
    }

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_PENDING_CAMERA_URI = "pending_camera_uri"
    }
}

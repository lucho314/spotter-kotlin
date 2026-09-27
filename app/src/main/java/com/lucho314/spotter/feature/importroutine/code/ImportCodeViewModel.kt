package com.lucho314.spotter.feature.importroutine.code

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.model.SharedRoutinePreview
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.usecase.ImportSharedRoutineUseCase
import com.lucho314.spotter.feature.common.toMessageRes
import com.lucho314.spotter.feature.common.toUserMessageRes
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

sealed interface ImportCodeStatus {
    data object Loading : ImportCodeStatus

    /** Invalid/expired/not importable; no retry action (the code itself is the problem). */
    data class Unavailable(@StringRes val titleRes: Int) : ImportCodeStatus

    /** A retryable failure (e.g. network). */
    data class LoadError(@StringRes val messageRes: Int) : ImportCodeStatus
    data class Ready(val preview: SharedRoutinePreview) : ImportCodeStatus
}

data class ImportCodeUiState(val status: ImportCodeStatus = ImportCodeStatus.Loading, val importing: Boolean = false)

sealed interface ImportCodeEvent {
    data class Imported(val routineId: String) : ImportCodeEvent
    data class ActionFailed(@StringRes val messageRes: Int) : ImportCodeEvent
}

@HiltViewModel
class ImportCodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importSharedRoutine: ImportSharedRoutineUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val code: ShareCode? = ShareCode.parse(savedStateHandle.get<String>(RouteArgs.CODE))

    private val status = MutableStateFlow<ImportCodeStatus>(ImportCodeStatus.Loading)
    private val importing = MutableStateFlow(false)

    private val eventChannel = Channel<ImportCodeEvent>(Channel.BUFFERED)
    val events: Flow<ImportCodeEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<ImportCodeUiState> = combine(status, importing, ::ImportCodeUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportCodeUiState())

    init {
        val currentCode = code
        if (currentCode == null) {
            status.value = ImportCodeStatus.Unavailable(R.string.import_code_invalid_title)
        } else {
            load(currentCode)
        }
    }

    fun retry() {
        code?.let { load(it) }
    }

    private fun load(code: ShareCode) {
        status.value = ImportCodeStatus.Loading
        viewModelScope.launch {
            status.value = when (val result = importSharedRoutine.preview(code)) {
                is AppResult.Success -> ImportCodeStatus.Ready(result.value)
                is AppResult.Failure -> result.error.toUnavailableOrLoadError()
            }
        }
    }

    fun onImportClick() {
        val currentCode = code ?: return
        if (uiState.value.status !is ImportCodeStatus.Ready || importing.value) return
        val userId = authRepository.currentUser()?.id
        if (userId == null) {
            viewModelScope.launch { eventChannel.send(ImportCodeEvent.ActionFailed(R.string.error_unauthorized)) }
            return
        }
        importing.value = true
        viewModelScope.launch {
            try {
                when (val result = importSharedRoutine.importRoutine(currentCode, userId)) {
                    is AppResult.Success -> eventChannel.send(ImportCodeEvent.Imported(result.value))
                    is AppResult.Failure -> when (result.error) {
                        AppError.NotFound -> status.value = ImportCodeStatus.Unavailable(R.string.import_code_invalid_title)
                        is AppError.Validation -> if (result.error.reason == ValidationReason.SHARED_ROUTINE_INVALID) {
                            status.value = ImportCodeStatus.Unavailable(R.string.validation_shared_routine_invalid)
                        } else {
                            eventChannel.send(ImportCodeEvent.ActionFailed(result.error.toUserMessageRes()))
                        }
                        else -> eventChannel.send(ImportCodeEvent.ActionFailed(result.error.toUserMessageRes()))
                    }
                }
            } finally {
                importing.value = false
            }
        }
    }

    private fun AppError.toUnavailableOrLoadError(): ImportCodeStatus = when (this) {
        AppError.NotFound -> ImportCodeStatus.Unavailable(R.string.import_code_invalid_title)
        is AppError.Validation -> if (reason == ValidationReason.SHARED_ROUTINE_INVALID) {
            ImportCodeStatus.Unavailable(R.string.validation_shared_routine_invalid)
        } else {
            ImportCodeStatus.LoadError(toMessageRes())
        }
        else -> ImportCodeStatus.LoadError(toMessageRes())
    }
}

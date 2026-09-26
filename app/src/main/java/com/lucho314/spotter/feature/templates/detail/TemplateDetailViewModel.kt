package com.lucho314.spotter.feature.templates.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.TemplateRepository
import com.lucho314.spotter.domain.usecase.AdoptTemplateUseCase
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateDetailUiState(
    val loading: Boolean = true,
    val template: TemplateDetail? = null,
    val adopting: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

/** One-shot effects: adopting is a side-effecting action (creates routines), not a state transition. */
sealed interface TemplateDetailEvent {
    data object RoutinesCreated : TemplateDetailEvent
    data class AdoptFailed(@StringRes val messageRes: Int) : TemplateDetailEvent
}

@HiltViewModel
class TemplateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: TemplateRepository,
    private val authRepository: AuthRepository,
    private val adoptTemplateUseCase: AdoptTemplateUseCase,
) : ViewModel() {

    // See ExerciseDetailViewModel's KDoc for why this reads the SavedStateHandle key directly
    // instead of `toRoute<TemplateDetailRoute>()`.
    private val templateId: String = checkNotNull(savedStateHandle[RouteArgs.TEMPLATE_ID]) { "Missing ${RouteArgs.TEMPLATE_ID}" }

    private val _uiState = MutableStateFlow(TemplateDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<TemplateDetailEvent>(Channel.BUFFERED)
    val events: Flow<TemplateDetailEvent> = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun retry() = load()

    fun onAdoptConfirmed() {
        val state = _uiState.value
        val template = state.template ?: return
        if (state.adopting) return
        val userId = authRepository.currentUser()?.id
        if (userId == null) {
            _uiState.update { it.copy(errorMessageRes = R.string.error_unauthorized) }
            return
        }
        _uiState.update { it.copy(adopting = true) }
        viewModelScope.launch {
            when (val result = adoptTemplateUseCase(userId, template)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(adopting = false) }
                    eventChannel.send(TemplateDetailEvent.RoutinesCreated)
                }

                is AppResult.Failure -> {
                    _uiState.update { it.copy(adopting = false) }
                    eventChannel.send(TemplateDetailEvent.AdoptFailed(result.error.toMessageRes()))
                }
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, errorMessageRes = null) }
        viewModelScope.launch {
            when (val result = templateRepository.getTemplate(templateId)) {
                is AppResult.Success -> _uiState.update { it.copy(loading = false, template = result.value) }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, errorMessageRes = result.error.toMessageRes()) }
            }
        }
    }
}

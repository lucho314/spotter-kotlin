package com.lucho314.spotter.feature.routines.edit

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.map
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.calc.Validators
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.domain.repository.TemplateRepository
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineEditUiState(
    val loading: Boolean = false,
    val isEditing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val daysPerWeek: Int? = null,
    val saving: Boolean = false,
    /** Real template count for the "Usar una plantilla" banner (create mode only, RN bug 29: this was hardcoded). */
    val templateCount: Int? = null,
    /** Edit mode only: the existing routine couldn't be loaded, so the form is blocked (review issue: saving over stale/blank fields could otherwise silently wipe the real description/daysPerWeek). */
    @StringRes val loadErrorRes: Int? = null,
)

sealed interface RoutineEditEvent {
    data class Saved(val routineId: String) : RoutineEditEvent
    data class SaveFailed(@StringRes val messageRes: Int) : RoutineEditEvent
}

@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    private val templateRepository: TemplateRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    // See ExerciseDetailViewModel's KDoc on the same pattern for why this reads the SavedStateHandle
    // key directly instead of `toRoute<RoutineEditRoute>()`.
    private val routineId: String? = savedStateHandle[RouteArgs.ROUTINE_ID]
    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val _uiState = MutableStateFlow(RoutineEditUiState(loading = routineId != null, isEditing = routineId != null))
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<RoutineEditEvent>(Channel.BUFFERED)
    val events: Flow<RoutineEditEvent> = eventChannel.receiveAsFlow()

    init {
        val id = routineId
        if (id != null) loadExisting(id) else loadTemplateCount()
    }

    /** Retries the initial load (edit mode only - create mode has nothing to load, so this is a no-op then). */
    fun retryLoad() {
        val id = routineId ?: return
        _uiState.update { it.copy(loading = true, loadErrorRes = null) }
        loadExisting(id)
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    /** `null` clears it (routine with no fixed weekly schedule) - tapping the already-selected day count again is how the screen calls this with `null`. */
    fun onDaysPerWeekChange(value: Int?) = _uiState.update { it.copy(daysPerWeek = value) }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.saving || state.loadErrorRes != null) return
        val input = RoutineInput(name = state.name, description = state.description.trim().ifBlank { null }, daysPerWeek = state.daysPerWeek)
        val validationReason = Validators.routineInput(input)
        if (validationReason != null) {
            viewModelScope.launch { eventChannel.send(RoutineEditEvent.SaveFailed(validationReason.toMessageRes())) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = routineId
            val result = if (id != null) {
                routineRepository.updateRoutine(userId, id, input).map { id }
            } else {
                routineRepository.createRoutine(userId, input)
            }
            when (result) {
                is AppResult.Success -> {
                    // Deliberately left `saving = true`: the screen navigates away right after
                    // `Saved`, and resetting it here would let a very fast double-tap re-submit in
                    // the gap between this event being sent and that navigation actually happening.
                    eventChannel.send(RoutineEditEvent.Saved(result.value))
                }

                is AppResult.Failure -> {
                    _uiState.update { it.copy(saving = false) }
                    eventChannel.send(RoutineEditEvent.SaveFailed(result.error.toMessageRes()))
                }
            }
        }
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val refreshResult = routineRepository.refreshRoutine(userId, id)
            val detail = routineRepository.observeRoutine(userId, id).first()
            when {
                detail != null -> _uiState.update {
                    it.copy(
                        loading = false,
                        name = detail.name,
                        description = detail.description.orEmpty(),
                        daysPerWeek = detail.daysPerWeek,
                    )
                }
                // Only block the form if there is nothing usable in the cache either; a stale
                // cache is still better than refusing to let the user edit at all offline.
                refreshResult is AppResult.Failure -> _uiState.update { it.copy(loading = false, loadErrorRes = refreshResult.error.toMessageRes()) }
                else -> _uiState.update { it.copy(loading = false, loadErrorRes = R.string.error_not_found) }
            }
        }
    }

    private fun loadTemplateCount() {
        viewModelScope.launch {
            when (val result = templateRepository.getTemplates()) {
                is AppResult.Success -> _uiState.update { it.copy(templateCount = result.value.size) }
                is AppResult.Failure -> Unit // banner just stays hidden; not worth surfacing as an error here
            }
        }
    }
}

package com.lucho314.spotter.feature.exercise

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.repository.ExerciseRepository
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    @StringRes val errorMessageRes: Int? = null,
)

/**
 * `getExercise` reads the cached catalog first (see [ExerciseRepository.getExercise]), so this
 * works even without a connection for any exercise the user has already seen in a routine.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    // `SavedStateHandle.toRoute<T>()` decodes via kotlinx.serialization reflection, which doesn't
    // read back values put in by a plain `SavedStateHandle(map)` (only real, nav-populated handles) -
    // reading the argument directly by its route's property name works in both.
    private val exerciseId: Int = checkNotNull(savedStateHandle[RouteArgs.EXERCISE_ID]) { "Missing ${RouteArgs.EXERCISE_ID}" }

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(loading = true, errorMessageRes = null) }
        viewModelScope.launch {
            when (val result = exerciseRepository.getExercise(exerciseId)) {
                is AppResult.Success -> _uiState.update { it.copy(loading = false, exercise = result.value) }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, errorMessageRes = result.error.toMessageRes()) }
            }
        }
    }
}

package com.lucho314.spotter.feature.templates.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.domain.repository.TemplateRepository
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val loading: Boolean = true,
    val templates: List<RoutineTemplateSummary> = emptyList(),
    val goalFilter: TemplateGoal? = null,
    val daysFilter: Int? = null,
    @StringRes val errorMessageRes: Int? = null,
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    /** [goal] is the fully-resolved new filter value; the screen already toggles it off on a repeated tap. */
    fun onGoalFilterChanged(goal: TemplateGoal?) {
        _uiState.update { it.copy(goalFilter = goal) }
        load()
    }

    /** [days] is the fully-resolved new filter value (2..6, or null to clear it). */
    fun onDaysFilterChanged(days: Int?) {
        _uiState.update { it.copy(daysFilter = days) }
        load()
    }

    /**
     * Cancels any in-flight load before starting a new one: without this, rapidly changing filters
     * (each call launches its own request) could have an older, slower request resolve *after* a
     * newer one and overwrite its results with stale data.
     */
    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true, errorMessageRes = null) }
        loadJob = viewModelScope.launch {
            val filters = _uiState.value
            when (val result = templateRepository.getTemplates(filters.goalFilter, filters.daysFilter)) {
                is AppResult.Success -> _uiState.update { it.copy(loading = false, templates = result.value) }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, errorMessageRes = result.error.toMessageRes()) }
            }
        }
    }
}

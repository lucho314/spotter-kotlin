package com.lucho314.spotter.feature.progress

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.repository.ProgressRepository
import com.lucho314.spotter.domain.usecase.GetExerciseProgressUseCase
import com.lucho314.spotter.feature.common.SpotterDateFormats
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressChartPoint(val label: String, val e1RmKg: Double)

sealed interface ProgressChartState {
    data object Hidden : ProgressChartState
    data object Loading : ProgressChartState
    data class Loaded(val points: List<ProgressChartPoint>) : ProgressChartState
    data class Error(@StringRes val messageRes: Int) : ProgressChartState
}

data class ProgressUiState(
    val loading: Boolean = true,
    @StringRes val loadErrorRes: Int? = null,
    val refreshing: Boolean = false,
    val records: List<PersonalRecord> = emptyList(),
    val selectedExerciseId: Int? = null,
    val chart: ProgressChartState = ProgressChartState.Hidden,
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val getExerciseProgressUseCase: GetExerciseProgressUseCase,
    private val preferencesRepository: PreferencesRepository,
    authRepository: AuthRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val records = MutableStateFlow<List<PersonalRecord>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val loadErrorRes = MutableStateFlow<Int?>(null)
    private val refreshing = MutableStateFlow(false)
    private val selectedExerciseId = MutableStateFlow<Int?>(null)
    private val chart = MutableStateFlow<ProgressChartState>(ProgressChartState.Hidden)

    private var chartJob: Job? = null

    /** [combine] has a typed overload up to 5 flows; the chart and the weight unit are combined separately to stay within that. */
    private data class Core(
        val records: List<PersonalRecord>,
        val loading: Boolean,
        val loadErrorRes: Int?,
        val refreshing: Boolean,
        val selectedExerciseId: Int?,
    )

    private val core = combine(records, loading, loadErrorRes, refreshing, selectedExerciseId, ::Core)

    val uiState: StateFlow<ProgressUiState> = combine(core, chart, preferencesRepository.weightUnit) { c, chartState, unit ->
        ProgressUiState(
            loading = c.loading,
            loadErrorRes = c.loadErrorRes,
            refreshing = c.refreshing,
            records = c.records,
            selectedExerciseId = c.selectedExerciseId,
            chart = chartState,
            weightUnit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    init {
        loadRecords()
    }

    fun refresh() = loadRecords()

    fun retry() = loadRecords()

    private fun loadRecords() {
        refreshing.value = true
        viewModelScope.launch {
            when (val result = progressRepository.getPersonalRecords(userId)) {
                is AppResult.Success -> {
                    records.value = result.value
                    loadErrorRes.value = null
                    loading.value = false
                }
                is AppResult.Failure -> {
                    if (records.value.isEmpty()) loadErrorRes.value = result.error.toMessageRes()
                    loading.value = false
                }
            }
            refreshing.value = false
        }
    }

    fun onExerciseChipClick(exerciseId: Int) {
        if (selectedExerciseId.value == exerciseId) {
            chartJob?.cancel()
            selectedExerciseId.value = null
            chart.value = ProgressChartState.Hidden
            return
        }
        chartJob?.cancel()
        selectedExerciseId.value = exerciseId
        chart.value = ProgressChartState.Loading
        chartJob = viewModelScope.launch {
            when (val result = getExerciseProgressUseCase(userId, exerciseId)) {
                is AppResult.Success -> chart.value = ProgressChartState.Loaded(
                    result.value.map { ProgressChartPoint(SpotterDateFormats.shortDayMonth(it.date, timeProvider.zone()), it.bestE1RmKg) },
                )
                is AppResult.Failure -> chart.value = ProgressChartState.Error(result.error.toMessageRes())
            }
        }
    }
}

package com.lucho314.spotter.feature.routines.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.feature.common.toMessageRes
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

data class ArchivedRoutinesUiState(
    val loading: Boolean = true,
    val routines: List<RoutineSummary> = emptyList(),
    /** Set only when there is nothing cached to show at all and the initial refresh failed. */
    @StringRes val loadErrorRes: Int? = null,
)

/** One-shot effects: transient action failures that shouldn't replay on rotation. */
sealed interface ArchivedRoutinesEvent {
    data class ActionFailed(@StringRes val messageRes: Int) : ArchivedRoutinesEvent
}

@HiltViewModel
class ArchivedRoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()
    private val initialRefreshDone = MutableStateFlow(false)
    private val initialRefreshError = MutableStateFlow<Int?>(null)

    private val eventChannel = Channel<ArchivedRoutinesEvent>(Channel.BUFFERED)
    val events: Flow<ArchivedRoutinesEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<ArchivedRoutinesUiState> = combine(
        routineRepository.observeArchivedRoutines(userId),
        initialRefreshDone,
        initialRefreshError,
    ) { routines, refreshDone, error ->
        val hasCachedData = routines.isNotEmpty()
        ArchivedRoutinesUiState(
            loading = !hasCachedData && !refreshDone,
            routines = routines,
            loadErrorRes = if (!hasCachedData && refreshDone) error else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedRoutinesUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = routineRepository.refreshArchived(userId)) {
                is AppResult.Success -> initialRefreshError.value = null
                is AppResult.Failure -> initialRefreshError.value = result.error.toMessageRes()
            }
            initialRefreshDone.value = true
        }
    }

    fun onRestore(routineId: String) {
        viewModelScope.launch {
            when (val result = routineRepository.setArchived(userId, routineId, archived = false)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> eventChannel.send(ArchivedRoutinesEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }
}

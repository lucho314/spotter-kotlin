package com.lucho314.spotter.feature.history.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.work.SyncScheduler
import com.lucho314.spotter.domain.calc.WorkoutMath
import com.lucho314.spotter.domain.model.PendingWorkout
import com.lucho314.spotter.domain.model.WorkoutSessionSummary
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.HISTORY_PAGE_SIZE
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import com.lucho314.spotter.feature.common.SpotterDateFormats
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistorySessionItem(val id: String, val routineName: String?, val dateText: String, val durationMinutes: Long?)

data class FailedWorkoutItem(val id: String, val dateText: String, val setCount: Int)

data class HistoryUiState(
    val loading: Boolean = true,
    @StringRes val loadErrorRes: Int? = null,
    val refreshing: Boolean = false,
    val sessions: List<HistorySessionItem> = emptyList(),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** [PendingWorkoutRepository.observeCount] minus [failedWorkouts].size - the ones still uploading, not yet failed. */
    val pendingOnlyCount: Int = 0,
    val failedWorkouts: List<FailedWorkoutItem> = emptyList(),
    val deletingSessionIds: Set<String> = emptySet(),
)

sealed interface HistoryEvent {
    data class ActionFailed(@StringRes val messageRes: Int) : HistoryEvent
    data object SessionDeleted : HistoryEvent
}

/**
 * "Cargar más" pagination (not infinite scroll, migration plan deviation section 7.8) over
 * [WorkoutHistoryRepository.getSessions]. A drop in the pending/failed outbox count (a background
 * sync just completed) reloads the first page silently, since a just-synced session belongs at the
 * top of the list.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val pendingWorkoutRepository: PendingWorkoutRepository,
    private val syncScheduler: SyncScheduler,
    authRepository: AuthRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val sessions = MutableStateFlow<List<HistorySessionItem>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val loadErrorRes = MutableStateFlow<Int?>(null)
    private val refreshing = MutableStateFlow(false)
    private val hasMore = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val deletingSessionIds = MutableStateFlow<Set<String>>(emptySet())

    private var loadJob: Job? = null
    private var lastPendingCount = -1

    private val eventChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = eventChannel.receiveAsFlow()

    /** [combine] has a typed overload up to 5 flows; the load/refresh state is grouped separately from the outbox/pagination state to stay within that. */
    private data class LoadState(
        val sessions: List<HistorySessionItem>,
        val loading: Boolean,
        val loadErrorRes: Int?,
        val refreshing: Boolean,
        val deletingSessionIds: Set<String>,
    )

    private data class OutboxState(
        val hasMore: Boolean,
        val loadingMore: Boolean,
        val pendingCount: Int,
        val failed: List<PendingWorkout>,
    )

    private val loadState = combine(sessions, loading, loadErrorRes, refreshing, deletingSessionIds, ::LoadState)
    private val outboxState = combine(
        hasMore, loadingMore, pendingWorkoutRepository.observeCount(userId), pendingWorkoutRepository.observeFailed(userId), ::OutboxState,
    )

    val uiState: StateFlow<HistoryUiState> = combine(loadState, outboxState) { load, outbox ->
        HistoryUiState(
            loading = load.loading,
            loadErrorRes = load.loadErrorRes,
            refreshing = load.refreshing,
            sessions = load.sessions,
            hasMore = outbox.hasMore,
            loadingMore = outbox.loadingMore,
            pendingOnlyCount = (outbox.pendingCount - outbox.failed.size).coerceAtLeast(0),
            failedWorkouts = outbox.failed.map { it.toFailedWorkoutItem() },
            deletingSessionIds = load.deletingSessionIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        reload(userInitiated = false)
        viewModelScope.launch {
            pendingWorkoutRepository.observeCount(userId).collect { count ->
                if (lastPendingCount != -1 && count < lastPendingCount) reload(userInitiated = false)
                lastPendingCount = count
            }
        }
    }

    fun refresh() = reload(userInitiated = true)

    fun retry() = reload(userInitiated = true)

    private fun reload(userInitiated: Boolean) {
        loadJob?.cancel()
        refreshing.value = true
        loadJob = viewModelScope.launch {
            when (val result = workoutHistoryRepository.getSessions(userId, offset = 0)) {
                is AppResult.Success -> {
                    val items = result.value.map { it.toHistorySessionItem() }
                    sessions.value = items
                    hasMore.value = result.value.size == HISTORY_PAGE_SIZE
                    loadErrorRes.value = null
                    loading.value = false
                }
                is AppResult.Failure -> {
                    if (sessions.value.isEmpty()) {
                        loadErrorRes.value = result.error.toMessageRes()
                    } else if (userInitiated) {
                        eventChannel.send(HistoryEvent.ActionFailed(result.error.toMessageRes()))
                    }
                    loading.value = false
                }
            }
            refreshing.value = false
        }
    }

    fun loadMore() {
        if (loadingMore.value || !hasMore.value) return
        loadingMore.value = true
        loadJob = viewModelScope.launch {
            when (val result = workoutHistoryRepository.getSessions(userId, offset = sessions.value.size)) {
                is AppResult.Success -> {
                    val newItems = result.value.map { it.toHistorySessionItem() }
                    sessions.value = (sessions.value + newItems).distinctBy { it.id }
                    hasMore.value = result.value.size == HISTORY_PAGE_SIZE
                }
                is AppResult.Failure -> eventChannel.send(HistoryEvent.ActionFailed(result.error.toMessageRes()))
            }
            loadingMore.value = false
        }
    }

    fun onDeleteSession(id: String) {
        if (id in deletingSessionIds.value) return
        deletingSessionIds.value += id
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.deleteSession(id)) {
                is AppResult.Success -> {
                    sessions.value = sessions.value.filterNot { it.id == id }
                    eventChannel.send(HistoryEvent.SessionDeleted)
                }
                is AppResult.Failure -> eventChannel.send(HistoryEvent.ActionFailed(result.error.toMessageRes()))
            }
            deletingSessionIds.value -= id
        }
    }

    fun onRetryFailed(id: String) {
        viewModelScope.launch {
            pendingWorkoutRepository.resetToPending(id)
            syncScheduler.schedule()
        }
    }

    fun onDiscardFailed(id: String) {
        viewModelScope.launch { pendingWorkoutRepository.delete(id) }
    }

    private fun WorkoutSessionSummary.toHistorySessionItem() = HistorySessionItem(
        id = id,
        routineName = routineName,
        dateText = SpotterDateFormats.longDay(completedAt ?: startedAt, timeProvider.zone()),
        durationMinutes = WorkoutMath.durationMinutes(startedAt, completedAt),
    )

    private fun PendingWorkout.toFailedWorkoutItem() = FailedWorkoutItem(
        id = id,
        dateText = SpotterDateFormats.longDay(completedAt, timeProvider.zone()),
        setCount = sets.size,
    )
}

package com.lucho314.spotter.feature.routines.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.calc.SpanishWeekdays
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
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

data class RoutinesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val routines: List<RoutineSummary> = emptyList(),
    /** Set only when there is nothing cached to show at all and the initial refresh failed. */
    @StringRes val loadErrorRes: Int? = null,
    /**
     * Non-null when this user has a workout in progress: shows a "Continuar entrenamiento" banner.
     * This lives here (not a dashboard banner) because `feature/dashboard/` doesn't exist yet
     * (migration plan phase 5) - Rutinas is the closest already-shipped top-level screen, and the
     * one the routine detail screen itself returns to after starting a workout.
     */
    val activeWorkoutRoutineName: String? = null,
)

/** One-shot effects: transient action failures that shouldn't replay on rotation. */
sealed interface RoutinesEvent {
    data class ActionFailed(@StringRes val messageRes: Int) : RoutinesEvent
}

/** Minimum time between two [refreshOnResume] calls, so navigating back and forth quickly doesn't hammer the network. */
private val RESUME_REFRESH_THROTTLE = Duration.ofSeconds(2)

/**
 * Cache-then-network (ADR A3-c): [uiState] observes [RoutineRepository]'s local cache directly, so
 * it renders immediately (even offline) while [refresh] fetches in the background - `loading`
 * stays `true` until the cache has *some* data or the first refresh has finished (success or
 * failure), so a brand-new/offline user briefly sees a spinner instead of a flash of "no tenés
 * rutinas" or a generic error; `loadErrorRes` is only set when refresh failed *and* there is still
 * nothing cached to show, since a real cached list should never be replaced by an error screen -
 * see [refresh]'s use of [RoutinesEvent] for that case instead.
 */
@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val timeProvider: TimeProvider,
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val refreshing = MutableStateFlow(false)
    private val initialRefreshDone = MutableStateFlow(false)
    private val initialRefreshError = MutableStateFlow<Int?>(null)
    private var lastRefreshAt: java.time.Instant? = null

    private val eventChannel = Channel<RoutinesEvent>(Channel.BUFFERED)
    val events: Flow<RoutinesEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<RoutinesUiState> = combine(
        routineRepository.observeRoutines(userId),
        refreshing,
        initialRefreshDone,
        initialRefreshError,
        activeWorkoutRepository.observeActive(userId),
    ) { routines, isRefreshing, refreshDone, error, activeWorkout ->
        val hasCachedData = routines.isNotEmpty()
        RoutinesUiState(
            loading = !hasCachedData && !refreshDone,
            refreshing = isRefreshing,
            routines = SpanishWeekdays.sortRoutinesByFirstWeekday(routines),
            loadErrorRes = if (!hasCachedData && refreshDone) error else null,
            activeWorkoutRoutineName = activeWorkout?.routineName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    init {
        // Silent: this is the automatic initial load, not something the user asked for - same as
        // `refreshOnResume`, a failure here with a cache already on screen (e.g. from a previous
        // session) just leaves that cache showing, with no snackbar.
        performRefresh(userInitiated = false)
    }

    /** Pull-to-refresh: the user explicitly asked for this, so - unlike the silent background refreshes - a failure must say so, even if stale cached data is still shown underneath. */
    fun refresh() = performRefresh(userInitiated = true)

    /**
     * Called when the screen becomes visible again (e.g. returning from routine detail after
     * editing/adding exercises there): the cache for `routines:active` is only refreshed by a
     * mutation that touches the routine's own summary fields, not e.g. its exercise count, so
     * without this the list can go stale. Throttled so rapid back-and-forth navigation doesn't
     * refetch on every single resume, and silent (see [performRefresh]): the user didn't ask for
     * this one, they just navigated back.
     */
    fun refreshOnResume() {
        val last = lastRefreshAt
        if (last != null && Duration.between(last, timeProvider.now()) < RESUME_REFRESH_THROTTLE) return
        performRefresh(userInitiated = false)
    }

    private fun performRefresh(userInitiated: Boolean) {
        refreshing.value = true
        lastRefreshAt = timeProvider.now()
        viewModelScope.launch {
            when (val result = routineRepository.refreshRoutines(userId)) {
                is AppResult.Success -> initialRefreshError.value = null
                is AppResult.Failure -> {
                    initialRefreshError.value = result.error.toMessageRes()
                    // Only a problem worth interrupting the user for if there's already a cached
                    // list on screen (otherwise `loadErrorRes` already covers it with a full error
                    // state) AND they're the one who asked for this refresh.
                    if (userInitiated && uiState.value.routines.isNotEmpty()) {
                        eventChannel.send(RoutinesEvent.ActionFailed(result.error.toMessageRes()))
                    }
                }
            }
            initialRefreshDone.value = true
            refreshing.value = false
        }
    }

    fun onArchive(routineId: String) {
        viewModelScope.launch {
            when (val result = routineRepository.setArchived(userId, routineId, archived = true)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> eventChannel.send(RoutinesEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }

    /** Returns `null` for a code that isn't in either known [ShareCode] format; the screen keeps the field and shows an inline error. */
    fun parseImportCode(raw: String): ShareCode? = ShareCode.parse(raw)
}

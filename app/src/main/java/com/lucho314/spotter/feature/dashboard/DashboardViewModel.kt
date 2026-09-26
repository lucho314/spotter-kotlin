package com.lucho314.spotter.feature.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.calc.SpanishWeekdays
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.domain.usecase.GetDashboardStatsUseCase
import com.lucho314.spotter.feature.common.SectionState
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val greeting: Greeting = Greeting.MORNING,
    val displayName: String? = null,
    val sessionsThisWeek: SectionState<Int> = SectionState.Loading,
    val lastSession: SectionState<LastSessionLabel> = SectionState.Loading,
    val latestPr: SectionState<PersonalRecord?> = SectionState.Loading,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val routinesLoading: Boolean = true,
    val topRoutines: List<RoutineSummary> = emptyList(),
    val activeWorkoutRoutineName: String? = null,
    val pendingSyncCount: Int = 0,
    val refreshing: Boolean = false,
)

sealed interface DashboardEvent {
    data class ActionFailed(@StringRes val messageRes: Int) : DashboardEvent
}

private val RESUME_REFRESH_THROTTLE: Duration = Duration.ofSeconds(30)

/**
 * Composes the dashboard's independent sections. [uiState] combines exactly 5 flows (the typed
 * [combine] overload's limit): the internal [StatsSection] (greeting + the 3 stat sections'
 * `SectionState` + refreshing + whether routines finished their first refresh),
 * [RoutineRepository.observeRoutines], [ActiveWorkoutRepository.observeActive],
 * [PendingWorkoutRepository.observeCount] and [PreferencesRepository.weightUnit].
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val routineRepository: RoutineRepository,
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val pendingWorkoutRepository: PendingWorkoutRepository,
    preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private data class StatsSection(
        val greeting: Greeting,
        val sessionsThisWeek: SectionState<Int>,
        val lastSession: SectionState<LastSessionLabel>,
        val latestPr: SectionState<PersonalRecord?>,
        val refreshing: Boolean,
        val routinesRefreshDone: Boolean,
    )

    private val statsSection = MutableStateFlow(
        StatsSection(
            greeting = greetingFor(timeProvider.now(), timeProvider.zone()),
            sessionsThisWeek = SectionState.Loading,
            lastSession = SectionState.Loading,
            latestPr = SectionState.Loading,
            refreshing = false,
            routinesRefreshDone = false,
        ),
    )

    private var lastPendingCount = -1
    private var lastResumeRefreshAt: Instant? = null

    private val eventChannel = Channel<DashboardEvent>(Channel.BUFFERED)
    val events: Flow<DashboardEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        statsSection,
        routineRepository.observeRoutines(userId),
        activeWorkoutRepository.observeActive(userId),
        pendingWorkoutRepository.observeCount(userId),
        preferencesRepository.weightUnit,
    ) { stats, routines, active, pendingCount, unit ->
        DashboardUiState(
            greeting = stats.greeting,
            displayName = authRepository.currentUser()?.displayName,
            sessionsThisWeek = stats.sessionsThisWeek,
            lastSession = stats.lastSession,
            latestPr = stats.latestPr,
            weightUnit = unit,
            routinesLoading = routines.isEmpty() && !stats.routinesRefreshDone,
            topRoutines = SpanishWeekdays.sortRoutinesByFirstWeekday(routines).take(3),
            activeWorkoutRoutineName = active?.routineName,
            pendingSyncCount = pendingCount,
            refreshing = stats.refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        loadStats(userInitiated = false)
        refreshRoutines(userInitiated = false)
        viewModelScope.launch {
            pendingWorkoutRepository.observeCount(userId).collect { count ->
                if (lastPendingCount != -1 && count < lastPendingCount) loadStats(userInitiated = false)
                lastPendingCount = count
            }
        }
    }

    fun refresh() {
        loadStats(userInitiated = true)
        refreshRoutines(userInitiated = true)
    }

    /** Throttled, silent refresh for returning to this screen (e.g. after finishing a workout). */
    fun refreshOnResume() {
        val last = lastResumeRefreshAt
        if (last != null && Duration.between(last, timeProvider.now()) < RESUME_REFRESH_THROTTLE) return
        lastResumeRefreshAt = timeProvider.now()
        loadStats(userInitiated = false)
        refreshRoutines(userInitiated = false)
    }

    private fun loadStats(userInitiated: Boolean) {
        statsSection.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val stats = getDashboardStatsUseCase(userId)
            val current = statsSection.value
            val sessions = current.sessionsThisWeek.updated(stats.sessionsThisWeek, userInitiated) { it }
            val lastSession = current.lastSession.updated(stats.lastSessionAt, userInitiated) { instant ->
                lastSessionLabel(instant, timeProvider.now(), timeProvider.zone())
            }
            val latestPr = current.latestPr.updated(stats.latestPr, userInitiated) { it }
            statsSection.update { it.copy(sessionsThisWeek = sessions, lastSession = lastSession, latestPr = latestPr, refreshing = false) }
        }
    }

    /** Maps an [AppResult] onto this section, keeping the current value on a repeated failure (see the class KDoc). */
    private suspend fun <T, R> SectionState<R>.updated(result: AppResult<T>, userInitiated: Boolean, transform: (T) -> R): SectionState<R> =
        when (result) {
            is AppResult.Success -> SectionState.Loaded(transform(result.value))
            is AppResult.Failure -> {
                if (this is SectionState.Loaded) {
                    if (userInitiated) eventChannel.send(DashboardEvent.ActionFailed(result.error.toMessageRes()))
                    this
                } else {
                    SectionState.Error(result.error.toMessageRes())
                }
            }
        }

    private fun refreshRoutines(userInitiated: Boolean) {
        viewModelScope.launch {
            val hadCache = uiState.value.topRoutines.isNotEmpty()
            when (val result = routineRepository.refreshRoutines(userId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> if (userInitiated && hadCache) {
                    eventChannel.send(DashboardEvent.ActionFailed(result.error.toMessageRes()))
                }
            }
            statsSection.update { it.copy(routinesRefreshDone = true) }
        }
    }
}

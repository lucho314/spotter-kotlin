package com.lucho314.spotter.feature.workout

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IdGenerator
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.network.NetworkMonitor
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import com.lucho314.spotter.domain.usecase.DiscardWorkoutUseCase
import com.lucho314.spotter.domain.usecase.FinishResult
import com.lucho314.spotter.domain.usecase.FinishWorkoutUseCase
import com.lucho314.spotter.domain.usecase.ToggleSetCompletionUseCase
import com.lucho314.spotter.domain.usecase.UpdateSetInputUseCase
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A set's in-flight edited text, ahead of what's persisted to Room ([WorkoutViewModel]'s debounce window). */
data class InputDraft(val weightText: String, val repsText: String)

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: ActiveWorkout? = null,
    val drafts: Map<String, InputDraft> = emptyMap(),
    val now: Instant = Instant.EPOCH,
    val online: Boolean = true,
) {
    val currentExercise: ActiveExercise?
        get() = workout?.let { it.exercises.getOrNull(it.currentExerciseIndex) }

    val sessionElapsedSeconds: Long
        get() = workout?.let { ChronoUnit.SECONDS.between(it.startedAt, now).coerceAtLeast(0) } ?: 0

    val restRemainingSeconds: Int?
        get() = workout?.rest?.remainingSeconds(now)

    val completedSetCount: Int
        get() = workout?.completedSetCount ?: 0

    /** [set] overlaid with its in-flight [drafts] text, if any (so typing shows immediately, without waiting for the debounced Room round-trip). */
    fun display(set: ActiveSet): ActiveSet =
        drafts[set.id]?.let { set.copy(weightText = it.weightText, repsText = it.repsText) } ?: set
}

sealed interface LastSessionUiState {
    data object Hidden : LastSessionUiState
    data object Loading : LastSessionUiState
    data class Loaded(val session: LastExerciseSession?) : LastSessionUiState
    data class Error(@StringRes val messageRes: Int) : LastSessionUiState
}

/** One-shot effects. */
sealed interface WorkoutEvent {
    /** The rest timer reached zero while this screen was being actively observed; the screen beeps and vibrates. */
    data object RestFinished : WorkoutEvent
    data class ActionFailed(@StringRes val messageRes: Int) : WorkoutEvent
    data class Finished(val online: Boolean) : WorkoutEvent
    data object Discarded : WorkoutEvent

    /** No active workout for this user (already finished/discarded elsewhere, e.g. another device) - nothing to show. */
    data object NoActiveWorkout : WorkoutEvent
}

/**
 * Drives the active-workout screen. Room ([ActiveWorkoutRepository]) is the single source of truth
 * (survives process death); this only adds a thin, debounced text-input overlay on top of it so
 * fast typing doesn't round-trip through Room on every keystroke (RN bug #8, section 7: the RN
 * screen kept per-row local state keyed by list index, which showed another exercise's values after
 * navigating - keying persistence by `set.id` here, with Room itself as the state, avoids that
 * class of bug entirely).
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val authRepository: AuthRepository,
    private val updateSetInputUseCase: UpdateSetInputUseCase,
    private val toggleSetCompletionUseCase: ToggleSetCompletionUseCase,
    private val finishWorkoutUseCase: FinishWorkoutUseCase,
    private val discardWorkoutUseCase: DiscardWorkoutUseCase,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
    private val networkMonitor: NetworkMonitor,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val drafts = MutableStateFlow<Map<String, InputDraft>>(emptyMap())
    private val persistJobs = mutableMapOf<String, Job>()

    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    private val _lastSessionState = MutableStateFlow<LastSessionUiState>(LastSessionUiState.Hidden)
    val lastSessionState: StateFlow<LastSessionUiState> = _lastSessionState.asStateFlow()

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(timeProvider.now())
            delay(1_000)
        }
    }

    private var lastRestEndsAtNotified: Instant? = null
    private var notifiedNoActiveWorkout = false

    val uiState: StateFlow<WorkoutUiState> = combine(
        activeWorkoutRepository.observeActive(userId),
        ticker,
        drafts,
        networkMonitor.isOnline,
    ) { workout, now, draftMap, online ->
        if (workout != null) {
            notifiedNoActiveWorkout = false
            maybeHandleRestFinished(workout, now)
        } else if (!notifiedNoActiveWorkout) {
            notifiedNoActiveWorkout = true
            eventChannel.trySend(WorkoutEvent.NoActiveWorkout)
        }
        WorkoutUiState(loading = false, workout = workout, drafts = draftMap, now = now, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    /**
     * The rest timer reaching zero only matters here while this screen is actually being collected
     * (see [uiState]'s `WhileSubscribed`): while backgrounded, [com.lucho314.spotter.core.notifications.RestTimerReceiver]'s
     * scheduled alarm is the reliable notifier instead. `endsAt` (not a plain boolean) keys
     * [lastRestEndsAtNotified] so a *new* rest period (a different `endsAt`) always notifies again,
     * even if the previous one was also already at zero.
     */
    private suspend fun maybeHandleRestFinished(workout: ActiveWorkout, now: Instant) {
        val rest = workout.rest ?: return
        if (rest.remainingSeconds(now) > 0) return
        if (lastRestEndsAtNotified == rest.endsAt) return
        lastRestEndsAtNotified = rest.endsAt
        activeWorkoutRepository.setRestTimer(workout.sessionId, null)
        eventChannel.trySend(WorkoutEvent.RestFinished)
    }

    fun onWeightChange(setId: String, text: String) {
        updateDraft(setId) { it.copy(weightText = text) }
        schedulePersist(setId)
    }

    fun onRepsChange(setId: String, text: String) {
        updateDraft(setId) { it.copy(repsText = text) }
        schedulePersist(setId)
    }

    private fun currentDraftOrSet(setId: String): InputDraft {
        drafts.value[setId]?.let { return it }
        val set = uiState.value.workout?.exercises?.firstNotNullOfOrNull { exercise -> exercise.sets.firstOrNull { it.id == setId } }
        return InputDraft(weightText = set?.weightText.orEmpty(), repsText = set?.repsText.orEmpty())
    }

    private fun updateDraft(setId: String, transform: (InputDraft) -> InputDraft) {
        drafts.update { current -> current + (setId to transform(current[setId] ?: currentDraftOrSet(setId))) }
    }

    private fun schedulePersist(setId: String) {
        persistJobs[setId]?.cancel()
        persistJobs[setId] = viewModelScope.launch {
            delay(300)
            flushDraft(setId)
        }
    }

    private suspend fun flushDraft(setId: String) {
        val draft = drafts.value[setId] ?: return
        updateSetInputUseCase(setId, draft.weightText, draft.repsText)
    }

    private suspend fun flushAllDrafts() {
        persistJobs.values.forEach { it.cancel() }
        persistJobs.clear()
        drafts.value.keys.toList().forEach { flushDraft(it) }
    }

    fun onToggleSet(exerciseRowId: Long, setId: String) {
        viewModelScope.launch {
            persistJobs[setId]?.cancel()
            persistJobs.remove(setId)
            flushDraft(setId)
            drafts.update { it - setId }
            val workout = activeWorkoutRepository.getActive(userId) ?: return@launch
            when (val result = toggleSetCompletionUseCase(workout, exerciseRowId, setId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> eventChannel.send(WorkoutEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }

    fun onAddSet(exerciseRowId: Long) {
        viewModelScope.launch {
            val workout = activeWorkoutRepository.getActive(userId) ?: return@launch
            val exercise = workout.exercises.firstOrNull { it.rowId == exerciseRowId } ?: return@launch
            val last = exercise.sets.lastOrNull()
            val weightText = last?.weightText.orEmpty()
            val repsText = last?.repsText ?: exercise.targetReps.toString()
            activeWorkoutRepository.addSet(exerciseRowId, idGenerator.uuid(), weightText, repsText)
        }
    }

    fun onSelectExercise(index: Int) {
        viewModelScope.launch {
            flushAllDrafts()
            val workout = activeWorkoutRepository.getActive(userId) ?: return@launch
            activeWorkoutRepository.setCurrentExercise(workout.sessionId, index)
        }
    }

    fun onSkipRest() {
        viewModelScope.launch {
            val workout = activeWorkoutRepository.getActive(userId) ?: return@launch
            activeWorkoutRepository.setRestTimer(workout.sessionId, null)
            restTimerAlarmScheduler.cancel()
        }
    }

    fun onFinish() {
        viewModelScope.launch {
            flushAllDrafts()
            val sessionId = uiState.value.workout?.sessionId ?: return@launch
            when (val result = finishWorkoutUseCase(userId, sessionId)) {
                is AppResult.Success -> when (result.value) {
                    FinishResult.Saved -> eventChannel.send(WorkoutEvent.Finished(online = networkMonitor.isOnline.first()))
                    FinishResult.NothingToSave -> eventChannel.send(WorkoutEvent.Discarded)
                }

                is AppResult.Failure -> eventChannel.send(WorkoutEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }

    fun onDiscard() {
        viewModelScope.launch {
            val sessionId = uiState.value.workout?.sessionId ?: return@launch
            discardWorkoutUseCase(sessionId)
            eventChannel.send(WorkoutEvent.Discarded)
        }
    }

    fun onShowLastSession(exerciseId: Int) {
        _lastSessionState.value = LastSessionUiState.Loading
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.getLastSession(userId, exerciseId)) {
                is AppResult.Success -> _lastSessionState.value = LastSessionUiState.Loaded(result.value)
                is AppResult.Failure -> _lastSessionState.value = LastSessionUiState.Error(result.error.toMessageRes())
            }
        }
    }

    fun onDismissLastSession() {
        _lastSessionState.value = LastSessionUiState.Hidden
    }

    /**
     * `viewModelScope` is already cancelled by the time [onCleared] runs, so the last (at most
     * 300ms old) unflushed keystrokes need a scope that outlives it - a short-lived one, since this
     * is only meant to finish the handful of pending upserts already scheduled by [schedulePersist],
     * not to run indefinitely.
     */
    override fun onCleared() {
        super.onCleared()
        val pending = drafts.value
        if (pending.isEmpty()) return
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            pending.forEach { (setId, draft) -> updateSetInputUseCase(setId, draft.weightText, draft.repsText) }
        }
    }
}

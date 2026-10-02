package com.lucho314.spotter.feature.workout

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IdGenerator
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.network.NetworkMonitor
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.domain.calc.ExerciseNote
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.LastExerciseSession
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import com.lucho314.spotter.feature.common.SpotterDateFormats
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
import kotlinx.coroutines.launch

/** A set's in-flight edited text, ahead of what's persisted to Room ([WorkoutViewModel]'s debounce window). */
data class InputDraft(val weightText: String, val repsText: String)

data class WorkoutUiState(
    val loading: Boolean = true,
    val workout: ActiveWorkout? = null,
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
}

sealed interface LastSessionUiState {
    data object Hidden : LastSessionUiState
    data object Loading : LastSessionUiState
    data class Loaded(val session: LastExerciseSession?, val dateText: String?) : LastSessionUiState
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

    /**
     * Compose snapshot state, not a [kotlinx.coroutines.flow.StateFlow]/`combine()` pipeline
     * (review carry-over 10): a set's in-flight text used to be folded into [uiState] alongside the
     * 1s [ticker] and [NetworkMonitor.isOnline], so every keystroke's UI update was delayed behind
     * a coroutine dispatch and recomposed together with unrelated ticks - under fast typing this
     * could visibly drop characters or jump the cursor. Writing here is synchronous and read
     * directly by [WorkoutScreen] (via [display]), so a keystroke shows immediately; the debounced
     * Room persistence below is unaffected.
     */
    private val drafts: SnapshotStateMap<String, InputDraft> = mutableStateMapOf()
    private val persistJobs = mutableMapOf<String, Job>()

    /** Same overlay + debounce as [drafts], for each exercise's note, keyed by [ActiveExercise.rowId]. */
    private val noteDrafts: SnapshotStateMap<Long, String> = mutableStateMapOf()
    private val notePersistJobs = mutableMapOf<Long, Job>()

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

    /**
     * `true` while [onFinish] or [onDiscard] is in flight. [FinishWorkoutUseCase] and
     * [DiscardWorkoutUseCase] both clear Room's active session as part of finishing/discarding, so
     * without this guard the very next [uiState] emission would see `workout == null` and fire
     * [WorkoutEvent.NoActiveWorkout] - racing (and sometimes winning against) the [WorkoutEvent.Finished]/
     * [WorkoutEvent.Discarded] event this same call already sends, which made the screen navigate
     * back via the wrong path and drop the finish snackbar (review carry-over 3).
     */
    private var closing = false

    val uiState: StateFlow<WorkoutUiState> = combine(
        activeWorkoutRepository.observeActive(userId),
        ticker,
        networkMonitor.isOnline,
    ) { workout, now, online ->
        if (workout != null) {
            notifiedNoActiveWorkout = false
            maybeHandleRestFinished(workout, now)
        } else if (!notifiedNoActiveWorkout && !closing) {
            notifiedNoActiveWorkout = true
            eventChannel.trySend(WorkoutEvent.NoActiveWorkout)
        }
        WorkoutUiState(loading = false, workout = workout, now = now, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    /** [set] overlaid with its in-flight [drafts] text, if any (so typing shows immediately, without waiting for the debounced Room round-trip). */
    fun display(set: ActiveSet): ActiveSet =
        drafts[set.id]?.let { set.copy(weightText = it.weightText, repsText = it.repsText) } ?: set

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
        // Conditional on the DAO still holding this exact `endsAt`: `workout` here can be a stale
        // snapshot (this combine() emission race with a completed-set write), so an unconditional
        // clear could wipe out a brand *new* rest period that already started in the meantime.
        // If it no longer matches, that new period is what's actually running - don't beep for the
        // old one either.
        if (activeWorkoutRepository.clearRestTimerIfMatches(workout.sessionId, rest.endsAt)) {
            eventChannel.trySend(WorkoutEvent.RestFinished)
        }
    }

    /** [exercise]'s note text as currently typed (draft first, then what's persisted). */
    fun noteText(exercise: ActiveExercise): String = noteDrafts[exercise.rowId] ?: exercise.note.orEmpty()

    fun onNoteChange(exerciseRowId: Long, text: String) {
        noteDrafts[exerciseRowId] = ExerciseNote.clampInput(text)
        notePersistJobs[exerciseRowId]?.cancel()
        notePersistJobs[exerciseRowId] = viewModelScope.launch {
            delay(300)
            flushNoteDraft(exerciseRowId)
        }
    }

    private suspend fun flushNoteDraft(exerciseRowId: Long) {
        val text = noteDrafts[exerciseRowId] ?: return
        activeWorkoutRepository.updateExerciseNote(exerciseRowId, text.ifEmpty { null })
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
        drafts[setId]?.let { return it }
        val set = uiState.value.workout?.exercises?.firstNotNullOfOrNull { exercise -> exercise.sets.firstOrNull { it.id == setId } }
        return InputDraft(weightText = set?.weightText.orEmpty(), repsText = set?.repsText.orEmpty())
    }

    private fun updateDraft(setId: String, transform: (InputDraft) -> InputDraft) {
        drafts[setId] = transform(drafts[setId] ?: currentDraftOrSet(setId))
    }

    private fun schedulePersist(setId: String) {
        persistJobs[setId]?.cancel()
        persistJobs[setId] = viewModelScope.launch {
            delay(300)
            flushDraft(setId)
        }
    }

    private suspend fun flushDraft(setId: String) {
        val draft = drafts[setId] ?: return
        updateSetInputUseCase(setId, draft.weightText, draft.repsText)
    }

    private suspend fun flushAllDrafts() {
        persistJobs.values.forEach { it.cancel() }
        persistJobs.clear()
        drafts.keys.toList().forEach { flushDraft(it) }
        notePersistJobs.values.forEach { it.cancel() }
        notePersistJobs.clear()
        noteDrafts.keys.toList().forEach { flushNoteDraft(it) }
    }

    fun onToggleSet(exerciseRowId: Long, setId: String) {
        viewModelScope.launch {
            persistJobs[setId]?.cancel()
            persistJobs.remove(setId)
            flushDraft(setId)
            drafts.remove(setId)
            val workout = activeWorkoutRepository.getActive(userId) ?: return@launch
            when (val result = toggleSetCompletionUseCase(workout, exerciseRowId, setId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> eventChannel.send(WorkoutEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }

    fun onAddSet(exerciseRowId: Long) {
        viewModelScope.launch {
            // Without this, a just-typed (but not yet debounce-flushed) weight/reps edit on the
            // last set would be invisible here: the new set would copy Room's stale pre-edit text
            // instead of what the user actually just entered.
            flushAllDrafts()
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
        closing = true
        viewModelScope.launch {
            flushAllDrafts()
            val sessionId = uiState.value.workout?.sessionId ?: run { closing = false; return@launch }
            when (val result = finishWorkoutUseCase(userId, sessionId)) {
                is AppResult.Success -> when (result.value) {
                    FinishResult.Saved -> eventChannel.send(WorkoutEvent.Finished(online = networkMonitor.isOnline.first()))
                    FinishResult.NothingToSave -> eventChannel.send(WorkoutEvent.Discarded)
                    // Not a user-chosen discard: the session was already gone (finished/discarded
                    // elsewhere). Report it the same way the normal "no active workout" race is
                    // reported instead of misleadingly claiming *this* screen discarded it.
                    FinishResult.SessionGone -> eventChannel.send(WorkoutEvent.NoActiveWorkout)
                }

                is AppResult.Failure -> {
                    closing = false
                    eventChannel.send(WorkoutEvent.ActionFailed(result.error.toMessageRes()))
                }
            }
        }
    }

    fun onDiscard() {
        closing = true
        viewModelScope.launch {
            val sessionId = uiState.value.workout?.sessionId ?: run { closing = false; return@launch }
            discardWorkoutUseCase(sessionId)
            eventChannel.send(WorkoutEvent.Discarded)
        }
    }

    fun onShowLastSession(exerciseId: Int) {
        _lastSessionState.value = LastSessionUiState.Loading
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.getLastSession(userId, exerciseId)) {
                is AppResult.Success -> _lastSessionState.value = LastSessionUiState.Loaded(
                    session = result.value,
                    dateText = result.value?.let { SpotterDateFormats.longDay(it.date, timeProvider.zone()) },
                )
                is AppResult.Failure -> _lastSessionState.value = LastSessionUiState.Error(result.error.toMessageRes())
            }
        }
    }

    fun onDismissLastSession() {
        _lastSessionState.value = LastSessionUiState.Hidden
    }

    /**
     * Flushes any in-flight (still-debounced) draft text right away. Called from
     * [WorkoutScreen] on [androidx.lifecycle.Lifecycle.Event.ON_STOP] so backgrounding the app
     * mid-keystroke - and the process later being killed while backgrounded - can't silently lose
     * up to [schedulePersist]'s 300ms debounce window of typed text.
     */
    fun onStop() {
        viewModelScope.launch { flushAllDrafts() }
    }

    /**
     * `viewModelScope` is already cancelled by the time [onCleared] runs, so the last (at most
     * 300ms old) unflushed keystrokes need a scope that outlives it - a short-lived one, since this
     * is only meant to finish the handful of pending upserts already scheduled by [schedulePersist],
     * not to run indefinitely.
     */
    override fun onCleared() {
        super.onCleared()
        val pending = drafts.toMap()
        val pendingNotes = noteDrafts.toMap()
        if (pending.isEmpty() && pendingNotes.isEmpty()) return
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            pending.forEach { (setId, draft) -> updateSetInputUseCase(setId, draft.weightText, draft.repsText) }
            pendingNotes.forEach { (rowId, text) -> activeWorkoutRepository.updateExerciseNote(rowId, text.ifEmpty { null }) }
        }
    }
}

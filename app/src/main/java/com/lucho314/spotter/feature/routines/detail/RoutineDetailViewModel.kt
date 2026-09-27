package com.lucho314.spotter.feature.routines.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.calc.RoutineOrdering
import com.lucho314.spotter.domain.calc.SpanishWeekdays
import com.lucho314.spotter.domain.calc.TextSanitizer
import com.lucho314.spotter.domain.calc.Validators
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.RoutineExercisePatch
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.domain.usecase.DaySelection
import com.lucho314.spotter.domain.usecase.ShareRoutineUseCase
import com.lucho314.spotter.domain.usecase.StartResult
import com.lucho314.spotter.domain.usecase.StartWorkoutUseCase
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

/** One section of the detail screen: a real [day] or `null` for "Sin día asignado". */
data class RoutineDayGroup(val day: RoutineDay?, val exercises: List<RoutineExercise>)

data class RoutineDetailUiState(
    val loading: Boolean = true,
    val routine: RoutineDetail? = null,
    val dayGroups: List<RoutineDayGroup> = emptyList(),
    /**
     * Bumped every time [RoutineDetailViewModel.onReorderDayGroup] finishes, success or failure:
     * the screen keys its local optimistic drag order off this (alongside [dayGroups] itself) so a
     * *failed* reorder - which typically leaves [dayGroups] byte-for-byte identical, since nothing
     * actually changed server-side - still forces the UI to drop its stale optimistic order and
     * fall back to the authoritative one from the cache.
     */
    val reorderRevision: Int = 0,
    /** Set only when there is no cached routine to show at all and the initial load failed. */
    @StringRes val loadErrorRes: Int? = null,
    /** True while [RoutineDetailViewModel.onShareClick] is finding/creating a share code. */
    val sharing: Boolean = false,
) {
    val usedDayNames: Set<String> get() = routine?.days.orEmpty().mapTo(mutableSetOf()) { it.name }
    val canStartWorkout: Boolean get() = routine?.exercises?.isNotEmpty() == true
}

/** One-shot effects: navigation, and transient action failures that shouldn't replay on rotation. */
sealed interface RoutineDetailEvent {
    data object Archived : RoutineDetailEvent
    data class ActionFailed(@StringRes val messageRes: Int) : RoutineDetailEvent
    data object WorkoutStarted : RoutineDetailEvent

    /** RN bug #6: [existing] is already in progress; the screen must ask "Continuar o descartar y empezar" instead of silently overwriting it. */
    data class WorkoutAlreadyActive(val existing: ActiveWorkout) : RoutineDetailEvent

    /** A share code is ready to be shared/copied - either reused or freshly created. */
    data class ShareCodeReady(val routineName: String, val code: String) : RoutineDetailEvent
}

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val shareRoutineUseCase: ShareRoutineUseCase,
    private val timeProvider: TimeProvider,
    authRepository: AuthRepository,
) : ViewModel() {

    // See ExerciseDetailViewModel's KDoc for why this reads the SavedStateHandle key directly
    // instead of `toRoute<RoutineDetailRoute>()`.
    val routineId: String = checkNotNull(savedStateHandle[RouteArgs.ROUTINE_ID]) { "Missing ${RouteArgs.ROUTINE_ID}" }
    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val initialLoadDone = MutableStateFlow(false)
    private val initialLoadError = MutableStateFlow<Int?>(null)
    private val reorderRevision = MutableStateFlow(0)
    private val sharing = MutableStateFlow(false)

    private val eventChannel = Channel<RoutineDetailEvent>(Channel.BUFFERED)
    val events: Flow<RoutineDetailEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<RoutineDetailUiState> = combine(
        routineRepository.observeRoutine(userId, routineId),
        initialLoadDone,
        initialLoadError,
        reorderRevision,
        sharing,
    ) { routine, loadDone, loadError, revision, isSharing ->
        RoutineDetailUiState(
            loading = routine == null && !loadDone,
            routine = routine,
            dayGroups = routine?.let(::buildDayGroups).orEmpty(),
            reorderRevision = revision,
            loadErrorRes = if (routine == null && loadDone) loadError else null,
            sharing = isSharing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = routineRepository.refreshRoutine(userId, routineId)) {
                is AppResult.Success -> initialLoadError.value = null
                is AppResult.Failure -> initialLoadError.value = result.error.toMessageRes()
            }
            initialLoadDone.value = true
        }
    }

    fun onRemoveExercise(routineExerciseId: String) {
        viewModelScope.launch {
            when (val result = routineRepository.removeExercise(userId, routineId, routineExerciseId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> reportFailure(result.error)
            }
        }
    }

    /**
     * [orderedIds] must be the ids of one [RoutineDayGroup]'s exercises only (never the whole
     * routine at once): `sort_order` is scoped per day, so mixing groups would silently reorder
     * across days.
     */
    fun onReorderDayGroup(orderedIds: List<String>) {
        viewModelScope.launch {
            when (val result = routineRepository.reorderExercises(userId, routineId, orderedIds)) {
                is AppResult.Success -> Unit
                // The repository already refreshes the detail either way (see its KDoc), so the
                // optimistic order never lingers stale server-side - but if the *cache* ends up
                // identical to what it was before (the common case: nothing changed), the screen's
                // local optimistic list would otherwise never notice. Bumping the revision forces it
                // to re-derive from `dayGroups` regardless.
                is AppResult.Failure -> {
                    reportFailure(result.error)
                }
            }
            reorderRevision.update { it + 1 }
        }
    }

    /**
     * Updates sets/reps/rest and, if [newDayNumber] is non-null, moves the exercise to that day
     * (RN bug 17: the day was silently dropped from the patch). A `Conflict` here always means the
     * exact same exercise already sits on the target day (`UNIQUE(routine_id, exercise_id,
     * day_number)`) - including [com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER], per the
     * review carry-over - so it gets its own clear message instead of the generic one.
     *
     * A move also appends the exercise at [RoutineOrdering.nextSortOrderIn] of the *destination
     * group*'s current contents (review issue: keeping the old `sort_order` could tie with - or
     * interleave oddly among - whatever's already there). [newDayNumber] as passed by the screen is
     * either a real [com.lucho314.spotter.domain.model.RoutineDay.dayNumber] or the
     * [com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER] sentinel for "Sin día asignado";
     * [resolveMoveTarget] maps the latter to whichever bucket that actually is right now (same rule
     * as `AddExerciseViewModel`, see [RoutineOrdering.unassignedBucketDayNumber]).
     */
    fun onUpdateExercise(routineExerciseId: String, targetSets: Int, targetReps: Int, restSeconds: Int, newDayNumber: Int? = null) {
        val validationReason = Validators.routineExercise(targetSets, targetReps, restSeconds)
        if (validationReason != null) {
            viewModelScope.launch { eventChannel.send(RoutineDetailEvent.ActionFailed(validationReason.toMessageRes())) }
            return
        }
        val routine = uiState.value.routine
        val (resolvedDayNumber, sortOrder) = if (newDayNumber != null && routine != null) {
            resolveMoveTarget(routine, newDayNumber)
        } else {
            newDayNumber to null
        }
        val patch = RoutineExercisePatch(
            targetSets = targetSets,
            targetReps = targetReps,
            restSeconds = restSeconds,
            dayNumber = resolvedDayNumber,
            sortOrder = sortOrder,
        )
        viewModelScope.launch {
            when (val result = routineRepository.updateRoutineExercise(userId, routineId, routineExerciseId, patch)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> {
                    val messageRes = if (result.error is AppError.Conflict) R.string.error_exercise_already_in_day else result.error.toMessageRes()
                    eventChannel.send(RoutineDetailEvent.ActionFailed(messageRes))
                }
            }
        }
    }

    private fun resolveMoveTarget(routine: RoutineDetail, requestedDayNumber: Int): Pair<Int, Int> {
        val isRealDay = routine.days.any { it.dayNumber == requestedDayNumber }
        return if (isRealDay) {
            requestedDayNumber to RoutineOrdering.nextSortOrderIn(routine.exercisesForDay(requestedDayNumber))
        } else {
            val bucketDayNumber = RoutineOrdering.unassignedBucketDayNumber(routine)
            bucketDayNumber to RoutineOrdering.nextSortOrderIn(routine.unassignedExercises)
        }
    }

    /**
     * Picks the day number for a newly added day (review carry-over): if the routine has no days
     * yet, [RoutineOrdering.suggestedFirstDayNumber] reuses whatever number its legacy-imported
     * exercises already share instead of always starting at a fresh, unrelated slot; otherwise
     * [RoutineOrdering.nextDayNumber] finds the next free 1..7 slot. `null` (all 7 taken) surfaces
     * as a validation message, never a silent fallback.
     */
    fun onAddDay(name: String) {
        val routine = uiState.value.routine ?: return
        val dayNumber = if (routine.days.isEmpty()) {
            RoutineOrdering.suggestedFirstDayNumber(routine.exercises)
        } else {
            RoutineOrdering.nextDayNumber(routine.days, routine.exercises)
        }
        if (dayNumber == null) {
            viewModelScope.launch { eventChannel.send(RoutineDetailEvent.ActionFailed(ValidationReason.DAYS_MAX_REACHED.toMessageRes())) }
            return
        }
        viewModelScope.launch {
            when (val result = routineRepository.addDays(userId, routineId, listOf(dayNumber to name))) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> reportFailure(result.error)
            }
        }
    }

    fun onRenameDay(dayId: String, name: String) {
        viewModelScope.launch {
            when (val result = routineRepository.renameDay(userId, routineId, dayId, name)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> reportFailure(result.error)
            }
        }
    }

    fun onArchiveConfirmed() {
        viewModelScope.launch {
            when (val result = routineRepository.setArchived(userId, routineId, archived = true)) {
                is AppResult.Success -> eventChannel.send(RoutineDetailEvent.Archived)
                is AppResult.Failure -> reportFailure(result.error)
            }
        }
    }

    /** Today's Spanish weekday name, used to preselect it in `DayPickerSheet` (RN bug #7 fix). */
    fun todayWeekdayName(): String = SpanishWeekdays.of(LocalDate.ofInstant(timeProvider.now(), timeProvider.zone()).dayOfWeek)

    /**
     * [replaceExisting] is only ever `true` when the user explicitly picked "Descartar y empezar"
     * in response to a previous [RoutineDetailEvent.WorkoutAlreadyActive] for this same [day] - the
     * screen re-invokes this with the exact same [day] it originally asked for (review carry-over:
     * `replace` is only ever reached through this path, never speculatively).
     */
    fun onStartWorkout(day: DaySelection, replaceExisting: Boolean = false) {
        viewModelScope.launch {
            when (val result = startWorkoutUseCase(userId, routineId, day, replaceExisting)) {
                is AppResult.Success -> when (val value = result.value) {
                    is StartResult.Started -> eventChannel.send(RoutineDetailEvent.WorkoutStarted)
                    is StartResult.ActiveWorkoutExists -> eventChannel.send(RoutineDetailEvent.WorkoutAlreadyActive(value.existing))
                }

                is AppResult.Failure -> reportFailure(result.error)
            }
        }
    }

    /** Reuses the routine's active share code, or creates a new one; guarded against double-tap via [sharing]. */
    fun onShareClick() {
        val routine = uiState.value.routine ?: return
        if (sharing.value) return
        sharing.value = true
        viewModelScope.launch {
            try {
                when (val result = shareRoutineUseCase(userId, routine)) {
                    is AppResult.Success -> eventChannel.send(
                        RoutineDetailEvent.ShareCodeReady(
                            routineName = TextSanitizer.singleLine(routine.name, 50) ?: routine.name,
                            code = result.value.value,
                        ),
                    )
                    is AppResult.Failure -> reportFailure(result.error)
                }
            } finally {
                sharing.value = false
            }
        }
    }

    fun onDeleteDay(dayId: String) {
        viewModelScope.launch {
            when (val result = routineRepository.deleteDay(userId, routineId, dayId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> reportFailure(result.error)
            }
        }
    }

    private suspend fun reportFailure(error: AppError) {
        eventChannel.send(RoutineDetailEvent.ActionFailed(error.toMessageRes()))
    }

    private fun buildDayGroups(routine: RoutineDetail): List<RoutineDayGroup> {
        val namedGroups = routine.days.sortedWith(RoutineOrdering.dayComparator).map { day ->
            RoutineDayGroup(day = day, exercises = routine.exercisesForDay(day.dayNumber))
        }
        val unassigned = routine.unassignedExercises
        return if (unassigned.isNotEmpty()) namedGroups + RoutineDayGroup(day = null, exercises = unassigned) else namedGroups
    }
}

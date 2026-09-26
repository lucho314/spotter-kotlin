package com.lucho314.spotter.feature.routines.addexercise

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.calc.RoutineOrdering
import com.lucho314.spotter.domain.calc.Validators
import com.lucho314.spotter.domain.model.Exercise
import com.lucho314.spotter.domain.model.MuscleGroup
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.ExerciseRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.feature.common.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddExerciseItem(val exercise: Exercise, val alreadyAdded: Boolean)

/** The exercise currently being configured in the sets/reps/rest sheet, with its editable defaults. */
data class ConfiguringExercise(val exercise: Exercise, val sets: Int = 3, val reps: Int = 10, val restSeconds: Int = 90)

data class AddExerciseUiState(
    val loading: Boolean = true,
    val searchQuery: String = "",
    val selectedMuscleGroupId: Int? = null,
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val items: List<AddExerciseItem> = emptyList(),
    val configuring: ConfiguringExercise? = null,
    val adding: Boolean = false,
    /**
     * `false` until [RoutineDetail] has emitted at least once for this routine (review issue): the
     * "already added"/`sort_order` math in [TargetGroup] is meaningless against the seed empty
     * list `targetGroup` starts with, so the screen must disable "Agregar" until this is `true` -
     * otherwise a very fast tap right after opening could add a duplicate or with the wrong
     * `sort_order`.
     */
    val routineLoaded: Boolean = false,
    /** Set only when there is nothing cached to show at all and the initial catalog refresh failed. */
    @StringRes val loadErrorRes: Int? = null,
)

/** One-shot effects: navigation-worthy success, and transient action failures that shouldn't replay on rotation. */
sealed interface AddExerciseEvent {
    data class Added(val exerciseName: String) : AddExerciseEvent
    data class ActionFailed(@StringRes val messageRes: Int) : AddExerciseEvent
}

/**
 * The group of exercises this screen's "ya agregado"/`sort_order` math is scoped to, and the
 * concrete `day_number` a newly added exercise here will get.
 *
 * [RouteArgs.DAY_NUMBER] `null` means "the target is whatever this routine's 'unassigned' bucket
 * currently is" - which resolves differently depending on whether the routine has any real days
 * yet (see [RoutineOrdering.unassignedBucketDayNumber]): for a legacy/dayless routine, that bucket
 * is the *entire* routine (rendered as one flat list, RN parity), not the empty
 * [UNASSIGNED_DAY_NUMBER] slot - using the latter unconditionally (review issue) let the same
 * exercise be re-added and put new ones at the top instead of the end, since "already added"/
 * `sort_order` were computed against the wrong slice of the routine's exercises.
 */
private data class TargetGroup(val exercises: List<RoutineExercise>, val dayNumber: Int)

/**
 * Search is local/offline over the cached catalog (`ExerciseRepository.observeCatalog`), matching
 * accent- and case-insensitively (RN parity, section 10.3.4).
 */
@HiltViewModel
class AddExerciseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    // See ExerciseDetailViewModel's KDoc for why this reads the SavedStateHandle key directly
    // instead of `toRoute<AddExerciseRoute>()`. Unlike routineId, a missing/null dayNumber is a
    // valid, meaningful value here (see TargetGroup's KDoc), not a programmer error.
    private val routineId: String = checkNotNull(savedStateHandle[RouteArgs.ROUTINE_ID]) { "Missing ${RouteArgs.ROUTINE_ID}" }
    private val routeDayNumber: Int? = savedStateHandle.get<Int?>(RouteArgs.DAY_NUMBER)
    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val searchQuery = MutableStateFlow("")
    private val selectedMuscleGroupId = MutableStateFlow<Int?>(null)
    private val configuring = MutableStateFlow<ConfiguringExercise?>(null)
    private val adding = MutableStateFlow(false)
    private val initialRefreshDone = MutableStateFlow(false)
    private val initialRefreshError = MutableStateFlow<Int?>(null)

    private val eventChannel = Channel<AddExerciseEvent>(Channel.BUFFERED)
    val events: Flow<AddExerciseEvent> = eventChannel.receiveAsFlow()

    /** See [AddExerciseUiState.routineLoaded]'s KDoc. */
    private val routineDetailReady = MutableStateFlow(false)

    /** Latest snapshot of the routine, used to resolve [TargetGroup] (see its KDoc). */
    private val routineDetail: StateFlow<RoutineDetail?> =
        routineRepository.observeRoutine(userId, routineId)
            .onEach { routineDetailReady.value = true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val targetGroup: StateFlow<TargetGroup> =
        routineDetail.map(::resolveTargetGroup)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), resolveTargetGroup(null))

    private fun resolveTargetGroup(routine: RoutineDetail?): TargetGroup = when {
        routine == null -> TargetGroup(emptyList(), routeDayNumber ?: UNASSIGNED_DAY_NUMBER)
        routeDayNumber != null -> TargetGroup(routine.exercisesForDay(routeDayNumber), routeDayNumber)
        else -> TargetGroup(routine.unassignedExercises, RoutineOrdering.unassignedBucketDayNumber(routine))
    }

    private val filters = combine(searchQuery, selectedMuscleGroupId) { query, groupId -> query to groupId }
    private val sheetState = combine(configuring, adding) { conf, isAdding -> conf to isAdding }
    private val refreshState = combine(initialRefreshDone, initialRefreshError) { done, error -> done to error }
    private val targetGroupState = combine(targetGroup, routineDetailReady) { group, ready -> group to ready }

    val uiState: StateFlow<AddExerciseUiState> = combine(
        exerciseRepository.observeCatalog(),
        exerciseRepository.observeMuscleGroups(),
        targetGroupState,
        filters,
        combine(sheetState, refreshState) { sheet, refresh -> sheet to refresh },
    ) { catalog, muscleGroups, groupState, filterPair, sheetAndRefresh ->
        val (group, routineLoaded) = groupState
        val (query, groupId) = filterPair
        val (sheet, refresh) = sheetAndRefresh
        val (conf, isAdding) = sheet
        val (refreshDone, refreshError) = refresh
        // Same exercise + different day is allowed server-side (UNIQUE(routine, exercise, day)),
        // so only exercises already in the *target group* (see TargetGroup's KDoc) count as
        // "already added".
        val addedExerciseIds = group.exercises.mapTo(mutableSetOf()) { it.exerciseId }
        val normalizedQuery = normalize(query)
        val items = catalog
            .filter { groupId == null || it.muscleGroup?.id == groupId }
            .filter { normalizedQuery.isBlank() || normalize(it.name).contains(normalizedQuery) }
            .map { AddExerciseItem(it, alreadyAdded = it.id in addedExerciseIds) }
        val hasCachedCatalog = catalog.isNotEmpty()
        AddExerciseUiState(
            loading = !hasCachedCatalog && !refreshDone,
            routineLoaded = routineLoaded,
            searchQuery = query,
            selectedMuscleGroupId = groupId,
            muscleGroups = muscleGroups,
            items = items,
            configuring = conf,
            adding = isAdding,
            loadErrorRes = if (!hasCachedCatalog && refreshDone) refreshError else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExerciseUiState())

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            when (val result = exerciseRepository.refreshCatalog()) {
                is AppResult.Success -> initialRefreshError.value = null
                is AppResult.Failure -> initialRefreshError.value = result.error.toMessageRes()
            }
            initialRefreshDone.value = true
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onMuscleGroupSelected(groupId: Int?) {
        selectedMuscleGroupId.value = groupId
    }

    fun onExerciseSelected(exercise: Exercise) {
        configuring.value = ConfiguringExercise(exercise)
    }

    fun onConfigChanged(sets: Int, reps: Int, restSeconds: Int) {
        configuring.update { it?.copy(sets = sets, reps = reps, restSeconds = restSeconds) }
    }

    fun onDismissConfig() {
        configuring.value = null
    }

    fun onConfirmAdd() {
        val config = configuring.value ?: return
        // Defense in depth: the screen already disables "Agregar" until `routineLoaded`, but this
        // guards direct callers too - see `AddExerciseUiState.routineLoaded`'s KDoc for why adding
        // before the routine's real data has arrived is unsafe.
        if (adding.value || !routineDetailReady.value) return
        val validationReason = Validators.routineExercise(config.sets, config.reps, config.restSeconds)
        if (validationReason != null) {
            viewModelScope.launch { eventChannel.send(AddExerciseEvent.ActionFailed(validationReason.toMessageRes())) }
            return
        }
        adding.value = true
        viewModelScope.launch {
            val group = targetGroup.value
            val sortOrder = RoutineOrdering.nextSortOrderIn(group.exercises)
            val input = NewRoutineExercise(
                exerciseId = config.exercise.id,
                dayNumber = group.dayNumber,
                targetSets = config.sets,
                targetReps = config.reps,
                restSeconds = config.restSeconds,
            )
            when (val result = routineRepository.addExercise(userId, routineId, input, sortOrder)) {
                is AppResult.Success -> {
                    adding.value = false
                    configuring.value = null
                    eventChannel.send(AddExerciseEvent.Added(config.exercise.name))
                }

                is AppResult.Failure -> {
                    adding.value = false
                    val messageRes = if (result.error is AppError.Conflict) R.string.add_exercise_error_conflict else result.error.toMessageRes()
                    eventChannel.send(AddExerciseEvent.ActionFailed(messageRes))
                }
            }
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}

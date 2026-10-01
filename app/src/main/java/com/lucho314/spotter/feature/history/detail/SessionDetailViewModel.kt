package com.lucho314.spotter.feature.history.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.navigation.RouteArgs
import com.lucho314.spotter.domain.calc.ExerciseNote
import com.lucho314.spotter.domain.calc.ExerciseSetGrouping
import com.lucho314.spotter.domain.calc.SetInputValidation
import com.lucho314.spotter.domain.calc.SetInputValidator
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.calc.WorkoutMath
import com.lucho314.spotter.domain.model.ExportFormat
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.repository.WorkoutExportRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import com.lucho314.spotter.domain.usecase.BuildWorkoutExportUseCase
import com.lucho314.spotter.feature.common.SpotterDateFormats
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

data class ExerciseBlock(val exerciseId: Int, val name: String?, val sets: List<WorkoutSet>, val note: String? = null)

data class EditingNote(val exerciseId: Int, val exerciseName: String?, val text: String)

data class EditingSet(val setId: String, val setNumber: Int, val exerciseName: String?, val weightText: String, val repsText: String)

data class SessionDetailUiState(
    val loading: Boolean = true,
    @StringRes val loadErrorRes: Int? = null,
    val routineName: String? = null,
    val dateText: String = "",
    val durationMinutes: Long? = null,
    val volumeKg: Double = 0.0,
    val workingSetCount: Int = 0,
    val blocks: List<ExerciseBlock> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val savingSetIds: Set<String> = emptySet(),
    val addingExerciseIds: Set<Int> = emptySet(),
    val editing: EditingSet? = null,
    @StringRes val editErrorRes: Int? = null,
    val editingNote: EditingNote? = null,
    val savingNoteExerciseIds: Set<Int> = emptySet(),
    /** Non-null while [SessionDetailViewModel.onExport] is rendering that format. */
    val exporting: ExportFormat? = null,
) {
    val canShare: Boolean get() = blocks.isNotEmpty()
}

sealed interface SessionDetailEvent {
    data class ActionFailed(@StringRes val messageRes: Int) : SessionDetailEvent
    data class ShareFile(val uri: String, val mimeType: String) : SessionDetailEvent
}

/**
 * Exercise blocks are grouped by [WorkoutSet.exerciseId] (never trusting Postgrest's row order),
 * ordered by the earliest `completedAt` among their sets, tie-broken by exercise id; each block's
 * own sets are ordered by `setNumber`.
 */
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider,
    private val buildWorkoutExport: BuildWorkoutExportUseCase,
    private val workoutExportRepository: WorkoutExportRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[RouteArgs.SESSION_ID])

    private val detail = MutableStateFlow<WorkoutSessionDetail?>(null)
    private val loading = MutableStateFlow(true)
    private val loadErrorRes = MutableStateFlow<Int?>(null)
    private val savingSetIds = MutableStateFlow<Set<String>>(emptySet())
    private val addingExerciseIds = MutableStateFlow<Set<Int>>(emptySet())
    private val editing = MutableStateFlow<EditingSet?>(null)
    private val editErrorRes = MutableStateFlow<Int?>(null)
    private val exporting = MutableStateFlow<ExportFormat?>(null)
    private val editingNote = MutableStateFlow<EditingNote?>(null)
    private val savingNoteExerciseIds = MutableStateFlow<Set<Int>>(emptySet())

    private val eventChannel = Channel<SessionDetailEvent>(Channel.BUFFERED)
    val events: Flow<SessionDetailEvent> = eventChannel.receiveAsFlow()

    /** [combine] has a typed overload up to 5 flows; grouped so the whole state stays within that. */
    private data class Core(
        val detail: WorkoutSessionDetail?,
        val loading: Boolean,
        val loadErrorRes: Int?,
        val savingSetIds: Set<String>,
        val addingExerciseIds: Set<Int>,
    )

    private data class EditState(
        val editing: EditingSet?,
        val editErrorRes: Int?,
        val exporting: ExportFormat?,
        val editingNote: EditingNote?,
        val savingNoteExerciseIds: Set<Int>,
    )

    private val core = combine(detail, loading, loadErrorRes, savingSetIds, addingExerciseIds, ::Core)
    private val editState = combine(editing, editErrorRes, exporting, editingNote, savingNoteExerciseIds, ::EditState)

    val uiState: StateFlow<SessionDetailUiState> = combine(core, editState, preferencesRepository.weightUnit) { c, e, unit ->
        val d = c.detail
        SessionDetailUiState(
            loading = c.loading,
            loadErrorRes = c.loadErrorRes,
            routineName = d?.routineName,
            dateText = d?.let { SpotterDateFormats.longDay(it.completedAt ?: it.startedAt, timeProvider.zone()) }.orEmpty(),
            durationMinutes = d?.let { WorkoutMath.durationMinutes(it.startedAt, it.completedAt) },
            volumeKg = d?.sets?.let { WorkoutMath.volumeKg(it) } ?: 0.0,
            workingSetCount = d?.sets?.count { !it.isWarmup } ?: 0,
            blocks = d?.let { buildBlocks(it) } ?: emptyList(),
            weightUnit = unit,
            savingSetIds = c.savingSetIds,
            addingExerciseIds = c.addingExerciseIds,
            editing = e.editing,
            editErrorRes = e.editErrorRes,
            exporting = e.exporting,
            editingNote = e.editingNote,
            savingNoteExerciseIds = e.savingNoteExerciseIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loading.value = true
        loadErrorRes.value = null
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.getSession(sessionId)) {
                is AppResult.Success -> {
                    detail.value = result.value
                    loading.value = false
                }
                is AppResult.Failure -> {
                    loadErrorRes.value = result.error.toMessageRes()
                    loading.value = false
                }
            }
        }
    }

    /** Refetches after a successful [onAddSet], without flipping [loading] back on. */
    private fun reloadSilently() {
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.getSession(sessionId)) {
                is AppResult.Success -> detail.value = result.value
                is AppResult.Failure -> eventChannel.send(SessionDetailEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }

    private fun buildBlocks(detail: WorkoutSessionDetail): List<ExerciseBlock> =
        ExerciseSetGrouping.group(detail.sets).map { ExerciseBlock(it.exerciseId, it.exerciseName, it.sets, detail.exerciseNotes[it.exerciseId]) }

    fun onEditNote(exerciseId: Int) {
        val d = detail.value ?: return
        val sets = d.sets.filter { it.exerciseId == exerciseId }
        if (sets.isEmpty()) return
        editingNote.value = EditingNote(
            exerciseId = exerciseId,
            exerciseName = sets.firstNotNullOfOrNull { it.exerciseName },
            text = d.exerciseNotes[exerciseId].orEmpty(),
        )
    }

    fun onNoteDismiss() {
        editingNote.value = null
    }

    /** Saves remotely, then updates the shown note; a blank [text] removes the note. */
    fun onNoteConfirm(text: String) {
        val current = editingNote.value ?: return
        if (current.exerciseId in savingNoteExerciseIds.value) return
        editingNote.value = null
        val note = ExerciseNote.normalize(text)
        savingNoteExerciseIds.value += current.exerciseId
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.setExerciseNote(sessionId, current.exerciseId, note)) {
                is AppResult.Success -> detail.value?.let { d ->
                    val notes = if (note == null) d.exerciseNotes - current.exerciseId else d.exerciseNotes + (current.exerciseId to note)
                    detail.value = d.copy(exerciseNotes = notes)
                }
                is AppResult.Failure -> eventChannel.send(SessionDetailEvent.ActionFailed(result.error.toMessageRes()))
            }
            savingNoteExerciseIds.value -= current.exerciseId
        }
    }

    fun onExport(format: ExportFormat) {
        val currentDetail = detail.value ?: return
        if (exporting.value != null) return
        exporting.value = format
        viewModelScope.launch {
            try {
                val data = buildWorkoutExport(currentDetail, uiState.value.weightUnit)
                when (val result = workoutExportRepository.export(data, format)) {
                    is AppResult.Success -> eventChannel.send(SessionDetailEvent.ShareFile(result.value.uri, result.value.mimeType))
                    is AppResult.Failure -> eventChannel.send(SessionDetailEvent.ActionFailed(R.string.share_workout_error))
                }
            } finally {
                exporting.value = null
            }
        }
    }

    fun onEditSet(setId: String) {
        val set = detail.value?.sets?.firstOrNull { it.id == setId } ?: return
        val unit = uiState.value.weightUnit
        editing.value = EditingSet(
            setId = set.id,
            setNumber = set.setNumber,
            exerciseName = set.exerciseName,
            weightText = WeightConverter.format(set.weightKg, unit),
            repsText = set.reps.toString(),
        )
        editErrorRes.value = null
    }

    fun onEditDismiss() {
        editing.value = null
        editErrorRes.value = null
    }

    fun onEditConfirm(weightText: String, repsText: String) {
        val current = editing.value ?: return
        if (current.setId in savingSetIds.value) return
        val unit = uiState.value.weightUnit
        when (val validation = SetInputValidator.validate(weightText, repsText, unit)) {
            is SetInputValidation.Invalid -> editErrorRes.value = validation.reason.toMessageRes()
            is SetInputValidation.Valid -> {
                editing.value = null
                editErrorRes.value = null
                savingSetIds.value += current.setId
                viewModelScope.launch {
                    when (val result = workoutHistoryRepository.updateSet(current.setId, validation.weightKg, validation.reps)) {
                        is AppResult.Success -> updateLocalSet(current.setId, validation.weightKg, validation.reps)
                        is AppResult.Failure -> eventChannel.send(SessionDetailEvent.ActionFailed(result.error.toMessageRes()))
                    }
                    savingSetIds.value -= current.setId
                }
            }
        }
    }

    private fun updateLocalSet(setId: String, weightKg: Double, reps: Int) {
        val current = detail.value ?: return
        detail.value = current.copy(sets = current.sets.map { if (it.id == setId) it.copy(weightKg = weightKg, reps = reps) else it })
    }

    fun onDeleteSet(setId: String) {
        viewModelScope.launch {
            when (val result = workoutHistoryRepository.deleteSet(setId)) {
                is AppResult.Success -> {
                    val current = detail.value
                    if (current != null) detail.value = current.copy(sets = current.sets.filterNot { it.id == setId })
                }
                is AppResult.Failure -> eventChannel.send(SessionDetailEvent.ActionFailed(result.error.toMessageRes()))
            }
        }
    }

    fun onAddSet(exerciseId: Int) {
        if (exerciseId in addingExerciseIds.value) return
        val current = detail.value ?: return
        val last = current.sets.filter { it.exerciseId == exerciseId }.maxByOrNull { it.setNumber } ?: return
        addingExerciseIds.value += exerciseId
        viewModelScope.launch {
            val completedAt = current.completedAt ?: current.startedAt
            when (
                val result = workoutHistoryRepository.addSet(sessionId, exerciseId, last.setNumber + 1, last.weightKg, last.reps, completedAt)
            ) {
                is AppResult.Success -> reloadSilently()
                is AppResult.Failure -> eventChannel.send(SessionDetailEvent.ActionFailed(result.error.toMessageRes()))
            }
            addingExerciseIds.value -= exerciseId
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.routines.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.NumberStepper
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.SpanishWeekdays
import com.lucho314.spotter.domain.model.ActiveWorkout
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineDetail
import com.lucho314.spotter.domain.model.RoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.domain.usecase.DaySelection
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.common.routineExerciseSummary
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun RoutineDetailScreen(
    onBack: () -> Unit,
    onEditClick: (routineId: String) -> Unit,
    onAddExerciseClick: (routineId: String, dayNumber: Int?) -> Unit,
    onExerciseClick: (Int) -> Unit,
    onStartWorkoutClick: () -> Unit,
    onArchived: () -> Unit,
    /** Name of the exercise just added from [onAddExerciseClick]'s destination (nav result relayed via `SavedStateHandle`); shows a one-time "X agregado" snackbar. */
    addedExerciseName: String? = null,
    onAddedExerciseNameConsumed: () -> Unit = {},
    viewModel: RoutineDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showAddDaySheet by remember { mutableStateOf(false) }
    var dayBeingRenamed by remember { mutableStateOf<RoutineDay?>(null) }
    var dayPendingDelete by remember { mutableStateOf<RoutineDay?>(null) }
    var exercisePendingRemoval by remember { mutableStateOf<RoutineExercise?>(null) }
    var showStartDayPicker by remember { mutableStateOf(false) }
    var pendingDaySelection by remember { mutableStateOf<DaySelection?>(null) }
    var alreadyActiveWorkout by remember { mutableStateOf<ActiveWorkout?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* denied is a no-op: the alarm still falls back silently. */ }
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Lifecycle-aware: an event sent while backgrounded (e.g. `Archived`, mid network call) stays
    // buffered in the channel instead of being consumed-then-dropped - see ObserveAsEvents' KDoc.
    // `onArchived`/`onStartWorkoutClick` themselves are unguarded (no `dropUnlessResumed`): by the
    // time these run, the collection has already resumed to at least STARTED, so they can't fire
    // twice from one event.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RoutineDetailEvent.Archived -> onArchived()
            is RoutineDetailEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
            RoutineDetailEvent.WorkoutStarted -> {
                requestNotificationPermissionIfNeeded()
                onStartWorkoutClick()
            }

            is RoutineDetailEvent.WorkoutAlreadyActive -> alreadyActiveWorkout = event.existing
        }
    }

    LaunchedEffect(addedExerciseName) {
        if (addedExerciseName != null) {
            // Consumed *before* showing the snackbar (which can sit on screen for ~4s): otherwise,
            // navigating away and back while it's still up would restart this effect and show it
            // again, since the name would still be set.
            onAddedExerciseNameConsumed()
            snackbarHostState.showSnackbar(context.getString(R.string.add_exercise_added_snackbar, addedExerciseName))
        }
    }

    val shareComingSoon = stringResource(R.string.placeholder_coming_soon)
    val noExercisesMessage = stringResource(R.string.routine_detail_start_needs_exercises)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.routine?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { snackbarHostState.showSnackbar(shareComingSoon) } }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.routine_detail_share))
                    }
                    IconButton(onClick = { onAddExerciseClick(viewModel.routineId, null) }) {
                        Icon(Icons.Filled.AddCircleOutline, contentDescription = stringResource(R.string.routine_detail_add_exercise))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.routine_detail_menu_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routine_detail_menu_edit)) },
                                onClick = { showMenu = false; onEditClick(viewModel.routineId) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.routine_detail_menu_archive)) },
                                onClick = { showMenu = false; showArchiveConfirm = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val routine = uiState.routine
            if (routine != null) {
                SpotterButton(
                    text = stringResource(R.string.routine_detail_start_workout),
                    onClick = {
                        if (!uiState.canStartWorkout) {
                            scope.launch { snackbarHostState.showSnackbar(noExercisesMessage) }
                        } else if (routine.days.isEmpty()) {
                            // No days to pick from: RN parity for a flat/legacy routine - every
                            // exercise is loaded (RN bug #7 only applies once a routine has days).
                            pendingDaySelection = DaySelection.All
                            viewModel.onStartWorkout(DaySelection.All)
                        } else {
                            showStartDayPicker = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                )
            }
        },
    ) { padding ->
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.routine == null -> ErrorState(
                message = uiState.loadErrorRes?.let { stringResource(it) } ?: stringResource(R.string.error_unknown),
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding),
            )

            else -> RoutineDetailContent(
                uiState = uiState,
                onExerciseClick = onExerciseClick,
                onRemoveExerciseRequested = { exercisePendingRemoval = it },
                onEditExercise = viewModel::onUpdateExercise,
                onReorderDayGroup = viewModel::onReorderDayGroup,
                onRenameDayRequested = { dayBeingRenamed = it },
                onDeleteDayRequested = { dayPendingDelete = it },
                onAddDayClick = { showAddDaySheet = true },
                onAddExerciseToDay = { dayNumber -> onAddExerciseClick(viewModel.routineId, dayNumber) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showArchiveConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.routine_detail_archive_confirm_title),
            message = stringResource(R.string.routine_detail_archive_confirm_message, uiState.routine?.name.orEmpty()),
            onConfirm = { showArchiveConfirm = false; viewModel.onArchiveConfirmed() },
            onDismiss = { showArchiveConfirm = false },
        )
    }

    if (showAddDaySheet) {
        DayPickerDialog(
            title = stringResource(R.string.routine_detail_add_day),
            usedDayNames = uiState.usedDayNames,
            currentName = null,
            onSelect = { name -> showAddDaySheet = false; viewModel.onAddDay(name) },
            onDismiss = { showAddDaySheet = false },
        )
    }

    val renamingDay = dayBeingRenamed
    if (renamingDay != null) {
        DayPickerDialog(
            title = stringResource(R.string.routine_detail_rename_day),
            usedDayNames = uiState.usedDayNames,
            currentName = renamingDay.name,
            onSelect = { name -> dayBeingRenamed = null; viewModel.onRenameDay(renamingDay.id, name) },
            onDismiss = { dayBeingRenamed = null },
        )
    }

    val dayToDelete = dayPendingDelete
    if (dayToDelete != null) {
        val exerciseCount = uiState.routine?.exercisesForDay(dayToDelete.dayNumber)?.size ?: 0
        ConfirmDialog(
            title = stringResource(R.string.routine_detail_delete_day_confirm_title),
            message = if (exerciseCount > 0) {
                pluralStringResource(R.plurals.routine_detail_delete_day_confirm_message_with_exercises, exerciseCount, dayToDelete.name, exerciseCount)
            } else {
                stringResource(R.string.routine_detail_delete_day_confirm_message_empty, dayToDelete.name)
            },
            onConfirm = { dayPendingDelete = null; viewModel.onDeleteDay(dayToDelete.id) },
            onDismiss = { dayPendingDelete = null },
        )
    }

    val exerciseToRemove = exercisePendingRemoval
    if (exerciseToRemove != null) {
        ConfirmDialog(
            title = stringResource(R.string.routine_detail_remove_exercise_confirm_title),
            message = stringResource(
                R.string.routine_detail_remove_exercise_confirm_message,
                exerciseToRemove.exercise?.name ?: stringResource(R.string.template_exercise_unknown),
            ),
            onConfirm = { exercisePendingRemoval = null; viewModel.onRemoveExercise(exerciseToRemove.id) },
            onDismiss = { exercisePendingRemoval = null },
        )
    }

    val routineForStart = uiState.routine
    if (showStartDayPicker && routineForStart != null) {
        StartWorkoutDayPickerDialog(
            routine = routineForStart,
            todayWeekdayName = viewModel.todayWeekdayName(),
            onSelect = { day ->
                showStartDayPicker = false
                pendingDaySelection = day
                viewModel.onStartWorkout(day)
            },
            onDismiss = { showStartDayPicker = false },
        )
    }

    val existingActiveWorkout = alreadyActiveWorkout
    if (existingActiveWorkout != null) {
        AlertDialog(
            onDismissRequest = { alreadyActiveWorkout = null },
            title = { Text(stringResource(R.string.routine_detail_active_workout_title)) },
            text = { Text(stringResource(R.string.routine_detail_active_workout_message, existingActiveWorkout.routineName)) },
            confirmButton = {
                TextButton(onClick = { alreadyActiveWorkout = null; requestNotificationPermissionIfNeeded(); onStartWorkoutClick() }) {
                    Text(stringResource(R.string.routine_detail_active_workout_continue))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            alreadyActiveWorkout = null
                            pendingDaySelection?.let { viewModel.onStartWorkout(it, replaceExisting = true) }
                        },
                    ) {
                        Text(stringResource(R.string.routine_detail_active_workout_discard))
                    }
                    TextButton(onClick = { alreadyActiveWorkout = null }) {
                        Text(stringResource(R.string.generic_cancel))
                    }
                }
            },
        )
    }
}

/**
 * Lets the user pick which day's exercises to load into the new session (RN bug #7: the RN app
 * always started every exercise from every day). [SpanishWeekdays]-named days with exercises are
 * offered, plus "Sin día asignado" when there are unassigned ones; [todayWeekdayName] preselects
 * today's day if the routine actually has one by that name.
 */
@Composable
private fun StartWorkoutDayPickerDialog(
    routine: RoutineDetail,
    todayWeekdayName: String,
    onSelect: (DaySelection) -> Unit,
    onDismiss: () -> Unit,
) {
    data class Option(val label: String, val selection: DaySelection)

    val dayOptions = routine.days
        .sortedBy { SpanishWeekdays.order(it.name) }
        .filter { routine.exercisesForDay(it.dayNumber).isNotEmpty() }
        .map { Option(it.name, DaySelection.Day(it.dayNumber)) }
    val unassignedOption = if (routine.unassignedExercises.isNotEmpty()) {
        Option(stringResource(R.string.routine_detail_unassigned_day), DaySelection.Unassigned)
    } else {
        null
    }
    val options = dayOptions + listOfNotNull(unassignedOption)
    var selected by remember(options) { mutableStateOf(dayOptions.firstOrNull { it.label == todayWeekdayName } ?: options.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_detail_start_day_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                options.forEach { option ->
                    SpotterChip(label = option.label, selected = selected == option, onClick = { selected = option })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let { onSelect(it.selection) } }, enabled = selected != null) {
                Text(stringResource(R.string.routine_detail_start_workout))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

/** A row in the flattened, single-list rendering of every day section (needed so the whole screen shares one [sh.calvin.reorderable.ReorderableLazyListState]: see [RoutineDetailContent]'s KDoc). */
private sealed interface DetailRow {
    data class Description(val text: String) : DetailRow
    data class Header(val group: RoutineDayGroup) : DetailRow
    data class DayEmpty(val group: RoutineDayGroup) : DetailRow
    data class ExerciseRowData(val group: RoutineDayGroup, val exercise: RoutineExercise) : DetailRow
    data object AddDayButton : DetailRow
}

private fun RoutineDayGroup.key(): Any = day?.id ?: "unassigned"

private fun rowKey(row: DetailRow): Any = when (row) {
    is DetailRow.Description -> "description"
    is DetailRow.Header -> "header-${row.group.key()}"
    is DetailRow.DayEmpty -> "empty-${row.group.key()}"
    is DetailRow.ExerciseRowData -> row.exercise.id
    DetailRow.AddDayButton -> "add-day"
}

/**
 * [hasAnyDays] mirrors RN parity: a routine that was never organized into days at all renders its
 * exercises as a flat list (no "Sin día asignado" header) - the header only makes sense once the
 * user has *some* real days and this group is genuinely "the leftovers".
 */
private fun buildRows(uiState: RoutineDetailUiState, hasAnyDays: Boolean): List<DetailRow> {
    val rows = mutableListOf<DetailRow>()
    uiState.routine?.description?.let { rows += DetailRow.Description(it) }
    uiState.dayGroups.forEach { group ->
        if (group.day != null || hasAnyDays) {
            rows += DetailRow.Header(group)
            if (group.exercises.isEmpty()) rows += DetailRow.DayEmpty(group)
        }
        if (group.exercises.isNotEmpty()) {
            group.exercises.forEach { rows += DetailRow.ExerciseRowData(group, it) }
        }
    }
    rows += DetailRow.AddDayButton
    return rows
}

/**
 * Renders every day section (plus "sin día asignado") as ONE flat, drag-reorderable list: the
 * `sh.calvin.reorderable` library ties its drag math to a single [androidx.compose.foundation.lazy.LazyListState],
 * so nesting one `LazyColumn` per day inside this screen's outer list isn't an option (nested
 * scrollables measured with an unbounded height crash at runtime). Cross-group drags are rejected
 * in `onMove` below - `sort_order` is scoped per day - and only exercise rows, never headers or
 * the "agregar día" button, carry a drag handle.
 */
@Composable
private fun RoutineDetailContent(
    uiState: RoutineDetailUiState,
    onExerciseClick: (Int) -> Unit,
    onRemoveExerciseRequested: (RoutineExercise) -> Unit,
    onEditExercise: (String, Int, Int, Int, Int?) -> Unit,
    onReorderDayGroup: (List<String>) -> Unit,
    onRenameDayRequested: (RoutineDay) -> Unit,
    onDeleteDayRequested: (RoutineDay) -> Unit,
    onAddDayClick: () -> Unit,
    onAddExerciseToDay: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routine = uiState.routine
    // Only when there is truly nothing to organize (review issue: this used to also fire whenever
    // `exercises` was empty even with real days already set up, hiding them and making repeated
    // "Organizar por días" taps create invisible days).
    if (routine == null || (routine.days.isEmpty() && routine.exercises.isEmpty())) {
        EmptyState(
            title = stringResource(R.string.routine_detail_empty_title),
            description = stringResource(R.string.routine_detail_empty_description),
            modifier = modifier.fillMaxSize(),
            action = { SpotterButton(text = stringResource(R.string.routine_detail_organize_by_days), onClick = onAddDayClick) },
        )
        return
    }

    val hasAnyDays = routine.days.isNotEmpty()
    var rows by remember(uiState.dayGroups, uiState.reorderRevision, routine.description) { mutableStateOf(buildRows(uiState, hasAnyDays)) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromRow = rows.getOrNull(from.index)
        val toRow = rows.getOrNull(to.index)
        if (fromRow is DetailRow.ExerciseRowData && toRow is DetailRow.ExerciseRowData && fromRow.group.key() == toRow.group.key()) {
            rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    }

    LazyColumn(state = lazyListState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.xl)) {
        items(rows, key = ::rowKey) { row ->
            ReorderableItem(reorderableState, key = rowKey(row)) { _ ->
                when (row) {
                    is DetailRow.Description -> Text(
                        text = row.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotterColors.OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )

                    is DetailRow.Header -> DayGroupHeader(
                        title = row.group.day?.name ?: stringResource(R.string.routine_detail_unassigned_day),
                        onRename = row.group.day?.let { day -> { onRenameDayRequested(day) } },
                        onDelete = row.group.day?.let { day -> { onDeleteDayRequested(day) } },
                        onAddExercise = { onAddExerciseToDay(row.group.day?.dayNumber) },
                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                    )

                    is DetailRow.DayEmpty -> Text(
                        text = stringResource(R.string.routine_detail_day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotterColors.OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )

                    is DetailRow.ExerciseRowData -> ExerciseRow(
                        exercise = row.exercise,
                        days = routine.days,
                        onClick = { onExerciseClick(row.exercise.exerciseId) },
                        onRemoveRequested = { onRemoveExerciseRequested(row.exercise) },
                        onEdit = onEditExercise,
                        dragHandle = {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = stringResource(R.string.routine_detail_drag_handle),
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStopped = {
                                        val groupKey = row.group.key()
                                        val orderedIds = rows.filterIsInstance<DetailRow.ExerciseRowData>()
                                            .filter { it.group.key() == groupKey }
                                            .map { it.exercise.id }
                                        // `row.group.exercises` is this group's order as of the last
                                        // rebuild from `uiState` (i.e. before this drag) - skip the
                                        // network call entirely for a long-press that ends without
                                        // actually reordering anything.
                                        if (orderedIds != row.group.exercises.map { it.id }) {
                                            onReorderDayGroup(orderedIds)
                                        }
                                    },
                                ),
                            )
                        },
                    )

                    DetailRow.AddDayButton -> SpotterButton(
                        text = stringResource(R.string.routine_detail_add_day),
                        onClick = onAddDayClick,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayGroupHeader(
    title: String,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (onRename != null) SpotterColors.PrimaryContainer else SpotterColors.OnSurface,
            modifier = if (onRename != null) Modifier.clickable(onClick = onRename) else Modifier,
        )
        Row {
            IconButton(onClick = onAddExercise) {
                Icon(Icons.Filled.AddCircleOutline, contentDescription = stringResource(R.string.routine_detail_add_exercise))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.routine_detail_delete_day), tint = SpotterColors.Error)
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: RoutineExercise,
    days: List<RoutineDay>,
    onClick: () -> Unit,
    onRemoveRequested: () -> Unit,
    onEdit: (String, Int, Int, Int, Int?) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    var showEditSheet by remember { mutableStateOf(false) }
    SpotterCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exercise?.name ?: stringResource(R.string.template_exercise_unknown),
                    style = MaterialTheme.typography.titleSmall,
                    color = SpotterColors.OnSurface,
                    modifier = Modifier.clickable(onClick = onClick),
                )
                Text(
                    text = routineExerciseSummary(exercise.targetSets, exercise.targetReps, exercise.restSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                )
            }
            IconButton(onClick = { showEditSheet = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.generic_edit))
            }
            IconButton(onClick = onRemoveRequested) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.generic_delete), tint = SpotterColors.Error)
            }
            dragHandle()
        }
    }

    if (showEditSheet) {
        ExerciseEditDialog(
            exercise = exercise,
            days = days,
            onConfirm = { sets, reps, rest, dayNumber ->
                showEditSheet = false
                onEdit(exercise.id, sets, reps, rest, dayNumber)
            },
            onDismiss = { showEditSheet = false },
        )
    }
}

/**
 * Reused for both "agregar día" ([currentName] `null`, nothing pre-selected) and "cambiar nombre"
 * ([currentName] the day's current name: shown selected and, unlike every other used name, not
 * disabled - renaming a day to its own current name is always allowed).
 */
@Composable
private fun DayPickerDialog(
    title: String,
    usedDayNames: Set<String>,
    currentName: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SpanishWeekdays.ALL.forEach { name ->
                    val disabled = name in usedDayNames && name != currentName
                    SpotterChip(
                        label = name,
                        selected = selected == name,
                        enabled = !disabled,
                        onClick = { selected = name },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onSelect) }, enabled = selected != null) {
                Text(stringResource(R.string.generic_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

@Composable
private fun ExerciseEditDialog(
    exercise: RoutineExercise,
    days: List<RoutineDay>,
    onConfirm: (sets: Int, reps: Int, rest: Int, dayNumber: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by remember { mutableStateOf(exercise.targetSets) }
    var reps by remember { mutableStateOf(exercise.targetReps) }
    var rest by remember { mutableStateOf(exercise.restSeconds) }
    var selectedDayNumber by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_detail_edit_exercise_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberStepper(stringResource(R.string.routine_edit_sets_label), sets, { sets = it }, min = 1, max = 20)
                NumberStepper(stringResource(R.string.routine_edit_reps_label), reps, { reps = it }, min = 1, max = 100)
                NumberStepper(stringResource(R.string.routine_edit_rest_label), rest, { rest = it }, min = 15, max = 600, step = 15)
                Text(stringResource(R.string.routine_detail_move_to_day), style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SpotterChip(
                        label = stringResource(R.string.routine_detail_unassigned_day),
                        selected = selectedDayNumber == UNASSIGNED_DAY_NUMBER,
                        onClick = { selectedDayNumber = UNASSIGNED_DAY_NUMBER },
                    )
                    days.forEach { day ->
                        SpotterChip(
                            label = day.name,
                            selected = selectedDayNumber == day.dayNumber,
                            onClick = { selectedDayNumber = day.dayNumber },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sets, reps, rest, selectedDayNumber) }) { Text(stringResource(R.string.generic_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

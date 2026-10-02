@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.workout

import android.content.Context
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.CompactNumberField
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.ExerciseMedia
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes
import com.lucho314.spotter.domain.calc.ExerciseNote
import com.lucho314.spotter.domain.calc.NumberFormatter
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.calc.WorkoutMath
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.feature.common.ExerciseNoteText
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.common.targetSetsRepsSummary
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(
    onFinished: (online: Boolean) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastSessionState by viewModel.lastSessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var lastSessionExerciseId by remember { mutableStateOf<Int?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WorkoutEvent.RestFinished -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playBeep(context)
            }

            is WorkoutEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
            // No snackbar here: the Dashboard shows it after the relay (`SpotterNavHost`'s
            // `KEY_WORKOUT_FINISHED_ONLINE`) once this screen has already been popped, since a
            // snackbar attached to this screen's own SnackbarHostState would disappear with it.
            is WorkoutEvent.Finished -> onFinished(event.online)

            WorkoutEvent.Discarded -> onDiscarded()
            WorkoutEvent.NoActiveWorkout -> onDiscarded()
        }
    }

    BackHandler { showDiscardConfirm = true }

    // Backgrounding mid-keystroke (still inside the 300ms debounce window) must not risk losing
    // the edit if the process is later killed while backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val workout = uiState.workout
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            workout == null -> LoadingState(modifier = Modifier.padding(padding))
            else -> WorkoutContent(
                uiState = uiState,
                display = viewModel::display,
                noteText = viewModel::noteText,
                onNoteChange = viewModel::onNoteChange,
                onClose = { showDiscardConfirm = true },
                onFinishClick = { showFinishConfirm = true },
                onSelectExercise = viewModel::onSelectExercise,
                onShowLastSession = { exerciseId -> lastSessionExerciseId = exerciseId; viewModel.onShowLastSession(exerciseId) },
                onSkipRest = viewModel::onSkipRest,
                onWeightChange = viewModel::onWeightChange,
                onRepsChange = viewModel::onRepsChange,
                onToggleSet = viewModel::onToggleSet,
                onAddSet = viewModel::onAddSet,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showDiscardConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.workout_discard_confirm_title),
            message = stringResource(R.string.workout_discard_confirm_message),
            onConfirm = { showDiscardConfirm = false; viewModel.onDiscard() },
            onDismiss = { showDiscardConfirm = false },
        )
    }

    if (showFinishConfirm) {
        val completed = uiState.completedSetCount
        if (completed == 0) {
            ConfirmDialog(
                title = stringResource(R.string.workout_finish_empty_title),
                message = stringResource(R.string.workout_finish_empty_message),
                confirmLabel = stringResource(R.string.workout_discard_action),
                dismissLabel = stringResource(R.string.workout_finish_empty_back),
                onConfirm = { showFinishConfirm = false; viewModel.onDiscard() },
                onDismiss = { showFinishConfirm = false },
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.workout_finish_confirm_title),
                message = stringResource(R.string.workout_finish_confirm_message),
                onConfirm = { showFinishConfirm = false; viewModel.onFinish() },
                onDismiss = { showFinishConfirm = false },
            )
        }
    }

    val lastSessionExercise = lastSessionExerciseId
    if (lastSessionExercise != null && lastSessionState !is LastSessionUiState.Hidden) {
        LastSessionSheet(
            state = lastSessionState,
            weightUnit = workout?.weightUnit ?: WeightUnit.KG,
            onDismiss = { lastSessionExerciseId = null; viewModel.onDismissLastSession() },
        )
    }
}

private fun playBeep(context: Context) {
    val player = MediaPlayer.create(context, R.raw.beep) ?: return
    player.setOnCompletionListener { it.release() }
    player.start()
}

@Composable
private fun WorkoutContent(
    uiState: WorkoutUiState,
    display: (ActiveSet) -> ActiveSet,
    noteText: (ActiveExercise) -> String,
    onNoteChange: (Long, String) -> Unit,
    onClose: () -> Unit,
    onFinishClick: () -> Unit,
    onSelectExercise: (Int) -> Unit,
    onShowLastSession: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onWeightChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onToggleSet: (Long, String) -> Unit,
    onAddSet: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workout = requireNotNull(uiState.workout)
    val exercise = uiState.currentExercise ?: workout.exercises.firstOrNull()

    Column(modifier = modifier.fillMaxSize()) {
        WorkoutHeader(
            elapsedSeconds = uiState.sessionElapsedSeconds,
            completedSetCount = uiState.completedSetCount,
            online = uiState.online,
            onClose = onClose,
            onFinishClick = onFinishClick,
        )

        if (workout.exercises.size > 1) {
            ExerciseDots(
                count = workout.exercises.size,
                selectedIndex = workout.currentExerciseIndex,
                onSelect = onSelectExercise,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            )
        }

        if (exercise != null) {
            val restVisible = (uiState.restRemainingSeconds ?: 0) > 0
            // Items preceding `items(exercise.sets, ...)` below: ExerciseHeader, the optional
            // RestTimerCard, and SetTableHeader - needed to translate a set's index into its real
            // position in the list for `listState.animateScrollToItem` (see `focusNextAfterReps`).
            val setsStartIndex = 2 + if (restVisible) 1 else 0

            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current
            // Keyed by "$setId:$field": lets the reps field of one set request focus on the
            // weight field of a *different* (not-yet-composed) set further down the list, which a
            // plain per-row `remember { FocusRequester() }` couldn't reach. Never needs clearing -
            // set ids are unique for the whole session.
            val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
            fun focusRequesterFor(setId: String, field: SetInputField) =
                focusRequesters.getOrPut("$setId:$field") { FocusRequester() }

            fun focusNextAfterReps(setId: String) {
                val target = WorkoutFocusOrder.next(exercise.sets, setId, SetInputField.REPS)
                val targetIndex = target?.let { t -> exercise.sets.indexOfFirst { it.id == t.setId } } ?: -1
                if (target == null || targetIndex == -1) {
                    focusManager.clearFocus()
                    return
                }
                scope.launch {
                    listState.animateScrollToItem(setsStartIndex + targetIndex)
                    focusRequesterFor(target.setId, target.field).requestFocus()
                }
            }

            LazyColumn(
                state = listState,
                // Shrinks the list above the keyboard instead of letting it cover the lower rows -
                // `adjustResize` no longer resizes the window under edge-to-edge (targetSdk 36 on
                // Android 15+), so this is what takes its place.
                modifier = Modifier.weight(1f).imePadding(),
                contentPadding = PaddingValues(Spacing.xl),
            ) {
                item {
                    ExerciseHeader(
                        exercise = exercise,
                        index = workout.currentExerciseIndex,
                        total = workout.exercises.size,
                        note = noteText(exercise),
                        onNoteChange = { text -> onNoteChange(exercise.rowId, text) },
                        onShowLastSession = { onShowLastSession(exercise.exerciseId) },
                    )
                }
                val rest = uiState.restRemainingSeconds
                if (restVisible && rest != null) {
                    item {
                        RestTimerCard(remainingSeconds = rest, onSkip = onSkipRest, modifier = Modifier.padding(vertical = Spacing.sm))
                    }
                }
                item {
                    SetTableHeader(weightUnit = workout.weightUnit)
                }
                items(exercise.sets, key = { it.id }) { set ->
                    val displayed = display(set)
                    val repsHasNext = WorkoutFocusOrder.next(exercise.sets, set.id, SetInputField.REPS) != null
                    SetRow(
                        set = displayed,
                        weightUnit = workout.weightUnit,
                        onWeightChange = { text -> onWeightChange(set.id, text) },
                        onRepsChange = { text -> onRepsChange(set.id, text) },
                        onToggle = { onToggleSet(exercise.rowId, set.id) },
                        weightFocusRequester = focusRequesterFor(set.id, SetInputField.WEIGHT),
                        repsFocusRequester = focusRequesterFor(set.id, SetInputField.REPS),
                        repsImeAction = if (repsHasNext) ImeAction.Next else ImeAction.Done,
                        onWeightNext = { focusRequesterFor(set.id, SetInputField.REPS).requestFocus() },
                        onRepsAdvance = { focusNextAfterReps(set.id) },
                    )
                }
                item {
                    AddSetButton(onClick = { onAddSet(exercise.rowId) }, modifier = Modifier.padding(top = Spacing.sm))
                }
                item {
                    ExerciseNavigation(
                        canGoBack = workout.currentExerciseIndex > 0,
                        canGoForward = workout.currentExerciseIndex < workout.exercises.lastIndex,
                        onPrevious = { onSelectExercise(workout.currentExerciseIndex - 1) },
                        onNext = { onSelectExercise(workout.currentExerciseIndex + 1) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutHeader(
    elapsedSeconds: Long,
    completedSetCount: Int,
    online: Boolean,
    onClose: () -> Unit,
    onFinishClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workout_close), tint = SpotterColors.OnSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = formatElapsed(elapsedSeconds), style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurfaceVariant)
                // RN only shows this once at least one set is registered - not a fixed "0 series
                // registradas" subtitle (matches the target screenshot, which shows nothing here).
                if (completedSetCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.workout_sets_registered, completedSetCount, completedSetCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotterColors.PrimaryContainer,
                    )
                }
            }
            FinishButton(onClick = onFinishClick)
        }
        if (!online) {
            Box(modifier = Modifier.fillMaxWidth().background(SpotterColors.SurfaceHigh).padding(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.workout_offline_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = SpotterColors.OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * "Finalizar": a pill button with a dark surface background and cyan text - RN's `Button
 * variant="secondary" size="sm"`. Not [SpotterButtonVariant.Secondary] (outlined, transparent,
 * `onSurface` text): that variant is shared with other screens and looks different in RN too, so
 * reusing/changing it here would drift those screens instead of just matching this one.
 */
@Composable
private fun FinishButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(SpotterShapes.Button)
            .background(SpotterColors.SurfaceHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.workout_finish_action), style = MaterialTheme.typography.titleMedium, color = SpotterColors.Secondary)
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ExerciseDots(count: Int, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // RN: 6dp dots, 6dp gap, centered; the active one widens into a 20dp pill instead of growing
    // taller. The 48dp touch target box is layered on top purely for accessibility/reachability -
    // it isn't part of the RN look.
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        repeat(count) { index ->
            val label = stringResource(R.string.workout_select_exercise_dot, index + 1)
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp) // touch target, well above the visual dot itself
                    .clickable(role = Role.Button) { onSelect(index) }
                    .semantics { contentDescription = label },
            ) {
                Box(
                    modifier = Modifier
                        .size(width = if (selected) 20.dp else 6.dp, height = 6.dp)
                        .background(if (selected) SpotterColors.PrimaryContainer else SpotterColors.OutlineVariant, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun ExerciseHeader(
    exercise: ActiveExercise,
    index: Int,
    total: Int,
    note: String,
    onNoteChange: (String) -> Unit,
    onShowLastSession: () -> Unit,
) {
    // Hidden by default (most sets get no note); keyed by exercise so moving to another one starts
    // closed again.
    var noteEditorOpen by rememberSaveable(exercise.rowId) { mutableStateOf(false) }
    val hasNote = note.isNotBlank()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.workout_exercise_index, index + 1, total),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = SpotterColors.OnSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = exercise.name, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                NoteButton(hasNote = hasNote, onClick = { noteEditorOpen = !noteEditorOpen })
                HistoryButton(onClick = onShowLastSession)
            }
        }
        Text(
            text = targetSetsRepsSummary(exercise.targetSets, exercise.targetReps),
            style = MaterialTheme.typography.bodyMedium,
            color = SpotterColors.Secondary,
        )
        if (noteEditorOpen) {
            ExerciseNoteField(value = note, onValueChange = onNoteChange, onDone = { noteEditorOpen = false })
        } else if (hasNote) {
            ExerciseNoteText(note = note, modifier = Modifier.clickable(role = Role.Button) { noteEditorOpen = true })
        }
        // No background box (RN's exercise image sits directly on the screen, centered at its
        // natural aspect ratio - `showBackground = false`), bounded to a fixed height like RN's
        // `height: 200`.
        ExerciseMedia(
            mediaUrl = exercise.mediaUrl,
            imageUrl = exercise.imageUrl,
            showBackground = false,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

/** Same look as [HistoryButton]; lime instead of cyan once the exercise has a note. */
@Composable
private fun NoteButton(hasNote: Boolean, onClick: () -> Unit) {
    val tint = if (hasNote) SpotterColors.PrimaryContainer else SpotterColors.Secondary
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(SpotterShapes.CompactField)
            .background(tint.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.EditNote,
            contentDescription = stringResource(if (hasNote) R.string.workout_note_edit else R.string.workout_note_add),
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ExerciseNoteField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    SpotterTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.workout_note_placeholder),
        supportingText = stringResource(R.string.workout_note_counter, value.length, ExerciseNote.MAX_LENGTH),
        keyboardCapitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

/** RN's cyan clock icon in a translucent-cyan rounded square (`historyBtn`). */
@Composable
private fun HistoryButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(SpotterShapes.CompactField)
            .background(SpotterColors.Secondary.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.AccessTime,
            contentDescription = stringResource(R.string.workout_last_session),
            tint = SpotterColors.Secondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RestTimerCard(remainingSeconds: Int, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    SpotterCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = stringResource(R.string.workout_rest_label), style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
                Text(
                    text = stringResource(R.string.workout_rest_seconds_format, remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = SpotterColors.Secondary,
                )
            }
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.workout_rest_skip))
            }
        }
    }
}

/** Column header text ("KG"/"REPS") centered over [SetRow]'s inputs: mirrors its inner structure
 * (fixed-width "#" column, then a `weight(1f)` group with an invisible "×"-sized spacer) so both
 * rows line up exactly. */
@Composable
private fun SetTableHeader(weightUnit: WeightUnit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "#", style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (weightUnit == WeightUnit.KG) stringResource(R.string.workout_unit_kg) else stringResource(R.string.workout_unit_lb),
                style = MaterialTheme.typography.labelMedium,
                color = SpotterColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(text = SEPARATOR, style = MaterialTheme.typography.bodyLarge, color = Color.Transparent)
            Text(
                text = stringResource(R.string.workout_reps_header),
                style = MaterialTheme.typography.labelMedium,
                color = SpotterColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Box(modifier = Modifier.size(40.dp)) {}
    }
}

private const val SEPARATOR = "×"

@Composable
private fun SetRow(
    set: ActiveSet,
    weightUnit: WeightUnit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onToggle: () -> Unit,
    weightFocusRequester: FocusRequester,
    repsFocusRequester: FocusRequester,
    repsImeAction: ImeAction,
    onWeightNext: () -> Unit,
    onRepsAdvance: () -> Unit,
) {
    val completed = set.isCompleted
    // Row-local: brings whichever of this row's fields just got focus back into view above the
    // keyboard (including the last rows, which the keyboard would otherwise cover entirely) -
    // unlike the cross-row FocusRequesters above, this never needs to be reached from elsewhere.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val weightPlaceholder = stringResource(if (weightUnit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)
    val repsPlaceholder = stringResource(R.string.workout_reps_placeholder)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clip(SpotterShapes.Input)
            .background(if (completed) SpotterColors.PrimaryContainer.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = Spacing.xxs, vertical = Spacing.xs)
            .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        Text(
            text = set.setNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = SpotterColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp),
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (completed) {
                CompletedSetValue(text = set.weightText, modifier = Modifier.weight(1f))
            } else {
                CompactNumberField(
                    value = set.weightText,
                    onValueChange = onWeightChange,
                    placeholder = weightPlaceholder,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { onWeightNext() }),
                    modifier = Modifier.weight(1f)
                        .focusRequester(weightFocusRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() } },
                )
            }
            Text(text = SEPARATOR, style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurfaceVariant)
            if (completed) {
                CompletedSetValue(text = set.repsText, modifier = Modifier.weight(1f))
            } else {
                CompactNumberField(
                    value = set.repsText,
                    onValueChange = onRepsChange,
                    placeholder = repsPlaceholder,
                    keyboardType = KeyboardType.Number,
                    imeAction = repsImeAction,
                    keyboardActions = KeyboardActions(onNext = { onRepsAdvance() }, onDone = { onRepsAdvance() }),
                    modifier = Modifier.weight(1f)
                        .focusRequester(repsFocusRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { bringIntoViewRequester.bringIntoView() } },
                )
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                contentDescription = stringResource(if (completed) R.string.workout_set_uncomplete else R.string.workout_set_complete),
                tint = if (completed) SpotterColors.PrimaryContainer else SpotterColors.OnSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** A completed set's read-only weight/reps text (RN's `valueDone`: same size as the input it replaces, tinted lime). */
@Composable
private fun CompletedSetValue(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
        color = SpotterColors.PrimaryContainer,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** "+ Agregar serie": a dashed rounded outline (RN has no filled/bordered Button variant that looks like this). */
@Composable
private fun AddSetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(color = SpotterColors.OutlineVariant, shape = SpotterShapes.Input)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = SpotterColors.Secondary, modifier = Modifier.size(18.dp))
        Text(text = stringResource(R.string.workout_add_set), style = MaterialTheme.typography.labelLarge, color = SpotterColors.Secondary)
    }
}

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape, strokeWidth: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
    val radius = shape.topStart.toPx(size, this)
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius, radius))
}

@Composable
private fun ExerciseNavigation(canGoBack: Boolean, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (canGoBack) {
            SpotterButton(
                text = stringResource(R.string.workout_previous),
                onClick = onPrevious,
                variant = SpotterButtonVariant.Ghost,
            )
        } else {
            Box(modifier = Modifier) {}
        }
        if (canGoForward) {
            SpotterButton(
                text = stringResource(R.string.workout_next),
                onClick = onNext,
                variant = SpotterButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun LastSessionSheet(state: LastSessionUiState, weightUnit: WeightUnit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.xl)) {
            Text(text = stringResource(R.string.workout_last_session_title), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            when (state) {
                LastSessionUiState.Hidden -> Unit
                LastSessionUiState.Loading -> LoadingState(modifier = Modifier.height(120.dp))
                is LastSessionUiState.Error -> Text(text = stringResource(state.messageRes), color = SpotterColors.OnSurfaceVariant)
                is LastSessionUiState.Loaded -> {
                    val session = state.session
                    if (session == null) {
                        Text(text = stringResource(R.string.workout_last_session_empty), color = SpotterColors.OnSurfaceVariant)
                    } else {
                        if (state.dateText != null) {
                            Text(
                                text = state.dateText,
                                style = MaterialTheme.typography.labelMedium,
                                color = SpotterColors.OnSurfaceVariant,
                                modifier = Modifier.padding(bottom = Spacing.xs),
                            )
                        }
                        if (session.note != null) {
                            ExerciseNoteText(note = session.note, modifier = Modifier.padding(bottom = Spacing.sm))
                        }
                        // Already ordered by setNumber (WorkoutHistoryRepositoryImpl.getLastSession).
                        session.sets.forEach { set ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "#${set.setNumber}", color = SpotterColors.OnSurfaceVariant)
                                Text(text = WeightConverter.format(set.weightKg, weightUnit), color = SpotterColors.OnSurface)
                                Text(text = "${set.reps}", color = SpotterColors.OnSurface)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = stringResource(R.string.workout_last_session_total_volume), color = SpotterColors.OnSurfaceVariant)
                            Text(
                                text = NumberFormatter.formatVolume(WorkoutMath.volumeKg(session.sets), weightUnit),
                                style = MaterialTheme.typography.labelLarge,
                                color = SpotterColors.OnSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.workout

import android.content.Context
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.ExerciseMedia
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.model.ActiveExercise
import com.lucho314.spotter.domain.model.ActiveSet
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.common.targetSetsRepsSummary
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(
    onFinished: () -> Unit,
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
            is WorkoutEvent.Finished -> scope.launch {
                val message = if (event.online) {
                    context.getString(R.string.workout_finished_online)
                } else {
                    context.getString(R.string.workout_finished_offline)
                }
                snackbarHostState.showSnackbar(message)
                onFinished()
            }

            WorkoutEvent.Discarded -> onDiscarded()
            WorkoutEvent.NoActiveWorkout -> onDiscarded()
        }
    }

    BackHandler { showDiscardConfirm = true }

    val workout = uiState.workout
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            workout == null -> LoadingState(modifier = Modifier.padding(padding))
            else -> WorkoutContent(
                uiState = uiState,
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
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(Spacing.xl)) {
                item {
                    ExerciseHeader(
                        exercise = exercise,
                        index = workout.currentExerciseIndex,
                        total = workout.exercises.size,
                        onShowLastSession = { onShowLastSession(exercise.exerciseId) },
                    )
                }
                val rest = uiState.restRemainingSeconds
                if (rest != null && rest > 0) {
                    item {
                        RestTimerCard(remainingSeconds = rest, onSkip = onSkipRest, modifier = Modifier.padding(vertical = Spacing.sm))
                    }
                }
                item {
                    SetTableHeader(weightUnit = workout.weightUnit)
                }
                items(exercise.sets, key = { it.id }) { set ->
                    val displayed = uiState.display(set)
                    SetRow(
                        set = displayed,
                        onWeightChange = { text -> onWeightChange(set.id, text) },
                        onRepsChange = { text -> onRepsChange(set.id, text) },
                        onToggle = { onToggleSet(exercise.rowId, set.id) },
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
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workout_close))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = formatElapsed(elapsedSeconds), style = MaterialTheme.typography.titleLarge, color = SpotterColors.OnSurface)
                Text(
                    text = pluralStringResource(R.plurals.workout_sets_registered, completedSetCount, completedSetCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = SpotterColors.OnSurfaceVariant,
                )
            }
            TextButton(onClick = onFinishClick) {
                Text(stringResource(R.string.workout_finish_action))
            }
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterHorizontally)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == selectedIndex) 10.dp else 8.dp)
                    .background(if (index == selectedIndex) SpotterColors.PrimaryContainer else SpotterColors.OutlineVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun ExerciseHeader(exercise: ActiveExercise, index: Int, total: Int, onShowLastSession: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Text(
            text = stringResource(R.string.workout_exercise_index, index + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = SpotterColors.OnSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = exercise.name, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = onShowLastSession) {
                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.workout_last_session))
            }
        }
        Text(
            text = targetSetsRepsSummary(exercise.targetSets, exercise.targetReps),
            style = MaterialTheme.typography.bodyMedium,
            color = SpotterColors.OnSurfaceVariant,
        )
        ExerciseMedia(
            mediaUrl = exercise.mediaUrl,
            imageUrl = exercise.imageUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun RestTimerCard(remainingSeconds: Int, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    SpotterCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = stringResource(R.string.workout_rest_label), style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
                Text(text = "${remainingSeconds}s", style = MaterialTheme.typography.displaySmall, color = SpotterColors.Secondary)
            }
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.workout_rest_skip))
            }
        }
    }
}

@Composable
private fun SetTableHeader(weightUnit: WeightUnit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "#", style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant, modifier = Modifier.weight(0.5f))
        Text(
            text = if (weightUnit == WeightUnit.KG) stringResource(R.string.workout_unit_kg) else stringResource(R.string.workout_unit_lb),
            style = MaterialTheme.typography.labelMedium,
            color = SpotterColors.OnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = stringResource(R.string.workout_reps_header), style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(40.dp)) {}
    }
}

@Composable
private fun SetRow(set: ActiveSet, onWeightChange: (String) -> Unit, onRepsChange: (String) -> Unit, onToggle: () -> Unit) {
    val completed = set.isCompleted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (completed) SpotterColors.PrimaryContainer.copy(alpha = 0.06f) else SpotterColors.Background)
            .padding(vertical = Spacing.xxs),
    ) {
        Text(text = set.setNumber.toString(), style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurfaceVariant, modifier = Modifier.weight(0.5f))
        if (completed) {
            Text(text = set.weightText, style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurface, modifier = Modifier.weight(1f))
            Text(text = set.repsText, style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurface, modifier = Modifier.weight(1f))
        } else {
            SpotterTextField(
                value = set.weightText,
                onValueChange = onWeightChange,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f).padding(end = Spacing.xxs),
            )
            SpotterTextField(
                value = set.repsText,
                onValueChange = onRepsChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f).padding(end = Spacing.xxs),
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(if (completed) R.string.workout_set_uncomplete else R.string.workout_set_complete),
                tint = if (completed) SpotterColors.PrimaryContainer else SpotterColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddSetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpotterButton(
        text = stringResource(R.string.workout_add_set),
        onClick = onClick,
        variant = SpotterButtonVariant.Secondary,
        modifier = modifier.fillMaxWidth(),
    )
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
                        session.sets.forEach { set ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "#${set.setNumber}", color = SpotterColors.OnSurfaceVariant)
                                Text(text = WeightConverter.format(set.weightKg, weightUnit), color = SpotterColors.OnSurface)
                                Text(text = "${set.reps}", color = SpotterColors.OnSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

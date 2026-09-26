@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.routines.addexercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.NumberStepper
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.MuscleGroup
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.exercise.labelRes
import kotlinx.coroutines.launch

@Composable
fun AddExerciseScreen(
    onBack: () -> Unit,
    /** Called with the added exercise's name right before navigating back, so the previous screen can show a "X agregado" snackbar. */
    onExerciseAdded: (String) -> Unit = { onBack() },
    viewModel: AddExerciseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Lifecycle-aware: an event sent while backgrounded stays buffered in the channel instead of
    // being consumed-then-dropped - see ObserveAsEvents' KDoc.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AddExerciseEvent.Added -> onExerciseAdded(event.exerciseName)
            is AddExerciseEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_exercise_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val loadErrorRes = uiState.loadErrorRes
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            loadErrorRes != null -> ErrorState(
                message = stringResource(loadErrorRes),
                onRetry = viewModel::refreshCatalog,
                modifier = Modifier.padding(padding),
            )

            else -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                SpotterTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = stringResource(R.string.add_exercise_search_placeholder),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                )
                MuscleGroupFilter(
                    groups = uiState.muscleGroups,
                    selectedGroupId = uiState.selectedMuscleGroupId,
                    onSelect = viewModel::onMuscleGroupSelected,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(uiState.items, key = { it.exercise.id }) { item ->
                        ExerciseListItem(item = item, onClick = { if (!item.alreadyAdded) viewModel.onExerciseSelected(item.exercise) })
                    }
                }
            }
        }
    }

    val configuring = uiState.configuring
    if (configuring != null) {
        ExerciseConfigDialog(
            configuring = configuring,
            // Disabled until the routine's real data has arrived (see
            // AddExerciseUiState.routineLoaded's KDoc), not just while an add is already in flight.
            confirmEnabled = !uiState.adding && uiState.routineLoaded,
            onSetsChange = { viewModel.onConfigChanged(it, configuring.reps, configuring.restSeconds) },
            onRepsChange = { viewModel.onConfigChanged(configuring.sets, it, configuring.restSeconds) },
            onRestChange = { viewModel.onConfigChanged(configuring.sets, configuring.reps, it) },
            onConfirm = viewModel::onConfirmAdd,
            onDismiss = viewModel::onDismissConfig,
        )
    }
}

@Composable
private fun MuscleGroupFilter(groups: List<MuscleGroup>, selectedGroupId: Int?, onSelect: (Int?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            SpotterChip(label = stringResource(R.string.add_exercise_all_groups), selected = selectedGroupId == null, onClick = { onSelect(null) })
        }
        items(groups, key = { it.id }) { group ->
            SpotterChip(label = group.name, selected = selectedGroupId == group.id, onClick = { onSelect(group.id) })
        }
    }
}

@Composable
private fun ExerciseListItem(item: AddExerciseItem, onClick: () -> Unit) {
    SpotterCard(onClick = if (item.alreadyAdded) null else onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.exercise.name, style = MaterialTheme.typography.titleSmall, color = SpotterColors.OnSurface)
                Text(
                    text = stringResource(
                        R.string.add_exercise_muscle_and_equipment,
                        item.exercise.muscleGroup?.name ?: "",
                        stringResource(item.exercise.equipment.labelRes()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                )
            }
            if (item.alreadyAdded) {
                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.add_exercise_already_added), tint = SpotterColors.PrimaryContainer)
            }
        }
    }
}

@Composable
private fun ExerciseConfigDialog(
    configuring: ConfiguringExercise,
    confirmEnabled: Boolean,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(configuring.exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberStepper(label = stringResource(R.string.routine_edit_sets_label), value = configuring.sets, onValueChange = onSetsChange, min = 1, max = 20)
                NumberStepper(label = stringResource(R.string.routine_edit_reps_label), value = configuring.reps, onValueChange = onRepsChange, min = 1, max = 100)
                NumberStepper(
                    label = stringResource(R.string.routine_edit_rest_label),
                    value = configuring.restSeconds,
                    onValueChange = onRestChange,
                    min = 15,
                    max = 600,
                    step = 15,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(stringResource(R.string.add_exercise_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

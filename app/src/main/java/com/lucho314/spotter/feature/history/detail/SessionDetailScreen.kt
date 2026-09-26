@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.history.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.component.StatCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.NumberFormatter
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.calc.WorkoutMath
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(onBack: () -> Unit, viewModel: SessionDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var setToDelete by remember { mutableStateOf<WorkoutSet?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SessionDetailEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.routineName ?: stringResource(R.string.history_free_workout), maxLines = 1) },
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
            loadErrorRes != null -> ErrorState(message = stringResource(loadErrorRes), onRetry = viewModel::retry, modifier = Modifier.padding(padding))
            else -> SessionDetailContent(
                uiState = uiState,
                onEditSet = viewModel::onEditSet,
                onDeleteSetClick = { setToDelete = it },
                onAddSet = viewModel::onAddSet,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }

    val editing = uiState.editing
    if (editing != null) {
        EditSetDialog(
            editing = editing,
            weightUnit = uiState.weightUnit,
            errorRes = uiState.editErrorRes,
            onDismiss = viewModel::onEditDismiss,
            onConfirm = viewModel::onEditConfirm,
        )
    }

    val pendingDelete = setToDelete
    if (pendingDelete != null) {
        val exerciseName = pendingDelete.exerciseName ?: stringResource(R.string.session_detail_unknown_exercise)
        ConfirmDialog(
            title = stringResource(R.string.session_detail_delete_set_confirm_title),
            message = stringResource(R.string.session_detail_delete_set_confirm_message, pendingDelete.setNumber, exerciseName),
            onConfirm = { viewModel.onDeleteSet(pendingDelete.id); setToDelete = null },
            onDismiss = { setToDelete = null },
        )
    }
}

@Composable
private fun SessionDetailContent(
    uiState: SessionDetailUiState,
    onEditSet: (String) -> Unit,
    onDeleteSetClick: (WorkoutSet) -> Unit,
    onAddSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                StatCard(label = stringResource(R.string.session_detail_duration), value = WorkoutMath.formatDuration(uiState.durationMinutes), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.session_detail_volume), value = NumberFormatter.formatVolume(uiState.volumeKg, uiState.weightUnit), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.session_detail_sets), value = uiState.workingSetCount.toString(), modifier = Modifier.weight(1f))
            }
        }
        items(uiState.blocks, key = { it.exerciseId }) { block ->
            ExerciseBlockCard(
                block = block,
                weightUnit = uiState.weightUnit,
                savingSetIds = uiState.savingSetIds,
                adding = block.exerciseId in uiState.addingExerciseIds,
                onEditSet = onEditSet,
                onDeleteSetClick = onDeleteSetClick,
                onAddSet = { onAddSet(block.exerciseId) },
            )
        }
    }
}

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlock,
    weightUnit: WeightUnit,
    savingSetIds: Set<String>,
    adding: Boolean,
    onEditSet: (String) -> Unit,
    onDeleteSetClick: (WorkoutSet) -> Unit,
    onAddSet: () -> Unit,
) {
    SpotterCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = block.name ?: stringResource(R.string.session_detail_unknown_exercise), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        block.sets.forEach { set -> WorkoutSetRow(set, weightUnit, saving = set.id in savingSetIds, onEditClick = { onEditSet(set.id) }, onDeleteClick = { onDeleteSetClick(set) }) }
        TextButton(onClick = onAddSet, enabled = !adding) {
            if (adding) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text(stringResource(R.string.workout_add_set))
            }
        }
    }
}

@Composable
private fun WorkoutSetRow(set: WorkoutSet, weightUnit: WeightUnit, saving: Boolean, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val unitLabel = if (weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)
        val repsLabel = pluralStringResource(R.plurals.reps_count, set.reps, set.reps)
        val warmupSuffix = if (set.isWarmup) " · ${stringResource(R.string.session_detail_warmup)}" else ""
        Text(
            text = "#${set.setNumber} · ${WeightConverter.format(set.weightKg, weightUnit)} $unitLabel · $repsLabel$warmupSuffix",
            style = MaterialTheme.typography.bodyLarge,
            color = SpotterColors.OnSurface,
        )
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.session_detail_edit_set_cd))
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.session_detail_delete_set_cd))
                }
            }
        }
    }
}

@Composable
private fun EditSetDialog(
    editing: EditingSet,
    weightUnit: WeightUnit,
    errorRes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var weightText by remember(editing.setId) { mutableStateOf(editing.weightText) }
    var repsText by remember(editing.setId) { mutableStateOf(editing.repsText) }
    val unitLabel = if (weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_detail_edit_set_title, editing.setNumber)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SpotterTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = stringResource(R.string.session_detail_weight_label, unitLabel),
                    keyboardType = KeyboardType.Decimal,
                    isError = errorRes != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpotterTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = stringResource(R.string.workout_reps_header),
                    keyboardType = KeyboardType.Number,
                    isError = errorRes != null,
                    supportingText = errorRes?.let { stringResource(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weightText, repsText) }) { Text(stringResource(R.string.generic_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.generic_cancel)) }
        },
    )
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.routines.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun RoutineEditScreen(
    onBack: () -> Unit,
    onTemplatesClick: () -> Unit,
    onSaved: (routineId: String) -> Unit,
    viewModel: RoutineEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Lifecycle-aware: `Saved` can arrive while backgrounded (e.g. switching apps mid-save) - it
    // must stay buffered instead of being consumed-then-dropped by `onSaved` - see
    // ObserveAsEvents' KDoc. `onSaved` itself is unguarded (no `dropUnlessResumed`) for the same
    // reason.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RoutineEditEvent.Saved -> onSaved(event.routineId)
            is RoutineEditEvent.SaveFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.isEditing) R.string.routine_edit_title_edit else R.string.routine_edit_title_create)) },
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
                onRetry = viewModel::retryLoad,
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!uiState.isEditing && uiState.templateCount != null) {
                    SpotterCard(onClick = onTemplatesClick, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.routine_edit_templates_banner, uiState.templateCount ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotterColors.OnSurface,
                        )
                    }
                }
                SpotterTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(R.string.routine_edit_name_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                SpotterTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = stringResource(R.string.routine_edit_description_label),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                DaysPerWeekSelector(selected = uiState.daysPerWeek, onSelect = viewModel::onDaysPerWeekChange)
                SpotterButton(
                    text = stringResource(R.string.generic_save),
                    onClick = viewModel::onSaveClick,
                    enabled = !uiState.saving,
                    loading = uiState.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Days-per-week is genuinely optional (a routine with no fixed weekly schedule is valid): tapping
 * the already-selected count clears it back to "sin especificar", instead of the old
 * `NumberStepper` defaulting the *display* to 1 while actually saving `null` - indistinguishable
 * from the user having picked 1 on purpose, and with no way back to "unset" (review issue).
 */
@Composable
private fun DaysPerWeekSelector(selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.routine_edit_days_per_week_label),
            style = MaterialTheme.typography.labelMedium,
            color = SpotterColors.OnSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            (1..7).forEach { days ->
                val isSelected = selected == days
                SpotterChip(label = days.toString(), selected = isSelected, onClick = { onSelect(if (isSelected) null else days) })
            }
        }
    }
}

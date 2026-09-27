@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.routines.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.SpanishWeekdays
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.feature.common.ActiveWorkoutBanner
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun RoutinesScreen(
    onCreateClick: () -> Unit,
    onRoutineClick: (String) -> Unit,
    onTemplatesClick: () -> Unit,
    onArchivedClick: () -> Unit,
    onImportCode: (String) -> Unit,
    onImportImageClick: () -> Unit,
    onResumeWorkoutClick: () -> Unit,
    /** `true` right after adopting a template's routines (nav result relayed via `SavedStateHandle`); shows a one-time success snackbar. */
    justCreatedRoutinesFromTemplate: Boolean = false,
    onJustCreatedRoutinesConsumed: () -> Unit = {},
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var routineToArchive by remember { mutableStateOf<RoutineSummary?>(null) }
    var importCodeError by remember { mutableStateOf(false) }

    // Lifecycle-aware: an event sent while backgrounded stays buffered in the channel instead of
    // being consumed-then-dropped - see ObserveAsEvents' KDoc.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RoutinesEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    LaunchedEffect(justCreatedRoutinesFromTemplate) {
        if (justCreatedRoutinesFromTemplate) {
            // Consumed *before* showing the snackbar (which can sit on screen for ~4s): otherwise,
            // navigating away and back while it's still up would restart this effect and show it
            // again, since the flag would still be set.
            onJustCreatedRoutinesConsumed()
            snackbarHostState.showSnackbar(context.getString(R.string.templates_routines_created))
        }
    }

    // Refreshes when this screen becomes visible again, e.g. returning from routine detail after
    // adding/editing exercises there (the cached routine summary's exercise count would otherwise
    // go stale, since only routine-level mutations refresh `routines:active`).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshOnResume()
        onPauseOrDispose {}
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            RoutinesHeader(
                onImportCode = { code ->
                    val parsed = viewModel.parseImportCode(code)
                    if (parsed != null) {
                        importCodeError = false
                        onImportCode(parsed.value)
                    } else {
                        importCodeError = true
                    }
                },
                importCodeError = importCodeError,
                onImportImageClick = onImportImageClick,
                onCreateClick = onCreateClick,
            )

            val activeWorkoutRoutineName = uiState.activeWorkoutRoutineName
            if (activeWorkoutRoutineName != null) {
                ActiveWorkoutBanner(
                    routineName = activeWorkoutRoutineName,
                    onClick = onResumeWorkoutClick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                )
            }

            val loadErrorRes = uiState.loadErrorRes
            when {
                uiState.loading -> LoadingState(modifier = Modifier.weight(1f))
                loadErrorRes != null -> ErrorState(
                    message = stringResource(loadErrorRes),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                )

                else -> PullToRefreshBox(
                    isRefreshing = uiState.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(contentPadding = PaddingValues(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        item {
                            TemplatesEntryCard(onClick = onTemplatesClick)
                        }
                        item {
                            ArchivedRoutinesEntry(onClick = onArchivedClick)
                        }
                        if (uiState.routines.isEmpty()) {
                            item {
                                EmptyState(
                                    title = stringResource(R.string.routines_empty_title),
                                    description = stringResource(R.string.routines_empty_description),
                                    action = { SpotterButton(text = stringResource(R.string.routines_create), onClick = onCreateClick) },
                                )
                            }
                        } else {
                            items(uiState.routines, key = { it.id }) { routine ->
                                RoutineCard(
                                    routine = routine,
                                    onClick = { onRoutineClick(routine.id) },
                                    onLongClick = { routineToArchive = routine },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingArchive = routineToArchive
    if (pendingArchive != null) {
        ConfirmDialog(
            title = stringResource(R.string.routines_archive_confirm_title),
            message = stringResource(R.string.routines_archive_confirm_message, pendingArchive.name),
            onConfirm = {
                viewModel.onArchive(pendingArchive.id)
                routineToArchive = null
            },
            onDismiss = { routineToArchive = null },
        )
    }
}

@Composable
private fun RoutinesHeader(
    onImportCode: (String) -> Unit,
    importCodeError: Boolean,
    onImportImageClick: () -> Unit,
    onCreateClick: () -> Unit,
) {
    var codeText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.routines_title), style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface)
            Row {
                IconButton(onClick = onImportImageClick) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.routines_import_image))
                }
                IconButton(onClick = onCreateClick) {
                    Icon(Icons.Filled.AddCircleOutline, contentDescription = stringResource(R.string.routines_create))
                }
            }
        }
        SpotterTextField(
            value = codeText,
            onValueChange = { codeText = it.uppercase() },
            placeholder = stringResource(R.string.routines_import_code_placeholder),
            isError = importCodeError,
            supportingText = if (importCodeError) stringResource(R.string.validation_share_code_invalid) else null,
            keyboardCapitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.fillMaxWidth(),
        )
        if (codeText.isNotBlank()) {
            SpotterButton(
                text = stringResource(R.string.routines_import_code_action),
                onClick = { onImportCode(codeText) },
                variant = SpotterButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TemplatesEntryCard(onClick: () -> Unit) {
    SpotterCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = stringResource(R.string.routines_explore_templates), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
                Text(
                    text = stringResource(R.string.routines_explore_templates_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                )
            }
            SpotterCard(modifier = Modifier) {
                Text(text = stringResource(R.string.routines_new_badge), style = MaterialTheme.typography.labelSmall, color = SpotterColors.PrimaryContainer)
            }
        }
    }
}

@Composable
private fun ArchivedRoutinesEntry(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.routines_archived_entry),
        style = MaterialTheme.typography.labelLarge,
        color = SpotterColors.OnSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun RoutineCard(routine: RoutineSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    SpotterCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClickLabel = stringResource(R.string.routines_archive_action),
            onLongClick = onLongClick,
        ),
    ) {
        Text(text = routine.name, style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        if (routine.description != null) {
            Text(
                text = routine.description,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotterColors.OnSurfaceVariant,
                maxLines = 2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = pluralStringResource(R.plurals.exercise_count, routine.exerciseCount, routine.exerciseCount),
                style = MaterialTheme.typography.labelMedium,
                color = SpotterColors.OnSurfaceVariant,
            )
            val daysLabel = if (routine.days.isNotEmpty()) {
                routine.days.joinToString(" · ") { SpanishWeekdays.abbr(it.name) }
            } else {
                routine.daysPerWeek?.let { stringResource(R.string.routines_days_per_week, it) }
            }
            if (daysLabel != null) {
                Text(text = daysLabel, style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
            }
        }
    }
}

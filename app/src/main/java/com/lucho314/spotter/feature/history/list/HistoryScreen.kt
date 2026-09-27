@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.history.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.WorkoutMath
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onSessionClick: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessionToDelete by remember { mutableStateOf<HistorySessionItem?>(null) }
    var failedToDiscard by remember { mutableStateOf<FailedWorkoutItem?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HistoryEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
            HistoryEvent.SessionDeleted -> scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.history_session_deleted)) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = SpotterColors.OnSurface,
                modifier = Modifier.padding(Spacing.xl),
            )

            if (uiState.pendingOnlyCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.pending_sync_count, uiState.pendingOnlyCount, uiState.pendingOnlyCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxs),
                )
            }

            if (uiState.failedWorkouts.isNotEmpty()) {
                FailedWorkoutsCard(
                    items = uiState.failedWorkouts,
                    onRetry = viewModel::onRetryFailed,
                    onDiscardClick = { failedToDiscard = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                )
            }

            val loadErrorRes = uiState.loadErrorRes
            when {
                uiState.loading -> LoadingState(modifier = Modifier.weight(1f))
                loadErrorRes != null -> ErrorState(
                    message = stringResource(loadErrorRes),
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f),
                )

                uiState.sessions.isEmpty() -> EmptyState(
                    title = stringResource(R.string.history_empty_title),
                    description = stringResource(R.string.history_empty_description),
                    modifier = Modifier.weight(1f),
                )

                else -> PullToRefreshBox(
                    isRefreshing = uiState.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(contentPadding = PaddingValues(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(uiState.sessions, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onSessionClick(session.id) },
                                onLongClick = { sessionToDelete = session },
                            )
                        }
                        if (uiState.hasMore) {
                            item {
                                SpotterButton(
                                    text = stringResource(R.string.history_load_more),
                                    onClick = viewModel::loadMore,
                                    variant = SpotterButtonVariant.Secondary,
                                    loading = uiState.loadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = sessionToDelete
    if (pendingDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_confirm_title),
            message = stringResource(R.string.history_delete_confirm_message, pendingDelete.dateText),
            onConfirm = { viewModel.onDeleteSession(pendingDelete.id); sessionToDelete = null },
            onDismiss = { sessionToDelete = null },
        )
    }

    val pendingDiscard = failedToDiscard
    if (pendingDiscard != null) {
        ConfirmDialog(
            title = stringResource(R.string.history_failed_discard_confirm_title),
            message = stringResource(R.string.history_failed_discard_confirm_message, pendingDiscard.dateText),
            onConfirm = { viewModel.onDiscardFailed(pendingDiscard.id); failedToDiscard = null },
            onDismiss = { failedToDiscard = null },
        )
    }
}

@Composable
private fun FailedWorkoutsCard(
    items: List<FailedWorkoutItem>,
    onRetry: (String) -> Unit,
    onDiscardClick: (FailedWorkoutItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpotterCard(modifier = modifier) {
        Text(text = stringResource(R.string.history_failed_title), style = MaterialTheme.typography.titleSmall, color = SpotterColors.Error)
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = item.dateText, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurface)
                    Text(
                        text = pluralStringResource(R.plurals.sets_count, item.setCount, item.setCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotterColors.OnSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SpotterButton(text = stringResource(R.string.generic_retry), onClick = { onRetry(item.id) }, variant = SpotterButtonVariant.Ghost)
                    SpotterButton(text = stringResource(R.string.history_failed_discard), onClick = { onDiscardClick(item) }, variant = SpotterButtonVariant.Ghost)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: HistorySessionItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    SpotterCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClickLabel = stringResource(R.string.history_delete_confirm_title),
            onLongClick = onLongClick,
        ),
    ) {
        Text(
            text = session.routineName ?: stringResource(R.string.history_free_workout),
            style = MaterialTheme.typography.titleMedium,
            color = SpotterColors.OnSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = session.dateText, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
            Text(text = WorkoutMath.formatDuration(session.durationMinutes), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
        }
    }
}

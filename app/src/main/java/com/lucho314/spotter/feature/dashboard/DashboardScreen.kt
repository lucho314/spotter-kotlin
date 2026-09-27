@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.dashboard

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.SectionHeader
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.StatCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.feature.common.ActiveWorkoutBanner
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.common.SectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onStartWorkoutClick: () -> Unit,
    onSeeAllRoutinesClick: () -> Unit,
    onPendingClick: () -> Unit,
    onCreateRoutineClick: () -> Unit,
    onRoutineClick: (String) -> Unit,
    onResumeWorkoutClick: () -> Unit,
    /** `true`/`false` right after finishing a workout (online/offline), relayed via `SavedStateHandle`; shows a one-time success snackbar. */
    finishedWorkoutOnline: StateFlow<Boolean?>,
    onFinishedWorkoutConsumed: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val finishedOnline by finishedWorkoutOnline.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DashboardEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    LaunchedEffect(finishedOnline) {
        val online = finishedOnline ?: return@LaunchedEffect
        // Consumed *before* showing the snackbar (which can sit on screen for ~4s): otherwise,
        // navigating away and back while it's still up would restart this effect and show it again.
        onFinishedWorkoutConsumed()
        val message = if (online) context.getString(R.string.workout_finished_online) else context.getString(R.string.workout_finished_offline)
        snackbarHostState.showSnackbar(message)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshOnResume()
        onPauseOrDispose {}
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        PullToRefreshBox(isRefreshing = uiState.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                item {
                    DashboardHeader(uiState.greeting, uiState.displayName ?: stringResource(R.string.onboarding_default_name))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = stringResource(R.string.dashboard_stat_this_week),
                            value = sessionsThisWeekText(uiState.sessionsThisWeek),
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = stringResource(R.string.dashboard_stat_last_session),
                            value = lastSessionText(uiState.lastSession),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    val activeWorkoutRoutineName = uiState.activeWorkoutRoutineName
                    if (activeWorkoutRoutineName != null) {
                        ActiveWorkoutBanner(routineName = activeWorkoutRoutineName, onClick = onResumeWorkoutClick, modifier = Modifier.fillMaxWidth())
                    } else {
                        SpotterButton(text = stringResource(R.string.dashboard_start_workout), onClick = onStartWorkoutClick, modifier = Modifier.fillMaxWidth())
                    }
                }

                item { LatestPrCard(uiState.latestPr, uiState.weightUnit) }

                if (uiState.pendingSyncCount > 0) {
                    item {
                        Text(
                            text = pluralStringResource(R.plurals.pending_sync_count, uiState.pendingSyncCount, uiState.pendingSyncCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotterColors.Secondary,
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClick = onPendingClick)
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.dashboard_my_routines),
                        action = { SpotterButton(text = stringResource(R.string.dashboard_see_all), onClick = onSeeAllRoutinesClick, variant = SpotterButtonVariant.Ghost) },
                    )
                }

                if (!uiState.routinesLoading && uiState.topRoutines.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.dashboard_empty_title),
                            description = stringResource(R.string.dashboard_empty_description),
                            action = { SpotterButton(text = stringResource(R.string.routines_create), onClick = onCreateRoutineClick) },
                        )
                    }
                } else {
                    items(uiState.topRoutines, key = { it.id }) { routine ->
                        DashboardRoutineCard(routine, onClick = { onRoutineClick(routine.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(greeting: Greeting, displayName: String) {
    val greetingRes = when (greeting) {
        Greeting.MORNING -> R.string.dashboard_greeting_morning
        Greeting.AFTERNOON -> R.string.dashboard_greeting_afternoon
        Greeting.NIGHT -> R.string.dashboard_greeting_night
    }
    Column {
        Text(text = stringResource(greetingRes), style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
        Text(text = displayName, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface)
    }
}

@Composable
private fun sessionsThisWeekText(state: SectionState<Int>): String = when (state) {
    SectionState.Loading -> "…"
    is SectionState.Loaded -> pluralStringResource(R.plurals.dashboard_sessions_count, state.value, state.value)
    is SectionState.Error -> stringResource(R.string.dashboard_section_error)
}

@Composable
private fun lastSessionText(state: SectionState<LastSessionLabel>): String = when (state) {
    SectionState.Loading -> "…"
    is SectionState.Error -> stringResource(R.string.dashboard_section_error)
    is SectionState.Loaded -> when (val label = state.value) {
        LastSessionLabel.Today -> stringResource(R.string.dashboard_last_session_today)
        LastSessionLabel.Yesterday -> stringResource(R.string.dashboard_last_session_yesterday)
        is LastSessionLabel.DaysAgo -> pluralStringResource(R.plurals.dashboard_last_session_days_ago, label.days, label.days)
        LastSessionLabel.None -> stringResource(R.string.dashboard_last_session_none)
    }
}

@Composable
private fun LatestPrCard(state: SectionState<PersonalRecord?>, weightUnit: WeightUnit) {
    SpotterCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.dashboard_latest_pr), style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
        when (state) {
            SectionState.Loading -> Text(text = "…", style = MaterialTheme.typography.headlineSmall, color = SpotterColors.PrimaryContainer)
            is SectionState.Error -> Text(text = stringResource(R.string.dashboard_section_error), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
            is SectionState.Loaded -> {
                val pr = state.value
                if (pr == null) {
                    Text(text = stringResource(R.string.dashboard_latest_pr_empty), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
                } else {
                    val unitLabel = if (weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)
                    Text(
                        text = stringResource(R.string.dashboard_latest_pr_value, WeightConverter.formatOneDecimal(pr.estimated1RmKg, weightUnit), unitLabel),
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpotterColors.PrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardRoutineCard(routine: RoutineSummary, onClick: () -> Unit) {
    SpotterCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = routine.name, style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        if (routine.description != null) {
            Text(text = routine.description, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant, maxLines = 2)
        }
    }
}

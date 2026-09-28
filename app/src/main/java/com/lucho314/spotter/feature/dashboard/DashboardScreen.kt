@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.SectionHeader
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonSize
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.StatCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes
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
    onProfileClick: () -> Unit,
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
                    DashboardHeader(
                        greeting = uiState.greeting,
                        displayName = uiState.displayName ?: stringResource(R.string.onboarding_default_name),
                        avatarUrl = uiState.avatarUrl,
                        onProfileClick = onProfileClick,
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        val sessions = sessionsThisWeekParts(uiState.sessionsThisWeek)
                        StatCard(
                            label = stringResource(R.string.dashboard_stat_this_week),
                            value = sessions.first,
                            unit = sessions.second,
                            shape = SpotterShapes.DashboardCard,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = stringResource(R.string.dashboard_stat_last_session),
                            value = lastSessionText(uiState.lastSession),
                            valueColor = SpotterColors.Secondary,
                            shape = SpotterShapes.DashboardCard,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    val activeWorkoutRoutineName = uiState.activeWorkoutRoutineName
                    if (activeWorkoutRoutineName != null) {
                        ActiveWorkoutBanner(routineName = activeWorkoutRoutineName, onClick = onResumeWorkoutClick, modifier = Modifier.fillMaxWidth())
                    } else {
                        SpotterButton(
                            text = stringResource(R.string.dashboard_start_workout),
                            onClick = onStartWorkoutClick,
                            size = SpotterButtonSize.Large,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                        action = {
                            Text(
                                text = stringResource(R.string.dashboard_see_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = SpotterColors.Secondary,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button, onClick = onSeeAllRoutinesClick)
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                        },
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
private fun DashboardHeader(greeting: Greeting, displayName: String, avatarUrl: String?, onProfileClick: () -> Unit) {
    val greetingRes = when (greeting) {
        Greeting.MORNING -> R.string.dashboard_greeting_morning
        Greeting.AFTERNOON -> R.string.dashboard_greeting_afternoon
        Greeting.NIGHT -> R.string.dashboard_greeting_night
    }
    val profileCd = stringResource(R.string.dashboard_profile_cd)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = stringResource(greetingRes),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = SpotterColors.OnSurfaceVariant,
            )
            Text(text = displayName, style = MaterialTheme.typography.headlineLarge, color = SpotterColors.OnSurface)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SpotterColors.SurfaceHighest)
                .clickable(role = Role.Button, onClick = onProfileClick)
                .semantics { contentDescription = profileCd },
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = SpotterColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** (numberText, unitWordOrNull): [SectionState.Loaded] splits into a big number + a small plural
 * unit word (e.g. "4" + "sesiones") for [StatCard]'s baseline layout; the loading/error placeholder
 * text isn't a number, so it renders as a single plain line instead (no [Pair.second]). */
@Composable
private fun sessionsThisWeekParts(state: SectionState<Int>): Pair<String, String?> = when (state) {
    SectionState.Loading -> "…" to null
    is SectionState.Error -> stringResource(R.string.dashboard_section_error) to null
    is SectionState.Loaded -> state.value.toString() to pluralStringResource(R.plurals.dashboard_sessions_unit, state.value)
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
    SpotterCard(modifier = Modifier.fillMaxWidth(), shape = SpotterShapes.DashboardCard) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SpotterColors.PrimaryContainer, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.dashboard_latest_pr),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = SpotterColors.PrimaryContainer,
            )
        }
        when (state) {
            SectionState.Loading -> Text(text = "…", style = MaterialTheme.typography.headlineSmall, color = SpotterColors.PrimaryContainer)
            is SectionState.Error -> Text(text = stringResource(R.string.dashboard_section_error), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
            is SectionState.Loaded -> {
                val pr = state.value
                if (pr == null) {
                    Text(text = stringResource(R.string.dashboard_latest_pr_empty), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
                } else {
                    if (pr.exerciseName != null) {
                        Text(text = pr.exerciseName, style = MaterialTheme.typography.titleLarge, color = SpotterColors.OnSurface)
                    }
                    val unitLabel = if (weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = WeightConverter.formatOneDecimal(pr.estimated1RmKg, weightUnit),
                            style = MaterialTheme.typography.displayMedium,
                            color = SpotterColors.PrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.dashboard_latest_pr_unit, unitLabel),
                            style = MaterialTheme.typography.titleMedium,
                            color = SpotterColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardRoutineCard(routine: RoutineSummary, onClick: () -> Unit) {
    SpotterCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleMedium,
                color = SpotterColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SpotterColors.OnSurfaceVariant)
        }
        if (routine.days.isNotEmpty()) {
            Text(
                text = routine.days.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.labelMedium,
                color = SpotterColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

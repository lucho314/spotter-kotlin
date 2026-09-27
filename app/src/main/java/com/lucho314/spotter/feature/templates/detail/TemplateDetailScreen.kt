@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.templates.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.TemplateDay
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.feature.common.ObserveAsEvents
import com.lucho314.spotter.feature.common.routineExerciseSummary
import com.lucho314.spotter.feature.exercise.labelRes
import com.lucho314.spotter.feature.templates.accentColor
import com.lucho314.spotter.feature.templates.labelRes
import kotlinx.coroutines.launch

@Composable
fun TemplateDetailScreen(
    onBack: () -> Unit,
    onExerciseClick: (Int) -> Unit,
    onRoutinesCreated: () -> Unit,
    viewModel: TemplateDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Lifecycle-aware: `RoutinesCreated` can arrive while backgrounded (adopting several routines
    // one by one is not instant) - it must stay buffered instead of being consumed-then-dropped by
    // `onRoutinesCreated` - see ObserveAsEvents' KDoc. `onRoutinesCreated` itself is unguarded (no
    // `dropUnlessResumed`) for the same reason.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            TemplateDetailEvent.RoutinesCreated -> onRoutinesCreated()
            is TemplateDetailEvent.AdoptFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    // Adopting creates several routines one by one (AdoptTemplateUseCase); navigating away mid-way
    // would clear this screen's ViewModel and cancel that work. The use case itself compensates
    // (deletes what it already created) even on cancellation, but simply not cancelling in the
    // first place is the better UX - the system back gesture is intercepted while adopting.
    BackHandler(enabled = uiState.adopting) {}

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.template?.summary?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.adopting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val template = uiState.template
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            template == null -> ErrorState(
                message = uiState.errorMessageRes?.let { stringResource(it) } ?: stringResource(R.string.error_unknown),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            else -> TemplateDetailContent(
                template = template,
                adopting = uiState.adopting,
                onExerciseClick = onExerciseClick,
                onAdoptClick = { showConfirmDialog = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showConfirmDialog) {
        val dayCount = uiState.template?.days?.size ?: 0
        ConfirmDialog(
            title = stringResource(R.string.template_adopt_confirm_title),
            message = pluralStringResource(R.plurals.template_adopt_confirm_message, dayCount, dayCount),
            confirmLabel = stringResource(R.string.template_adopt_confirm_action),
            onConfirm = {
                showConfirmDialog = false
                viewModel.onAdoptConfirmed()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

@Composable
private fun TemplateDetailContent(
    template: TemplateDetail,
    adopting: Boolean,
    onExerciseClick: (Int) -> Unit,
    onAdoptClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(Spacing.xl)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(bottom = Spacing.md)) {
                    SpotterChip(
                        label = stringResource(template.summary.goal.labelRes()),
                        selected = true,
                        onClick = {},
                        selectedContainerColor = template.summary.goal.accentColor(),
                        selectedLabelColor = SpotterColors.OnSurfaceVariant,
                    )
                    SpotterChip(label = stringResource(template.summary.difficulty.labelRes()), selected = false, onClick = {})
                    SpotterChip(
                        label = stringResource(R.string.templates_days_per_week_badge, template.summary.daysPerWeek),
                        selected = false,
                        onClick = {},
                    )
                }
            }
            if (template.summary.description != null) {
                item {
                    Text(
                        text = template.summary.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotterColors.OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.template_detail_structure),
                    style = MaterialTheme.typography.titleLarge,
                    color = SpotterColors.OnSurface,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            items(template.days, key = { it.id }) { day ->
                TemplateDayCard(day = day, onExerciseClick = onExerciseClick, modifier = Modifier.padding(bottom = Spacing.sm))
            }
        }
        SpotterButton(
            text = stringResource(if (adopting) R.string.template_adopt_loading else R.string.template_adopt_action),
            onClick = onAdoptClick,
            enabled = !adopting,
            loading = adopting,
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        )
    }
}

@Composable
private fun TemplateDayCard(day: TemplateDay, onExerciseClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    SpotterCard(modifier = modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Text(text = day.name, style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        if (day.description != null) {
            Text(text = day.description, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
        }
        Text(
            text = pluralStringResource(R.plurals.exercise_count, day.exercises.size, day.exercises.size),
            style = MaterialTheme.typography.labelMedium,
            color = SpotterColors.OnSurfaceVariant,
        )
        if (expanded) {
            day.exercises.forEach { exercise ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = Spacing.xxs)
                        .clickable(role = Role.Button) { onExerciseClick(exercise.exerciseId) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = exercise.exercise?.name ?: stringResource(R.string.template_exercise_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotterColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = routineExerciseSummary(exercise.targetSets, exercise.targetReps, exercise.restSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = SpotterColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

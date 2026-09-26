@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.templates.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.RoutineTemplateSummary
import com.lucho314.spotter.domain.model.TemplateGoal
import com.lucho314.spotter.feature.exercise.labelRes
import com.lucho314.spotter.feature.templates.accentColor
import com.lucho314.spotter.feature.templates.icon
import com.lucho314.spotter.feature.templates.labelRes

private val DAY_OPTIONS = 2..6

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onTemplateClick: (String) -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        // The filter chips stay visible while a (re)load is in flight - only the list area below
        // them switches to a spinner/error/empty state, so changing a filter doesn't hide the very
        // controls the user would use to change it again.
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoalSelector(selected = uiState.goalFilter, onSelect = viewModel::onGoalFilterChanged)
            DaysSelector(selected = uiState.daysFilter, onSelect = viewModel::onDaysFilterChanged)
            val errorMessageRes = uiState.errorMessageRes
            when {
                uiState.loading -> LoadingState(modifier = Modifier.weight(1f))
                errorMessageRes != null -> ErrorState(
                    message = stringResource(errorMessageRes),
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f),
                )

                uiState.templates.isEmpty() -> EmptyState(title = stringResource(R.string.templates_empty), modifier = Modifier.weight(1f))

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(uiState.templates, key = { it.id }) { template ->
                        TemplateCard(template = template, onClick = { onTemplateClick(template.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSelector(selected: TemplateGoal?, onSelect: (TemplateGoal?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TemplateGoal.entries.forEach { goal ->
            val isSelected = selected == goal
            SpotterChip(
                label = stringResource(goal.labelRes()),
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else goal) },
                leadingIcon = { Icon(goal.icon(), contentDescription = null) },
                modifier = Modifier.weight(1f),
                selectedContainerColor = goal.accentColor(),
                selectedLabelColor = SpotterColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DaysSelector(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.templates_days_per_week_label),
            style = MaterialTheme.typography.labelMedium,
            color = SpotterColors.OnSurfaceVariant,
        )
        DAY_OPTIONS.forEach { days ->
            val isSelected = selected == days
            SpotterChip(label = days.toString(), selected = isSelected, onClick = { onSelect(if (isSelected) null else days) })
        }
    }
}

@Composable
private fun TemplateCard(template: RoutineTemplateSummary, onClick: () -> Unit) {
    SpotterCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SpotterChip(
                label = stringResource(template.goal.labelRes()),
                selected = true,
                onClick = {},
                selectedContainerColor = template.goal.accentColor(),
                selectedLabelColor = SpotterColors.OnSurfaceVariant,
            )
            SpotterChip(label = stringResource(template.difficulty.labelRes()), selected = false, onClick = {})
            SpotterChip(
                label = stringResource(R.string.templates_days_per_week_badge, template.daysPerWeek),
                selected = false,
                onClick = {},
            )
        }
        Text(text = template.name, style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        if (template.description != null) {
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = SpotterColors.OnSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

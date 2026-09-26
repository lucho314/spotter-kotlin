@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ChartPoint
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LineChart
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.model.PersonalRecord
import com.lucho314.spotter.domain.model.WeightUnit

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineSmall,
                color = SpotterColors.OnSurface,
                modifier = Modifier.padding(Spacing.xl),
            )

            val loadErrorRes = uiState.loadErrorRes
            when {
                uiState.loading -> LoadingState(modifier = Modifier.weight(1f))
                loadErrorRes != null -> ErrorState(message = stringResource(loadErrorRes), onRetry = viewModel::retry, modifier = Modifier.weight(1f))
                uiState.records.isEmpty() -> EmptyState(
                    title = stringResource(R.string.progress_empty_title),
                    description = stringResource(R.string.progress_empty_description),
                    modifier = Modifier.weight(1f),
                )

                else -> PullToRefreshBox(isRefreshing = uiState.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                    LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        item { Text(text = stringResource(R.string.progress_prs_title), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface) }
                        items(uiState.records, key = { it.id }) { record -> PersonalRecordCard(record, uiState.weightUnit) }

                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                                items(uiState.records, key = { it.id }) { record ->
                                    SpotterChip(
                                        label = record.exerciseName ?: stringResource(R.string.progress_exercise_unknown),
                                        selected = uiState.selectedExerciseId == record.exerciseId,
                                        onClick = { viewModel.onExerciseChipClick(record.exerciseId) },
                                    )
                                }
                            }
                        }

                        item { ProgressChartSection(uiState) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordCard(record: PersonalRecord, weightUnit: WeightUnit) {
    SpotterCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = stringResource(R.string.progress_trophy_cd), tint = SpotterColors.PrimaryContainer, modifier = Modifier.size(20.dp))
            Text(text = record.exerciseName ?: stringResource(R.string.progress_exercise_unknown), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        }
        val weightText = WeightConverter.format(record.bestWeightKg, weightUnit)
        val unitLabel = if (weightUnit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)
        Text(
            text = "$weightText $unitLabel × ${pluralStringResource(R.plurals.reps_count, record.bestRepsAtWeight, record.bestRepsAtWeight)}",
            style = MaterialTheme.typography.bodyMedium,
            color = SpotterColors.OnSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.progress_pr_1rm, WeightConverter.formatOneDecimal(record.estimated1RmKg, weightUnit), unitLabel),
            style = MaterialTheme.typography.bodySmall,
            color = SpotterColors.OnSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressChartSection(uiState: ProgressUiState) {
    when (val chart = uiState.chart) {
        ProgressChartState.Hidden -> Text(
            text = stringResource(R.string.progress_select_exercise),
            style = MaterialTheme.typography.bodyMedium,
            color = SpotterColors.OnSurfaceVariant,
        )

        ProgressChartState.Loading -> LoadingState(modifier = Modifier.fillMaxWidth())

        is ProgressChartState.Loaded -> if (chart.points.isEmpty()) {
            Text(text = stringResource(R.string.progress_chart_empty), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = stringResource(R.string.progress_chart_title), style = MaterialTheme.typography.titleSmall, color = SpotterColors.OnSurface)
                LineChart(points = chart.points.map { ChartPoint(it.label, WeightConverter.fromKg(it.e1RmKg, uiState.weightUnit).toFloat()) })
            }
        }

        is ProgressChartState.Error -> Text(text = stringResource(chart.messageRes), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.Error)
    }
}

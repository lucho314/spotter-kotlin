@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.ExerciseMedia
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SectionHeader
import com.lucho314.spotter.core.designsystem.component.SpotterChip
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.Exercise

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.exercise?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val exercise = uiState.exercise
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(padding))
            exercise == null -> ErrorState(
                message = uiState.errorMessageRes?.let { stringResource(it) } ?: stringResource(R.string.error_unknown),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            else -> ExerciseDetailContent(exercise = exercise, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun ExerciseDetailContent(exercise: Exercise, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.xl)) {
        item {
            ExerciseMedia(
                mediaUrl = exercise.mediaUrl,
                imageUrl = exercise.imageUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(text = exercise.name, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface)
                if (exercise.muscleGroup != null) {
                    Text(
                        text = exercise.muscleGroup.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotterColors.OnSurfaceVariant,
                    )
                }
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(vertical = Spacing.md),
            ) {
                item { SpotterChip(label = stringResource(exercise.equipment.labelRes()), selected = true, onClick = {}) }
                exercise.difficulty?.let { difficulty ->
                    item { SpotterChip(label = stringResource(difficulty.labelRes()), selected = false, onClick = {}) }
                }
                exercise.category?.let { category ->
                    item { SpotterChip(label = stringResource(category.labelRes()), selected = false, onClick = {}) }
                }
            }
        }
        if (exercise.secondaryMuscles.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.exercise_detail_secondary_muscles), modifier = Modifier.padding(bottom = Spacing.xs))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), contentPadding = PaddingValues(bottom = Spacing.md)) {
                    items(exercise.secondaryMuscles) { muscle ->
                        SpotterChip(label = muscle, selected = false, onClick = {})
                    }
                }
            }
        }
        if (exercise.instructions.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.exercise_detail_instructions), modifier = Modifier.padding(bottom = Spacing.xs))
            }
            itemsIndexed(exercise.instructions) { index, instruction ->
                Text(
                    text = "${index + 1}. $instruction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = Spacing.xs),
                )
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.routines.list

import androidx.compose.foundation.layout.Arrangement
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
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonSize
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.RoutineSummary
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun ArchivedRoutinesScreen(
    onBack: () -> Unit,
    viewModel: ArchivedRoutinesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ArchivedRoutinesEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines_archived_title)) },
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
        if (uiState.loading) {
            LoadingState(modifier = Modifier.padding(padding))
        } else if (loadErrorRes != null) {
            ErrorState(message = stringResource(loadErrorRes), onRetry = viewModel::refresh, modifier = Modifier.padding(padding))
        } else if (uiState.routines.isEmpty()) {
            EmptyState(title = stringResource(R.string.routines_archived_empty), modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(uiState.routines, key = { it.id }) { routine ->
                    ArchivedRoutineCard(routine = routine, onRestore = { viewModel.onRestore(routine.id) })
                }
            }
        }
    }
}

@Composable
private fun ArchivedRoutineCard(routine: RoutineSummary, onRestore: () -> Unit) {
    SpotterCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = routine.name, style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface, modifier = Modifier.weight(1f))
            SpotterButton(
                text = stringResource(R.string.routines_restore),
                onClick = onRestore,
                variant = SpotterButtonVariant.Secondary,
                size = SpotterButtonSize.Small,
            )
        }
    }
}

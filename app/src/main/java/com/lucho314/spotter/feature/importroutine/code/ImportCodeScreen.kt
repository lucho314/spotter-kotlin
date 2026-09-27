@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.importroutine.code

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.EmptyState
import com.lucho314.spotter.core.designsystem.component.ErrorState
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.SharedRoutinePreview
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@Composable
fun ImportCodeScreen(
    onBack: () -> Unit,
    onImported: (routineId: String) -> Unit,
    viewModel: ImportCodeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Unguarded: driven by ImportCodeEvent.Imported, which can legitimately arrive while
    // backgrounded (mid network call) - see ObserveAsEvents' KDoc.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ImportCodeEvent.Imported -> onImported(event.routineId)
            is ImportCodeEvent.ActionFailed -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
        }
    }

    BackHandler(enabled = uiState.importing) {}

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_code_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.importing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (val status = uiState.status) {
            ImportCodeStatus.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is ImportCodeStatus.Unavailable -> EmptyState(
                title = stringResource(status.titleRes),
                description = stringResource(R.string.import_code_invalid_description),
                modifier = Modifier.padding(padding).fillMaxSize(),
                action = { SpotterButton(text = stringResource(R.string.generic_back), onClick = onBack) },
            )
            is ImportCodeStatus.LoadError -> ErrorState(
                message = stringResource(status.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            is ImportCodeStatus.Ready -> ImportCodeReadyContent(
                preview = status.preview,
                importing = uiState.importing,
                onImportClick = viewModel::onImportClick,
                onCancel = onBack,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ImportCodeReadyContent(
    preview: SharedRoutinePreview,
    importing: Boolean,
    onImportClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SpotterCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.import_code_label),
                style = MaterialTheme.typography.labelMedium,
                color = SpotterColors.OnSurfaceVariant,
            )
            Text(text = preview.routineName, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface)
            Text(
                text = pluralStringResource(R.plurals.exercise_count, preview.exerciseCount, preview.exerciseCount),
                style = MaterialTheme.typography.bodyMedium,
                color = SpotterColors.OnSurfaceVariant,
            )
            if (preview.dayCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.import_code_day_count, preview.dayCount, preview.dayCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                )
            }
        }
        SpotterButton(
            text = stringResource(R.string.import_code_action),
            onClick = onImportClick,
            loading = importing,
            modifier = Modifier.fillMaxWidth(),
        )
        SpotterButton(
            text = stringResource(R.string.generic_cancel),
            onClick = onCancel,
            variant = SpotterButtonVariant.Ghost,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

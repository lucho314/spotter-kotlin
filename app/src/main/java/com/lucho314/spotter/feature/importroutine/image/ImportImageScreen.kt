@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.importroutine.image

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes
import com.lucho314.spotter.feature.common.ObserveAsEvents
import kotlinx.coroutines.launch

@StringRes
private fun AiImportErrorKind.messageRes(): Int = when (this) {
    AiImportErrorKind.OFFLINE -> R.string.import_image_error_offline
    AiImportErrorKind.IMAGE_TOO_LARGE -> R.string.validation_image_too_large
    AiImportErrorKind.IMAGE_UNREADABLE -> R.string.validation_image_unreadable
    AiImportErrorKind.TIMEOUT_MAYBE_CREATED -> R.string.import_image_error_timeout
    AiImportErrorKind.CONNECTION_LOST_MAYBE_CREATED -> R.string.import_image_error_connection_lost
    AiImportErrorKind.NOT_RECOGNIZED -> R.string.import_image_error_not_recognized
    AiImportErrorKind.SESSION_EXPIRED -> R.string.error_unauthorized
    AiImportErrorKind.GENERIC -> R.string.import_image_error_generic
}

@Composable
fun ImportImageScreen(
    onBack: () -> Unit,
    onRoutineCreated: (routineId: String, routineName: String?) -> Unit,
    onSeeRoutines: () -> Unit,
    viewModel: ImportImageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCameraResult(success)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onGalleryResult(uri?.toString())
    }

    // Unguarded: driven by ImportImageEvent (Imported/LaunchCamera/ShowError), which can arrive
    // while backgrounded (mid network call) - see ObserveAsEvents' KDoc.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ImportImageEvent.LaunchCamera -> {
                try {
                    cameraLauncher.launch(Uri.parse(event.uri))
                } catch (e: ActivityNotFoundException) {
                    viewModel.onCameraResult(false)
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.import_image_no_camera_app)) }
                }
            }
            is ImportImageEvent.Imported -> onRoutineCreated(event.routineId, event.routineName)
            is ImportImageEvent.ShowError -> scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(event.kind.messageRes()),
                    actionLabel = if (event.kind.suggestsCheckingRoutines) context.getString(R.string.import_image_see_routines) else null,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onSeeRoutines()
            }
        }
    }

    BackHandler(enabled = uiState.importing) {}

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_image_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.importing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(text = stringResource(R.string.import_image_description), style = MaterialTheme.typography.bodyMedium)

            val imageUri = uiState.imageUri
            if (imageUri == null) {
                SpotterButton(
                    text = stringResource(R.string.import_image_camera),
                    onClick = viewModel::onCameraClick,
                    variant = SpotterButtonVariant.Secondary,
                    enabled = !uiState.importing,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpotterButton(
                    text = stringResource(R.string.import_image_gallery),
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    variant = SpotterButtonVariant.Secondary,
                    enabled = !uiState.importing,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AsyncImage(
                    model = Uri.parse(imageUri),
                    contentDescription = stringResource(R.string.import_image_preview_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(SpotterShapes.Card),
                )
                SpotterButton(
                    text = stringResource(R.string.import_image_change),
                    onClick = viewModel::onClearImage,
                    variant = SpotterButtonVariant.Ghost,
                    enabled = !uiState.importing,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpotterButton(
                    text = stringResource(R.string.import_image_action),
                    onClick = viewModel::onImportClick,
                    loading = uiState.importing,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.importing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator()
                        Text(text = stringResource(R.string.import_image_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

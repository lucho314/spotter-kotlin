package com.lucho314.spotter.feature.garmin.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.SectionHeader
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.GarminConnectionState

@Composable
fun GarminSettingsSection(
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarminSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SectionHeader(title = stringResource(R.string.garmin_section_title), modifier = modifier)
    SpotterCard(modifier = modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.garmin_description), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)

        when (val connection = uiState.connection) {
            GarminConnectionState.NotConnected -> SpotterButton(
                text = stringResource(R.string.garmin_connect),
                onClick = onConnectClick,
                modifier = Modifier.fillMaxWidth(),
            )
            is GarminConnectionState.Connected -> ConnectedContent(connection, viewModel, onConnectClick)
        }

        if (uiState.failedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.garmin_failed_uploads, uiState.failedCount, uiState.failedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotterColors.OnSurfaceVariant,
                )
                SpotterButton(text = stringResource(R.string.garmin_retry_failed), onClick = viewModel::onRetryFailed, variant = SpotterButtonVariant.Ghost)
            }
        }
    }

    if (uiState.disconnectPrompt) {
        ConfirmDialog(
            title = stringResource(R.string.garmin_disconnect_confirm_title),
            message = stringResource(R.string.garmin_disconnect_confirm_message),
            onConfirm = viewModel::onDisconnectConfirmed,
            onDismiss = viewModel::onDisconnectDismiss,
        )
    }
}

@Composable
private fun ConnectedContent(connection: GarminConnectionState.Connected, viewModel: GarminSettingsViewModel, onConnectClick: () -> Unit) {
    Text(
        text = connection.displayName?.let { stringResource(R.string.garmin_connected_as, it) } ?: stringResource(R.string.garmin_connected),
        style = MaterialTheme.typography.bodyLarge,
        color = SpotterColors.OnSurface,
    )

    if (connection.needsReconnect) {
        Text(text = stringResource(R.string.garmin_reconnect_needed), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
        SpotterButton(text = stringResource(R.string.garmin_reconnect), onClick = onConnectClick, modifier = Modifier.fillMaxWidth())
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = connection.autoUpload,
                onValueChange = viewModel::onAutoUploadChange,
                role = Role.Switch,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.garmin_auto_upload), style = MaterialTheme.typography.bodyLarge, color = SpotterColors.OnSurface)
        Switch(checked = connection.autoUpload, onCheckedChange = null)
    }
    Text(text = stringResource(R.string.garmin_auto_upload_hint), style = MaterialTheme.typography.bodySmall, color = SpotterColors.OnSurfaceVariant)

    SpotterButton(
        text = stringResource(R.string.garmin_disconnect),
        onClick = viewModel::onDisconnectClick,
        variant = SpotterButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

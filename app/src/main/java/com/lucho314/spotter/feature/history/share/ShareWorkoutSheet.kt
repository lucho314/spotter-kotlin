@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.history.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.domain.model.ExportFormat

/** Stateless: [exporting] drives each row's spinner/disabled state; [onSelect] triggers the export in the caller's ViewModel. */
@Composable
fun ShareWorkoutSheet(exporting: ExportFormat?, onSelect: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = stringResource(R.string.share_workout_title), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
            ShareWorkoutOption(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.share_workout_pdf),
                description = stringResource(R.string.share_workout_pdf_description),
                loading = exporting == ExportFormat.PDF,
                enabled = exporting == null,
                onClick = { onSelect(ExportFormat.PDF) },
            )
            ShareWorkoutOption(
                icon = Icons.Filled.Image,
                title = stringResource(R.string.share_workout_story),
                description = stringResource(R.string.share_workout_story_description),
                loading = exporting == ExportFormat.STORY,
                enabled = exporting == null,
                onClick = { onSelect(ExportFormat.STORY) },
            )
        }
    }
}

@Composable
private fun ShareWorkoutOption(
    icon: ImageVector,
    title: String,
    description: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SpotterCard(modifier = Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(icon, contentDescription = null, tint = SpotterColors.OnSurface)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = SpotterColors.OnSurface)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = SpotterColors.OnSurfaceVariant)
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

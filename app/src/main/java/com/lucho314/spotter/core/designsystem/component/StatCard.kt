package com.lucho314.spotter.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/** A small stat tile, e.g. "ESTA SEMANA" / "3 sesiones" on the dashboard, or profile stats. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    valueColor: Color = SpotterColors.PrimaryContainer,
) {
    SpotterCard(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
        if (supportingText != null) {
            Text(text = supportingText, style = MaterialTheme.typography.bodySmall, color = SpotterColors.OnSurfaceVariant)
        }
    }
}

package com.lucho314.spotter.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes

/**
 * A small stat tile, e.g. "ESTA SEMANA" / "3 sesiones" on the dashboard, or profile stats.
 *
 * [unit], when given, splits [value] into a big [valueColor] number with a small
 * [SpotterColors.OnSurfaceVariant] word right after it on the same baseline (the RN look, e.g.
 * "4" + "sesiones") instead of one plain line - existing callers that don't pass it keep their
 * exact previous single-line rendering.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supportingText: String? = null,
    valueColor: Color = SpotterColors.PrimaryContainer,
    shape: Shape = SpotterShapes.Card,
) {
    SpotterCard(modifier = modifier, shape = shape) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
        if (unit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.headlineLarge, color = valueColor)
                Text(text = unit, style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
            }
        } else {
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
        }
        if (supportingText != null) {
            Text(text = supportingText, style = MaterialTheme.typography.bodySmall, color = SpotterColors.OnSurfaceVariant)
        }
    }
}

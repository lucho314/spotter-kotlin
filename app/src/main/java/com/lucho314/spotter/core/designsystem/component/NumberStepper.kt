package com.lucho314.spotter.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/**
 * A labeled `-`/`+` stepper for integer inputs (sets, reps, rest seconds). [value] never goes
 * below [min] or above [max]; [step] is the increment on each tap (e.g. rest uses `step = 15`).
 */
@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = SpotterColors.OnSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            IconButton(onClick = { onValueChange((value - step).coerceAtLeast(min)) }, enabled = value - step >= min) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.number_stepper_decrease, label))
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = SpotterColors.OnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            IconButton(onClick = { onValueChange((value + step).coerceAtMost(max)) }, enabled = value + step <= max) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.number_stepper_increase, label))
            }
        }
    }
}

package com.lucho314.spotter.core.designsystem.component

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterShapes

/**
 * Selectable filter chip (muscle group filter, template goal/days filters, day picker sheets).
 * [selectedContainerColor]/[selectedLabelColor] default to the app's primary accent but can be
 * overridden (e.g. a template's goal accent color, section 9.11 of the migration plan).
 */
@Composable
fun SpotterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    selectedContainerColor: Color = SpotterColors.PrimaryContainer,
    selectedLabelColor: Color = SpotterColors.OnPrimary,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = SpotterShapes.Chip,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = selectedLabelColor,
            selectedLeadingIconColor = selectedLabelColor,
        ),
    )
}

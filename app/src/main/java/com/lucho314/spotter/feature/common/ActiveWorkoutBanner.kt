package com.lucho314.spotter.feature.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.SpotterCard
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/**
 * "Entrenamiento en curso · Continuar" (RN bug #6, migration plan section 10 phase 4: there was no
 * way back into an active workout after closing the app). Lives in both Dashboard (replacing the
 * "Iniciar Entrenamiento" CTA) and Rutinas (the closest thing to a "start a workout" entry point,
 * and where this first shipped in phase 4).
 */
@Composable
fun ActiveWorkoutBanner(routineName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SpotterCard(onClick = onClick, modifier = modifier) {
        Text(text = stringResource(R.string.routines_active_workout_title), style = MaterialTheme.typography.titleSmall, color = SpotterColors.PrimaryContainer)
        Text(text = routineName, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
    }
}

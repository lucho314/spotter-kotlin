package com.lucho314.spotter.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonSize
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.feature.common.ObserveAsEvents

/** Stateful entry point, shown once per user (see [OnboardingViewModel]). */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Lifecycle-aware (ObserveAsEvents' KDoc): `setOnboardingDone` completing while this screen is
    // backgrounded must still navigate once resumed, not be dropped.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OnboardingEvent.Done -> onDone()
        }
    }

    OnboardingContent(
        displayName = uiState.displayName ?: stringResource(R.string.onboarding_default_name),
        onStartClick = viewModel::onStartClick,
    )
}

@Composable
private fun OnboardingContent(displayName: String, onStartClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl)
            .padding(bottom = Spacing.huge),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_badge),
                style = MaterialTheme.typography.labelSmall,
                color = SpotterColors.PrimaryContainer,
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.displayLarge,
                color = SpotterColors.OnSurface,
            )
            Text(
                text = stringResource(R.string.onboarding_description),
                style = MaterialTheme.typography.bodyLarge,
                color = SpotterColors.OnSurfaceVariant,
            )
        }
        SpotterButton(
            text = stringResource(R.string.onboarding_cta),
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth(),
            size = SpotterButtonSize.Large,
        )
    }
}

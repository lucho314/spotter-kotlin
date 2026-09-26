package com.lucho314.spotter.feature.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/** Shown instead of the app when `SUPABASE_URL`/`SUPABASE_ANON_KEY` are missing or malformed. The Supabase client is never touched in this state. */
@Composable
fun ConfigErrorScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.config_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = SpotterColors.OnSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.config_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = SpotterColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

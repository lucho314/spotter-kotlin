package com.lucho314.spotter.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.ConfirmDialog
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonVariant
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/** Stateful entry point. Phase 1 only shows identity + sign-out; stats and physical data come later. */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    ProfileContent(
        displayName = uiState.displayName ?: stringResource(R.string.onboarding_default_name),
        email = uiState.email,
        signingOut = uiState.signingOut,
        onSignOutConfirmed = viewModel::onSignOutConfirmed,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun ProfileContent(
    displayName: String,
    email: String?,
    signingOut: Boolean,
    onSignOutConfirmed: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = displayName, style = MaterialTheme.typography.headlineSmall, color = SpotterColors.OnSurface)
            if (email != null) {
                Text(text = email, style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
            }

            SpotterButton(
                text = stringResource(R.string.profile_sign_out),
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                variant = SpotterButtonVariant.Secondary,
                loading = signingOut,
                enabled = !signingOut,
            )
        }
    }

    if (showConfirmDialog) {
        ConfirmDialog(
            title = stringResource(R.string.profile_sign_out_confirm_title),
            message = stringResource(R.string.profile_sign_out_confirm_message),
            onConfirm = {
                showConfirmDialog = false
                onSignOutConfirmed()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

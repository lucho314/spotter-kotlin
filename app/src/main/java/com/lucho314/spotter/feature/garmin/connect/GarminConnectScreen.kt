@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lucho314.spotter.feature.garmin.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterTextField
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.feature.common.ObserveAsEvents

@Composable
fun GarminConnectScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: GarminConnectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Unguarded: driven by GarminConnectEvent.Connected, a ViewModel event, not a user click -
    // see SpotterNavHost's KDoc on async-operation results.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            GarminConnectEvent.Connected -> onConnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garmin_connect_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.generic_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = stringResource(R.string.garmin_description), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurfaceVariant)
            Text(text = stringResource(R.string.garmin_password_note), style = MaterialTheme.typography.bodySmall, color = SpotterColors.OnSurfaceVariant)

            when (val step = uiState.step) {
                GarminConnectStep.Credentials -> CredentialsStep(uiState, viewModel)
                is GarminConnectStep.Mfa -> MfaStep(step, uiState, viewModel)
            }

            Text(
                text = stringResource(R.string.garmin_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = SpotterColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
}

@Composable
private fun CredentialsStep(uiState: GarminConnectUiState, viewModel: GarminConnectViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SpotterTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.garmin_email),
            keyboardType = KeyboardType.Email,
            enabled = !uiState.submitting,
            isError = uiState.errorRes != null,
            modifier = Modifier.fillMaxWidth(),
        )
        SpotterTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.garmin_password),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !uiState.submitting,
            isError = uiState.errorRes != null,
            supportingText = uiState.errorRes?.let { stringResource(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        SpotterButton(
            text = stringResource(R.string.garmin_connect_button),
            onClick = viewModel::onSubmitCredentials,
            loading = uiState.submitting,
            enabled = !uiState.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MfaStep(step: GarminConnectStep.Mfa, uiState: GarminConnectUiState, viewModel: GarminConnectViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = stringResource(R.string.garmin_mfa_title), style = MaterialTheme.typography.titleMedium, color = SpotterColors.OnSurface)
        Text(text = mfaMessage(step.method), style = MaterialTheme.typography.bodyMedium, color = SpotterColors.OnSurface)
        SpotterTextField(
            value = uiState.mfaCode,
            onValueChange = viewModel::onMfaCodeChange,
            label = stringResource(R.string.garmin_mfa_code),
            keyboardType = KeyboardType.NumberPassword,
            enabled = !uiState.submitting,
            isError = uiState.errorRes != null,
            supportingText = uiState.errorRes?.let { stringResource(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        SpotterButton(
            text = stringResource(R.string.garmin_mfa_verify),
            onClick = viewModel::onSubmitMfa,
            loading = uiState.submitting,
            enabled = !uiState.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = viewModel::onBackToCredentials, enabled = !uiState.submitting) {
            Text(stringResource(R.string.generic_back))
        }
    }
}

@Composable
private fun mfaMessage(method: String?): String = when (method) {
    "sms" -> stringResource(R.string.garmin_mfa_message_sms)
    "email" -> stringResource(R.string.garmin_mfa_message_email)
    else -> stringResource(R.string.garmin_mfa_message_app)
}

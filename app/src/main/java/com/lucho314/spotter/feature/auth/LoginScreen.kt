package com.lucho314.spotter.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.SpotterButton
import com.lucho314.spotter.core.designsystem.component.SpotterButtonSize
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.feature.common.ObserveAsEvents
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Stateful entry point: wires [LoginViewModel] events to the Credential Manager client. */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Obtained via a Hilt entry point (not injected into the ViewModel): GoogleCredentialClient is
    // stateless and only ever needs the local `Context`, which ViewModels shouldn't hold onto.
    val credentialClient = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GoogleCredentialClientEntryPoint::class.java,
        ).googleCredentialClient()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // Lifecycle-aware (see ObserveAsEvents' KDoc): a request event arriving while backgrounded
    // stays buffered instead of being silently dropped. The actual credential request is kicked
    // off on `scope` (tied to the composition, not to this collector), so it survives across a
    // configuration change (e.g. rotating while the account picker is up) the same way the
    // previous plain `collectLatest` version did - only the *collection* is now lifecycle-gated,
    // not the in-flight request itself.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LoginEvent.LaunchCredentialRequest -> {
                scope.launch {
                    try {
                        val result = credentialClient.getIdToken(
                            context = context,
                            serverClientId = viewModel.googleWebClientId.orEmpty(),
                            hashedNonce = event.hashedNonce,
                        )
                        viewModel.onCredentialResult(result)
                    } catch (e: CancellationException) {
                        viewModel.onCredentialResult(GoogleIdResult.Cancelled)
                        throw e
                    }
                }
            }
        }
    }

    LoginContent(
        loading = uiState.loading,
        onGoogleClick = viewModel::onGoogleClick,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun LoginContent(
    loading: Boolean,
    onGoogleClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.login_logo),
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 8.sp),
                color = SpotterColors.PrimaryContainer,
            )
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = SpotterColors.OnSurfaceVariant,
            )

            Spacer(modifier = Modifier.padding(top = Spacing.giant))

            SpotterButton(
                text = stringResource(
                    if (loading) R.string.login_google_button_loading else R.string.login_google_button,
                ),
                onClick = onGoogleClick,
                modifier = Modifier.fillMaxWidth(),
                size = SpotterButtonSize.Large,
                loading = loading,
                enabled = !loading,
            )
        }
    }
}

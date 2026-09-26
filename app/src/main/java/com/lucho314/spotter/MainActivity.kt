package com.lucho314.spotter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.config.AppConfig
import com.lucho314.spotter.core.navigation.DeepLink
import com.lucho314.spotter.core.navigation.DeepLinkParser
import com.lucho314.spotter.core.notifications.EXTRA_OPEN_WORKOUT
import com.lucho314.spotter.feature.root.RootUiState
import com.lucho314.spotter.feature.root.RootViewModel
import com.lucho314.spotter.feature.root.SpotterRoot
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appConfig: AppConfig

    // Lazy so a misconfigured client (empty URL/key) is never constructed just by launching the
    // activity; see AppConfig.isValid / ConfigErrorScreen.
    @Inject lateinit var supabaseClient: Lazy<SupabaseClient>

    @Inject lateinit var logger: Logger

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { rootViewModel.uiState.value == RootUiState.Loading }

        // Only process the launching intent on a genuinely fresh start: a config-change
        // recreation (rotation, etc.) passes the same intent again and would otherwise re-run
        // handleDeeplinks/queue the same import code a second time.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            SpotterRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (!appConfig.isValid) return
        // Reopening the app from Recents redelivers the original launching intent; without this
        // guard an old auth callback/import link would be processed again.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (intent.getBooleanExtra(EXTRA_OPEN_WORKOUT, false)) {
            rootViewModel.onOpenWorkoutRequested()
        }
        when (val link = DeepLinkParser.parse(intent.dataString)) {
            is DeepLink.AuthCallback -> supabaseClient.get().handleDeeplinks(
                intent = intent,
                onError = { logger.w(TAG, "auth callback failed") },
            )

            is DeepLink.ImportRoutine -> rootViewModel.onDeepLink(link)
            null -> Unit
        }
    }
}

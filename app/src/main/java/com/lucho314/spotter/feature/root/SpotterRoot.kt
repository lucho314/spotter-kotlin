@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lucho314.spotter.feature.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lucho314.spotter.R
import com.lucho314.spotter.core.designsystem.component.LoadingState
import com.lucho314.spotter.core.designsystem.theme.Spacing
import com.lucho314.spotter.core.designsystem.theme.SpotterColors
import com.lucho314.spotter.core.designsystem.theme.SpotterTheme
import com.lucho314.spotter.core.navigation.DashboardRoute
import com.lucho314.spotter.core.navigation.ImportCodeRoute
import com.lucho314.spotter.core.navigation.OnboardingRoute
import com.lucho314.spotter.core.navigation.SpotterNavHost
import com.lucho314.spotter.core.navigation.TopLevelDestination
import com.lucho314.spotter.core.navigation.navigateToTopLevel
import com.lucho314.spotter.core.navigation.navigateToWorkout
import com.lucho314.spotter.domain.model.ShareCode
import com.lucho314.spotter.feature.auth.LoginScreen
import kotlinx.coroutines.flow.first

@Composable
fun SpotterRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val uiState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val pendingImportCode by rootViewModel.pendingImportCode.collectAsStateWithLifecycle()
    val pendingOpenWorkout by rootViewModel.pendingOpenWorkout.collectAsStateWithLifecycle()
    val isOnline by rootViewModel.isOnline.collectAsStateWithLifecycle()

    SpotterTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            when (val state = uiState) {
                RootUiState.Loading -> LoadingState()
                RootUiState.ConfigError -> ConfigErrorScreen()
                RootUiState.SignedOut -> LoginScreen()
                is RootUiState.SignedIn -> AuthenticatedApp(
                    needsOnboarding = state.needsOnboarding,
                    pendingImportCode = pendingImportCode,
                    onDeepLinkConsumed = rootViewModel::consumeDeepLink,
                    pendingOpenWorkout = pendingOpenWorkout,
                    onOpenWorkoutConsumed = rootViewModel::consumeOpenWorkout,
                    isOnline = isOnline,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(
    needsOnboarding: Boolean,
    pendingImportCode: ShareCode?,
    onDeepLinkConsumed: () -> Unit,
    pendingOpenWorkout: Boolean,
    onOpenWorkoutConsumed: () -> Unit,
    isOnline: Boolean,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
    }

    // Pinned to the value seen on the first composition: OnboardingScreen itself navigates to
    // DashboardRoute (with popUpTo) once done, so a later change to `needsOnboarding` must not
    // make NavHost rebuild its graph (and reset the back stack) out from under that navigation.
    val startDestination = remember { if (needsOnboarding) OnboardingRoute else DashboardRoute }

    // Hides the bottom bar instead of letting it float on top of the keyboard: on a top-level
    // screen with its own text input (e.g. RoutinesScreen's import-code field), the IME would
    // otherwise sit right above (and visually compete with) a bar whose destinations are moot
    // while the user is mid-typing anyway.
    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        bottomBar = {
            if (currentTopLevel != null && !imeVisible) {
                // RN's tab bar: `surfaceLow` background, no border/elevation, and no pill behind
                // the selected icon - just lime icon+label vs. gray, unlike Material3's default
                // `primaryContainer` indicator pill.
                NavigationBar(containerColor = SpotterColors.SurfaceLow) {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentTopLevel,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes), style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpotterColors.PrimaryContainer,
                                selectedTextColor = SpotterColors.PrimaryContainer,
                                unselectedIconColor = SpotterColors.OnSurfaceVariant,
                                unselectedTextColor = SpotterColors.OnSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // `consumeWindowInsets(padding)`: this is the one and only place system bar insets (status
        // bar / nav bar via the bottom bar) are turned into real padding. Per-screen Scaffolds
        // nested inside `SpotterNavHost` keep Material3's default `contentWindowInsets`, which
        // would otherwise pad for those same insets a second time now that edge-to-edge is on
        // everywhere (`MainActivity.enableEdgeToEdge`); consuming them here makes that padding a
        // no-op for descendants instead. IME insets are deliberately left untouched - each screen
        // that needs to react to the keyboard applies its own `imePadding()`.
        Column(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            // Global (not tied to any one screen, review carry-over): a plain per-screen banner
            // (like WorkoutScreen's own) never shows while browsing routines/history/etc, and
            // reappears from scratch when navigating into a screen that has one.
            if (!isOnline) {
                Text(
                    text = stringResource(R.string.offline_banner_global),
                    style = MaterialTheme.typography.labelMedium,
                    color = SpotterColors.OnSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(SpotterColors.SurfaceHigh).padding(Spacing.xs),
                )
            }
            SpotterNavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f),
            )
        }

        // Scaffold's content lambda is subcomposed during measure, so on a cold start this can
        // run before NavHost has called `navController.setGraph(...)`; navigating too early
        // throws `IllegalStateException: You must call setGraph() before calling getGraph()`.
        // Living in the same content lambda (right after SpotterNavHost) gives this the best
        // chance to run after the graph is set, but the flow below is the real guard: it
        // suspends until there is an actual back stack entry, i.e. the graph is ready.
        // `currentBackStackEntry` is a plain property (not snapshot-backed), so observing it via
        // `snapshotFlow` never emits; `currentBackStackEntryFlow` is the navigation library's own
        // flow of back stack entries and fires as soon as the graph is set.
        LaunchedEffect(pendingImportCode) {
            val code = pendingImportCode ?: return@LaunchedEffect
            navController.currentBackStackEntryFlow.first()
            // popUpTo<ImportCodeRoute>: a second deep link replaces an already-open preview instead
            // of stacking on top of it. A no-op if there's no ImportCodeRoute in the back stack yet.
            navController.navigate(ImportCodeRoute(code.value)) { popUpTo<ImportCodeRoute> { inclusive = true } }
            onDeepLinkConsumed()
        }

        // Tapping the "Descanso terminado" notification while the app was backgrounded/closed.
        LaunchedEffect(pendingOpenWorkout) {
            if (!pendingOpenWorkout) return@LaunchedEffect
            navController.currentBackStackEntryFlow.first()
            navController.navigateToWorkout()
            onOpenWorkoutConsumed()
        }
    }
}

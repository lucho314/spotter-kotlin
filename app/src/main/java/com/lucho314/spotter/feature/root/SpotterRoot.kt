package com.lucho314.spotter.feature.root

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lucho314.spotter.core.designsystem.component.LoadingState
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

    Scaffold(
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentTopLevel,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        SpotterNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        )

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

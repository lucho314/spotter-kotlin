package com.lucho314.spotter.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.lucho314.spotter.R

/** Destinations shown in the bottom navigation bar; the bar is hidden on any other route. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, Icons.Outlined.Home, R.string.nav_dashboard),
    ROUTINES(RoutinesRoute, Icons.Outlined.FitnessCenter, R.string.nav_routines),
    HISTORY(HistoryRoute, Icons.Outlined.History, R.string.nav_history),
    PROGRESS(ProgressRoute, Icons.AutoMirrored.Outlined.TrendingUp, R.string.nav_progress),
    PROFILE(ProfileRoute, Icons.Outlined.Person, R.string.nav_profile),
}

/** Navigates to a bottom-bar destination, preserving each tab's own back stack (`saveState`/`restoreState`). */
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

package com.lucho314.spotter.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lucho314.spotter.feature.auth.OnboardingScreen
import com.lucho314.spotter.feature.common.ComingSoonScreen
import com.lucho314.spotter.feature.exercise.ExerciseDetailScreen
import com.lucho314.spotter.feature.profile.ProfileScreen
import com.lucho314.spotter.feature.routines.addexercise.AddExerciseScreen
import com.lucho314.spotter.feature.routines.detail.RoutineDetailScreen
import com.lucho314.spotter.feature.routines.edit.RoutineEditScreen
import com.lucho314.spotter.feature.routines.list.ArchivedRoutinesScreen
import com.lucho314.spotter.feature.routines.list.RoutinesScreen
import com.lucho314.spotter.feature.templates.detail.TemplateDetailScreen
import com.lucho314.spotter.feature.templates.list.TemplatesScreen
import com.lucho314.spotter.feature.workout.WorkoutScreen

/** Nav-result relay keys (see the "returning a result" pattern in [SpotterNavHost]'s two `getBackStackEntry`/`previousBackStackEntry` usages below). */
private const val KEY_ROUTINES_CREATED_FROM_TEMPLATE = "routines_created_from_template"
private const val KEY_ADDED_EXERCISE_NAME = "added_exercise_name"

/**
 * [androidx.lifecycle.compose.dropUnlessResumed] only wraps a no-arg `() -> Unit` (it's meant for
 * `onBack`-style callbacks); this is the same "only while this destination is actually RESUMED"
 * guard for the parameterized *user-click* navigation callbacks below (`onRoutineClick: (String)
 * -> Unit` and similar) - a rapid double-tap, or a tap racing a system back gesture, must not fire
 * the same `navigate()` twice.
 *
 * **Not** used for callbacks driven by a ViewModel event (`onSaved`, `onArchived`,
 * `onExerciseAdded`, `onRoutinesCreated`, Onboarding's `onDone`) - see [ObserveAsEvents][com.lucho314.spotter.feature.common.ObserveAsEvents]'s
 * KDoc for why guarding those the same way silently drops them forever instead of just once.
 */
private fun <T> LifecycleOwner.dropUnlessResumed1(action: (T) -> Unit): (T) -> Unit = { arg ->
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action(arg)
}

private fun <A, B> LifecycleOwner.dropUnlessResumed2(action: (A, B) -> Unit): (A, B) -> Unit = { a, b ->
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action(a, b)
}

/**
 * Opens the active workout. `launchSingleTop`: there is only ever one active workout, so a second
 * `WorkoutRoute` on top of an existing one is always a bug - e.g. tapping the "Descanso terminado"
 * notification while `WorkoutScreen` is already the current destination, or an unguarded
 * event-driven call (see `RoutineDetailScreen`'s `onOpenWorkout`) racing a user click.
 */
fun NavController.navigateToWorkout() {
    navigate(WorkoutRoute) { launchSingleTop = true }
}

/**
 * Single `NavHost` for the authenticated app. Top-level destinations, routines/templates/exercise
 * detail (phase 3) and the active workout (phase 4) are wired up; dashboard, history, progress,
 * import and the AI import flow are still placeholders until their feature phase lands (see
 * MIGRATION_PLAN.md section 10).
 *
 * Two different kinds of callback are wired up here, deliberately guarded differently:
 * - **User clicks** (`onBack`, `onRoutineClick`, etc.): guarded with [dropUnlessResumed] (no-arg)
 *   or [dropUnlessResumed1]/[dropUnlessResumed2] (parameterized), so a double-tap or a tap racing
 *   a back gesture can't fire `navigate()` twice.
 * - **Async-operation results** (`onSaved`, `onArchived`, `onExerciseAdded`, `onRoutinesCreated`,
 *   `RoutineDetailScreen`'s `onOpenWorkout`, Onboarding's `onDone`): driven by a ViewModel event
 *   that can legitimately arrive while this screen is backgrounded (mid network call). These are **not** guarded here - see
 *   `com.lucho314.spotter.feature.common.ObserveAsEvents`'s KDoc: guarding the callback instead of
 *   the event *collection* silently drops the result forever (review bug), since the event has
 *   already been consumed from the channel by the time the guard runs.
 */
@Composable
fun SpotterNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<DashboardRoute> { ComingSoonScreen() }
        composable<RoutinesRoute> { entry ->
            val lifecycleOwner = LocalLifecycleOwner.current
            // `getStateFlow` (observed reactively), not a one-off `get()`: this entry's composition
            // can be kept alive in the back stack while another destination is on top, so a plain
            // snapshot read here wouldn't reliably pick up a value set *after* this composable last
            // ran but before it's shown again. `remember(entry)`: without it, a recomposition of
            // this composable slot could re-evaluate `getStateFlow(...)` and hand `collectAsState`
            // a "new" (even if equal) Flow, which can cancel an in-flight `showSnackbar` in
            // `RoutinesScreen` (review carry-over).
            val justCreatedRoutinesFromTemplateFlow = remember(entry) {
                entry.savedStateHandle.getStateFlow(KEY_ROUTINES_CREATED_FROM_TEMPLATE, false)
            }
            val justCreatedRoutinesFromTemplate by justCreatedRoutinesFromTemplateFlow.collectAsState()
            RoutinesScreen(
                onCreateClick = dropUnlessResumed { navController.navigate(RoutineEditRoute()) },
                onRoutineClick = lifecycleOwner.dropUnlessResumed1 { routineId -> navController.navigate(RoutineDetailRoute(routineId)) },
                onTemplatesClick = dropUnlessResumed { navController.navigate(TemplatesRoute) },
                onArchivedClick = dropUnlessResumed { navController.navigate(ArchivedRoutinesRoute) },
                onImportCode = lifecycleOwner.dropUnlessResumed1 { code -> navController.navigate(ImportCodeRoute(code)) },
                onImportImageClick = dropUnlessResumed { navController.navigate(ImportImageRoute) },
                onResumeWorkoutClick = dropUnlessResumed { navController.navigateToWorkout() },
                justCreatedRoutinesFromTemplate = justCreatedRoutinesFromTemplate,
                onJustCreatedRoutinesConsumed = { entry.savedStateHandle.remove<Boolean>(KEY_ROUTINES_CREATED_FROM_TEMPLATE) },
            )
        }
        composable<ArchivedRoutinesRoute> {
            ArchivedRoutinesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<RoutineEditRoute> {
            RoutineEditScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onTemplatesClick = dropUnlessResumed { navController.navigate(TemplatesRoute) },
                onSaved = { routineId ->
                    navController.navigate(RoutineDetailRoute(routineId)) {
                        popUpTo(RoutinesRoute)
                    }
                },
            )
        }
        composable<RoutineDetailRoute> { entry ->
            val lifecycleOwner = LocalLifecycleOwner.current
            // See the analogous `getStateFlow`/`remember(entry)` usage (and its KDoc) on
            // RoutinesRoute above.
            val addedExerciseNameFlow = remember(entry) {
                entry.savedStateHandle.getStateFlow<String?>(KEY_ADDED_EXERCISE_NAME, null)
            }
            val addedExerciseName by addedExerciseNameFlow.collectAsState()
            RoutineDetailScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEditClick = lifecycleOwner.dropUnlessResumed1 { routineId -> navController.navigate(RoutineEditRoute(routineId)) },
                onAddExerciseClick = lifecycleOwner.dropUnlessResumed2 { routineId, dayNumber ->
                    navController.navigate(AddExerciseRoute(routineId, dayNumber))
                },
                onExerciseClick = lifecycleOwner.dropUnlessResumed1 { exerciseId -> navController.navigate(ExerciseDetailRoute(exerciseId)) },
                // Unguarded: driven by `RoutineDetailEvent.WorkoutStarted`, which ObserveAsEvents
                // can deliver at STARTED (e.g. returning from background, before ON_RESUME), where
                // `dropUnlessResumed` would swallow it - the workout would be created in Room but
                // never opened. `navigateToWorkout()`'s `launchSingleTop` covers double-firing.
                onOpenWorkout = { navController.navigateToWorkout() },
                onArchived = { navController.popBackStack() },
                addedExerciseName = addedExerciseName,
                onAddedExerciseNameConsumed = { entry.savedStateHandle.remove<String>(KEY_ADDED_EXERCISE_NAME) },
            )
        }
        composable<AddExerciseRoute> {
            AddExerciseScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onExerciseAdded = { name ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_ADDED_EXERCISE_NAME, name)
                    navController.popBackStack()
                },
            )
        }
        composable<TemplatesRoute> {
            val lifecycleOwner = LocalLifecycleOwner.current
            TemplatesScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onTemplateClick = lifecycleOwner.dropUnlessResumed1 { templateId -> navController.navigate(TemplateDetailRoute(templateId)) },
            )
        }
        composable<TemplateDetailRoute> {
            val lifecycleOwner = LocalLifecycleOwner.current
            TemplateDetailScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onExerciseClick = lifecycleOwner.dropUnlessResumed1 { exerciseId -> navController.navigate(ExerciseDetailRoute(exerciseId)) },
                onRoutinesCreated = {
                    // `getBackStackEntry` throws if RoutinesRoute isn't actually in the back stack
                    // (shouldn't normally happen - it's this graph's start destination - but this
                    // guards against it rather than crashing the whole adoption flow).
                    val routinesEntry = runCatching { navController.getBackStackEntry(RoutinesRoute) }.getOrNull()
                    if (routinesEntry != null) {
                        routinesEntry.savedStateHandle[KEY_ROUTINES_CREATED_FROM_TEMPLATE] = true
                        navController.popBackStack(RoutinesRoute, inclusive = false)
                    } else {
                        navController.navigate(RoutinesRoute)
                    }
                },
            )
        }
        composable<ExerciseDetailRoute> {
            ExerciseDetailScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<HistoryRoute> { ComingSoonScreen() }
        composable<ProgressRoute> { ComingSoonScreen() }
        composable<ProfileRoute> { ProfileScreen() }
        composable<WorkoutRoute> {
            WorkoutScreen(
                onFinished = { navController.popBackStack() },
                onDiscarded = { navController.popBackStack() },
            )
        }
        // The real import-by-code and AI-import screens ship in a later phase; these placeholders
        // just give a destination to land on (the pending deep link, and the routines list's
        // shortcuts).
        composable<ImportCodeRoute> { ComingSoonScreen() }
        composable<ImportImageRoute> { ComingSoonScreen() }
    }
}

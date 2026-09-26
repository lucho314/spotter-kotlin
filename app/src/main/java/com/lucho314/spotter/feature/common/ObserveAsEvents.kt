package com.lucho314.spotter.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects one-shot events from a Channel-backed [Flow] (a ViewModel's `events`:
 * `Channel(Channel.BUFFERED)` + `receiveAsFlow()`) so they are never silently lost while this
 * screen is backgrounded.
 *
 * A plain `LaunchedEffect(Unit) { flow.collect { ... } }` keeps collecting even while the
 * `Activity` is stopped - Compose's composition survives a stop, only *disposal* cancels a
 * `LaunchedEffect` - so an event sent then is still *received* here (removed from the channel).
 * If handling it does something that must only run while the screen is actually showing (e.g.
 * `navController.navigate(...)`, guarded with `dropUnlessResumed`), that guard would silently
 * swallow it - and by then it's already gone from the channel, forever. This is exactly what
 * happened to `onRoutinesCreated`/`onSaved`/`onExerciseAdded`/`onArchived` (review bug): all four
 * are driven by events that can legitimately arrive while backgrounded (e.g. mid network call),
 * and the guard was on the *callback*, not the *collection*.
 *
 * [androidx.lifecycle.repeatOnLifecycle] fixes this at the right layer: it cancels/restarts the
 * *collection itself* around [Lifecycle.State.STARTED], leaving anything sent while stopped
 * sitting untouched in the channel's buffer until collection resumes and actually receives it.
 * Callbacks driven by these events need no `RESUMED` guard of their own then: by the time [onEvent]
 * runs, the screen is already at least `STARTED`. `dropUnlessResumed` (and the local
 * `dropUnlessResumed1`/`dropUnlessResumed2` in `SpotterNavHost`) stay reserved for *user-click*
 * callbacks, where the concern is a double-tap, not a dropped background event.
 */
@Composable
fun <T> ObserveAsEvents(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { event -> latestOnEvent(event) }
        }
    }
}

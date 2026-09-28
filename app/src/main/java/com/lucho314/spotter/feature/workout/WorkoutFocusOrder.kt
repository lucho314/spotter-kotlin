package com.lucho314.spotter.feature.workout

import com.lucho314.spotter.domain.model.ActiveSet

/** Which of a set's two inputs is being edited, for the fast keyboard-entry flow ([WorkoutFocusOrder]). */
enum class SetInputField { WEIGHT, REPS }

/** A specific set's input field the keyboard should move focus to next. */
data class SetFocusTarget(val setId: String, val field: SetInputField)

/**
 * Pure "what's the next field to focus" rule for the active-workout set table's fast keyboard
 * entry: weight -> reps of the same set, then reps -> weight of the next not-yet-completed set
 * (skipping already-completed ones, which don't render an input at all - see `WorkoutScreen`'s
 * `SetRow`). Returns `null` when there is nothing left to fill in, meaning the IME action should
 * be Done instead of Next, and pressing it should just hide the keyboard.
 */
object WorkoutFocusOrder {
    fun next(sets: List<ActiveSet>, currentSetId: String, currentField: SetInputField): SetFocusTarget? =
        when (currentField) {
            SetInputField.WEIGHT -> SetFocusTarget(currentSetId, SetInputField.REPS)
            SetInputField.REPS -> {
                val currentIndex = sets.indexOfFirst { it.id == currentSetId }
                if (currentIndex == -1) {
                    null
                } else {
                    sets.drop(currentIndex + 1)
                        .firstOrNull { !it.isCompleted }
                        ?.let { SetFocusTarget(it.id, SetInputField.WEIGHT) }
                }
            }
        }
}

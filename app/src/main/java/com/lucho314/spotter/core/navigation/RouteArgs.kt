package com.lucho314.spotter.core.navigation

/**
 * Argument keys shared between the [Routes] `@Serializable` route classes and the feature
 * ViewModels that read them via `SavedStateHandle["..."]` directly (not `toRoute<T>()`: see
 * `ExerciseDetailViewModel`'s KDoc for why). Each constant's value **must** match its route's
 * constructor property name exactly - Navigation stores each argument in the destination's
 * `SavedStateHandle` under that property name - which [RouteArgsTest] checks via reflection so a
 * rename on either side can't silently break the other.
 */
object RouteArgs {
    const val ROUTINE_ID = "routineId"
    const val DAY_NUMBER = "dayNumber"
    const val TEMPLATE_ID = "templateId"
    const val EXERCISE_ID = "exerciseId"
    const val SESSION_ID = "sessionId"
}

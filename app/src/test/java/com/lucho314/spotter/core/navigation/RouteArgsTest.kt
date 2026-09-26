package com.lucho314.spotter.core.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [RouteArgs]' constants are read by ViewModels via `SavedStateHandle[key]` (see
 * `ExerciseDetailViewModel`'s KDoc for why not `toRoute<T>()`), so they must exactly match the
 * corresponding route's constructor property name. Plain Java reflection (no `kotlin-reflect`
 * needed) on each data class's declared fields catches a rename on either side that would
 * otherwise only fail at runtime (a `checkNotNull` crash, or a silently-null optional arg).
 */
class RouteArgsTest {

    private fun fieldNames(clazz: Class<*>): Set<String> = clazz.declaredFields.map { it.name }.toSet()

    @Test
    fun `RoutineDetailRoute has a routineId field matching RouteArgs`() {
        assertThat(fieldNames(RoutineDetailRoute::class.java)).contains(RouteArgs.ROUTINE_ID)
    }

    @Test
    fun `RoutineEditRoute has a routineId field matching RouteArgs`() {
        assertThat(fieldNames(RoutineEditRoute::class.java)).contains(RouteArgs.ROUTINE_ID)
    }

    @Test
    fun `AddExerciseRoute has routineId and dayNumber fields matching RouteArgs`() {
        val fields = fieldNames(AddExerciseRoute::class.java)
        assertThat(fields).contains(RouteArgs.ROUTINE_ID)
        assertThat(fields).contains(RouteArgs.DAY_NUMBER)
    }

    @Test
    fun `TemplateDetailRoute has a templateId field matching RouteArgs`() {
        assertThat(fieldNames(TemplateDetailRoute::class.java)).contains(RouteArgs.TEMPLATE_ID)
    }

    @Test
    fun `ExerciseDetailRoute has an exerciseId field matching RouteArgs`() {
        assertThat(fieldNames(ExerciseDetailRoute::class.java)).contains(RouteArgs.EXERCISE_ID)
    }

    @Test
    fun `SessionDetailRoute has a sessionId field matching RouteArgs`() {
        assertThat(fieldNames(SessionDetailRoute::class.java)).contains(RouteArgs.SESSION_ID)
    }
}

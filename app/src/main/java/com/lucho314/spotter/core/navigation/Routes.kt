package com.lucho314.spotter.core.navigation

import kotlinx.serialization.Serializable

// Top-level (bottom bar) destinations.
@Serializable data object DashboardRoute
@Serializable data object RoutinesRoute
@Serializable data object HistoryRoute
@Serializable data object ProgressRoute
@Serializable data object ProfileRoute

// Routines.
@Serializable data class RoutineDetailRoute(val routineId: String)
@Serializable data class RoutineEditRoute(val routineId: String? = null) // null = create
@Serializable data class AddExerciseRoute(val routineId: String, val dayNumber: Int? = null)
@Serializable data object ArchivedRoutinesRoute

// Templates.
@Serializable data object TemplatesRoute
@Serializable data class TemplateDetailRoute(val templateId: String)

// Import.
@Serializable data class ImportCodeRoute(val code: String)
@Serializable data object ImportImageRoute

// Workout.
@Serializable data object WorkoutRoute

// History.
@Serializable data class SessionDetailRoute(val sessionId: String)

// Exercise.
@Serializable data class ExerciseDetailRoute(val exerciseId: Int)

// Onboarding.
@Serializable data object OnboardingRoute

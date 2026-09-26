package com.lucho314.spotter.domain.model

enum class TemplateGoal(val apiValue: String) {
    STRENGTH("strength"),
    HYPERTROPHY("hypertrophy"),
    FAT_LOSS("fat_loss"),
    GENERAL("general"),
    ;

    companion object {
        fun fromApi(value: String?): TemplateGoal? = entries.firstOrNull { it.apiValue == value }
    }
}

data class RoutineTemplateSummary(
    val id: String,
    val name: String,
    val description: String?,
    val goal: TemplateGoal,
    val difficulty: Difficulty,
    val daysPerWeek: Int,
)

data class TemplateExercise(
    val exerciseId: Int,
    val exercise: Exercise?,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)

data class TemplateDay(
    val id: String,
    val dayNumber: Int,
    val name: String,
    val description: String?,
    val exercises: List<TemplateExercise>,
)

data class TemplateDetail(
    val summary: RoutineTemplateSummary,
    val days: List<TemplateDay>,
)

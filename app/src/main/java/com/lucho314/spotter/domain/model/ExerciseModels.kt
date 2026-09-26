package com.lucho314.spotter.domain.model

/** Equipment used by an [Exercise]. Unknown/unmapped API values fall back to [OTHER]. */
enum class Equipment(val apiValue: String) {
    BARBELL("barbell"),
    DUMBBELL("dumbbell"),
    MACHINE("machine"),
    CABLE("cable"),
    BODYWEIGHT("bodyweight"),
    KETTLEBELL("kettlebell"),
    BAND("band"),
    OTHER("other"),
    ;

    companion object {
        fun fromApi(value: String?): Equipment = entries.firstOrNull { it.apiValue == value } ?: OTHER
    }
}

enum class Difficulty(val apiValue: String) {
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced"),
    ;

    companion object {
        fun fromApi(value: String?): Difficulty? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class ExerciseCategory(val apiValue: String) {
    COMPOUND("compound"),
    ISOLATION("isolation"),
    CARDIO("cardio"),
    STRETCH("stretch"),
    PLYOMETRIC("plyometric"),
    ;

    companion object {
        fun fromApi(value: String?): ExerciseCategory? = entries.firstOrNull { it.apiValue == value }
    }
}

data class MuscleGroup(
    val id: Int,
    val name: String,
    val nameEn: String,
)

data class Exercise(
    val id: Int,
    val name: String,
    val nameEn: String,
    val muscleGroup: MuscleGroup?,
    val equipment: Equipment,
    val imageUrl: String?,
    /** `gif_url` in the DB: despite the name, most rows are `.mp4`/`.webm` video URLs. */
    val mediaUrl: String?,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val difficulty: Difficulty?,
    val category: ExerciseCategory?,
)

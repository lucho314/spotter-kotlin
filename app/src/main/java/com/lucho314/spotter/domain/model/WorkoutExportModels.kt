package com.lucho314.spotter.domain.model

/** Output formats offered by [com.lucho314.spotter.domain.repository.WorkoutExportRepository]. */
enum class ExportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    STORY("image/jpeg", "jpg"),
}

/** Story renderings only ever show this many exercises before collapsing the rest into "+N más". */
const val STORY_MAX_EXERCISES = 4

/**
 * Everything a renderer needs to draw a workout export, with every number already formatted as
 * user-facing text (peso/volumen/duración/fecha, es-AR): the words with plurals ("10 reps", "+2
 * ejercicios más") are built by the renderers themselves with `Resources.getQuantityString`, since
 * this type must stay free of Android/UI concerns to live in `domain`.
 */
data class WorkoutExportData(
    val sessionId: String,
    /** Sanitized; null means the renderer must fall back to `R.string.history_free_workout`. */
    val routineName: String?,
    /** e.g. "Martes 3 de marzo de 2026" (es-AR), from `completedAt ?: startedAt`. */
    val dateText: String,
    /** [com.lucho314.spotter.domain.calc.WorkoutMath.formatDuration] - "45 min" | "1h 5min" | "—". */
    val durationText: String,
    /** [com.lucho314.spotter.domain.calc.NumberFormatter.formatVolume] - "12.345 kg". */
    val totalVolumeText: String,
    /** Working sets only (warmups excluded). */
    val totalSets: Int,
    /** "kg" | "lb". */
    val unitLabel: String,
    val exercises: List<ExportExercise>,
) {
    val storyExercises: List<ExportExercise> get() = exercises.take(STORY_MAX_EXERCISES)
    val hiddenStoryExerciseCount: Int get() = (exercises.size - STORY_MAX_EXERCISES).coerceAtLeast(0)
}

data class ExportExercise(
    val exerciseId: Int,
    /** Sanitized; null means the renderer must fall back to `R.string.session_detail_unknown_exercise`. */
    val name: String?,
    /** Ordered by `setNumber`, warmups included (flagged via [ExportSetRow.isWarmup]). */
    val sets: List<ExportSetRow>,
    val workingSetCount: Int,
    /** Warmups excluded. */
    val volumeText: String,
    /** [com.lucho314.spotter.domain.calc.WorkoutMath.topSet] over working sets only; null if there are none. */
    val topSet: ExportTopSet?,
)

data class ExportSetRow(val setNumber: Int, val weightText: String, val reps: Int, val rpeText: String?, val isWarmup: Boolean)

data class ExportTopSet(val weightText: String, val reps: Int)

/** A file rendered by [com.lucho314.spotter.domain.repository.WorkoutExportRepository], ready to be shared. */
data class ExportedFile(val uri: String, val mimeType: String)

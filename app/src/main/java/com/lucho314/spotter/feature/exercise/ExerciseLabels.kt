package com.lucho314.spotter.feature.exercise

import androidx.annotation.StringRes
import com.lucho314.spotter.R
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.ExerciseCategory

/** Spanish (Río de la Plata) labels for exercise metadata (section 9.11 of the migration plan). */
@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BARBELL -> R.string.equipment_barbell
    Equipment.DUMBBELL -> R.string.equipment_dumbbell
    Equipment.MACHINE -> R.string.equipment_machine
    Equipment.CABLE -> R.string.equipment_cable
    Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
    Equipment.KETTLEBELL -> R.string.equipment_kettlebell
    Equipment.BAND -> R.string.equipment_band
    Equipment.OTHER -> R.string.equipment_other
}

@StringRes
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.BEGINNER -> R.string.difficulty_beginner
    Difficulty.INTERMEDIATE -> R.string.difficulty_intermediate
    Difficulty.ADVANCED -> R.string.difficulty_advanced
}

@StringRes
fun ExerciseCategory.labelRes(): Int = when (this) {
    ExerciseCategory.COMPOUND -> R.string.category_compound
    ExerciseCategory.ISOLATION -> R.string.category_isolation
    ExerciseCategory.CARDIO -> R.string.category_cardio
    ExerciseCategory.STRETCH -> R.string.category_stretch
    ExerciseCategory.PLYOMETRIC -> R.string.category_plyometric
}

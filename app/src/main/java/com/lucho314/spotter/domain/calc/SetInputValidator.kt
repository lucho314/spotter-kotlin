package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.WeightUnit

/** Result of validating a history-detail set edit/add form (weight text + reps text, in [WeightUnit]). */
sealed interface SetInputValidation {
    data class Valid(val weightKg: Double, val reps: Int) : SetInputValidation
    data class Invalid(val reason: ValidationReason) : SetInputValidation
}

/**
 * Validates the weight/reps text fields shown when editing or adding a set in the history detail
 * screen (`SessionDetailScreen`), converting the parsed weight to kg for storage.
 */
object SetInputValidator {

    fun validate(weightText: String, repsText: String, unit: WeightUnit): SetInputValidation {
        val rawWeight = WeightInputParser.parseWeight(weightText)
            ?: return SetInputValidation.Invalid(ValidationReason.WEIGHT_INVALID)
        val weightKg = WeightConverter.toKg(rawWeight, unit)
        Validators.workoutSetKg(weightKg)?.let { return SetInputValidation.Invalid(it) }
        val reps = WeightInputParser.parseReps(repsText)
            ?: return SetInputValidation.Invalid(ValidationReason.WORKOUT_REPS_RANGE)
        return SetInputValidation.Valid(weightKg, reps)
    }
}

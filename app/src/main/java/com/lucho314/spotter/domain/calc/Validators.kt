package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.RoutineInput

/** Domain-level input validation, shared by every screen and use case that writes these fields. */
object Validators {

    fun routineInput(input: RoutineInput): ValidationReason? {
        val name = input.name.trim()
        return when {
            name.isEmpty() -> ValidationReason.NAME_EMPTY
            name.length > 50 -> ValidationReason.NAME_TOO_LONG
            (input.description?.length ?: 0) > 200 -> ValidationReason.DESCRIPTION_TOO_LONG
            input.daysPerWeek != null && input.daysPerWeek !in 1..7 -> ValidationReason.DAYS_PER_WEEK_RANGE
            else -> null
        }
    }

    fun routineExercise(targetSets: Int, targetReps: Int, restSeconds: Int): ValidationReason? = when {
        targetSets !in 1..20 -> ValidationReason.SETS_RANGE
        targetReps !in 1..100 -> ValidationReason.REPS_RANGE
        restSeconds !in 15..600 -> ValidationReason.REST_RANGE
        else -> null
    }

    fun workoutSetKg(weightKg: Double): ValidationReason? =
        if (weightKg < 0.0 || weightKg > 1000.0) ValidationReason.WEIGHT_RANGE else null
}

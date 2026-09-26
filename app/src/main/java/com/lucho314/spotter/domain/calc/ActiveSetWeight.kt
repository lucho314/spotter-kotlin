package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.Equipment

/**
 * Shared weight-parsing rule for an in-progress [com.lucho314.spotter.domain.model.ActiveSet],
 * used by both `ToggleSetCompletionUseCase` (validating before marking a set complete) and
 * `FinishWorkoutUseCase` (converting completed sets to kg for the outbox) - kept in one place so
 * the two can never disagree on what a blank weight field means.
 *
 * Not in the migration plan's explicit `domain/calc` list (section 9.6): factored out during
 * implementation once it became clear both use cases needed the exact same "blank weight is only
 * valid for bodyweight exercises, and means 0" rule (plan section 9.5's `ToggleSetCompletionUseCase`
 * bullet).
 */
object ActiveSetWeight {

    /**
     * Parses [weightText] with [WeightInputParser.parseWeight], except a blank [weightText] is
     * `0.0` when [equipment] is [Equipment.BODYWEIGHT] (no external load to enter) instead of
     * invalid. Null means the input is genuinely invalid and the set must not be completed.
     */
    fun parse(weightText: String, equipment: Equipment): Double? =
        if (weightText.isBlank() && equipment == Equipment.BODYWEIGHT) {
            0.0
        } else {
            WeightInputParser.parseWeight(weightText)
        }
}

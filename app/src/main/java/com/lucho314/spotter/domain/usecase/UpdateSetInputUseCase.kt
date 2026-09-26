package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import javax.inject.Inject

/**
 * Persists a set's raw weight/reps text as the user types (debounced by
 * `feature.workout.WorkoutViewModel`) - a pure pass-through, no validation: that only happens when
 * actually completing the set ([ToggleSetCompletionUseCase]), so the user can freely type
 * intermediate, momentarily-invalid text ("7", "72,") without it being rejected mid-keystroke.
 */
class UpdateSetInputUseCase @Inject constructor(
    private val activeWorkoutRepository: ActiveWorkoutRepository,
) {
    suspend operator fun invoke(setId: String, weightText: String, repsText: String) {
        activeWorkoutRepository.updateSetInputs(setId, weightText, repsText)
    }
}

package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.RoutineInput
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `routineInput rejects an empty (or blank) name`() {
        assertThat(Validators.routineInput(RoutineInput("", null, null))).isEqualTo(ValidationReason.NAME_EMPTY)
        assertThat(Validators.routineInput(RoutineInput("   ", null, null))).isEqualTo(ValidationReason.NAME_EMPTY)
    }

    @Test
    fun `routineInput rejects a name over 50 chars`() {
        val longName = "a".repeat(51)
        assertThat(Validators.routineInput(RoutineInput(longName, null, null))).isEqualTo(ValidationReason.NAME_TOO_LONG)
    }

    @Test
    fun `routineInput rejects a description over 200 chars`() {
        val longDescription = "a".repeat(201)
        assertThat(Validators.routineInput(RoutineInput("Push", longDescription, null))).isEqualTo(ValidationReason.DESCRIPTION_TOO_LONG)
    }

    @Test
    fun `routineInput rejects daysPerWeek outside 1 to 7`() {
        assertThat(Validators.routineInput(RoutineInput("Push", null, 0))).isEqualTo(ValidationReason.DAYS_PER_WEEK_RANGE)
        assertThat(Validators.routineInput(RoutineInput("Push", null, 8))).isEqualTo(ValidationReason.DAYS_PER_WEEK_RANGE)
        assertThat(Validators.routineInput(RoutineInput("Push", null, null))).isNull()
    }

    @Test
    fun `routineInput accepts valid input`() {
        assertThat(Validators.routineInput(RoutineInput("Push Day", "Chest, shoulders, triceps", 3))).isNull()
    }

    @Test
    fun `routineExercise validates sets, reps and rest ranges`() {
        assertThat(Validators.routineExercise(targetSets = 0, targetReps = 10, restSeconds = 90)).isEqualTo(ValidationReason.SETS_RANGE)
        assertThat(Validators.routineExercise(targetSets = 21, targetReps = 10, restSeconds = 90)).isEqualTo(ValidationReason.SETS_RANGE)
        assertThat(Validators.routineExercise(targetSets = 3, targetReps = 0, restSeconds = 90)).isEqualTo(ValidationReason.REPS_RANGE)
        assertThat(Validators.routineExercise(targetSets = 3, targetReps = 101, restSeconds = 90)).isEqualTo(ValidationReason.REPS_RANGE)
        assertThat(Validators.routineExercise(targetSets = 3, targetReps = 10, restSeconds = 14)).isEqualTo(ValidationReason.REST_RANGE)
        assertThat(Validators.routineExercise(targetSets = 3, targetReps = 10, restSeconds = 601)).isEqualTo(ValidationReason.REST_RANGE)
        assertThat(Validators.routineExercise(targetSets = 3, targetReps = 10, restSeconds = 90)).isNull()
    }

    @Test
    fun `workoutSetKg validates the 0 to 1000 range`() {
        assertThat(Validators.workoutSetKg(-1.0)).isEqualTo(ValidationReason.WEIGHT_RANGE)
        assertThat(Validators.workoutSetKg(1000.01)).isEqualTo(ValidationReason.WEIGHT_RANGE)
        assertThat(Validators.workoutSetKg(0.0)).isNull()
        assertThat(Validators.workoutSetKg(1000.0)).isNull()
    }
}

package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.WeightUnit
import org.junit.Test

class SetInputValidatorTest {

    @Test
    fun `accepts a comma decimal in kg`() {
        val result = SetInputValidator.validate("72,5", "10", WeightUnit.KG)

        assertThat(result).isEqualTo(SetInputValidation.Valid(72.5, 10))
    }

    @Test
    fun `converts lb to kg`() {
        val result = SetInputValidator.validate("220.46", "10", WeightUnit.LB)

        assertThat((result as SetInputValidation.Valid).weightKg).isWithin(0.01).of(100.0)
    }

    @Test
    fun `empty weight is invalid`() {
        val result = SetInputValidator.validate("", "10", WeightUnit.KG)

        assertThat(result).isEqualTo(SetInputValidation.Invalid(ValidationReason.WEIGHT_INVALID))
    }

    @Test
    fun `a weight over 1000kg-equivalent is invalid`() {
        val result = SetInputValidator.validate("1001", "10", WeightUnit.KG)

        assertThat(result).isInstanceOf(SetInputValidation.Invalid::class.java)
    }

    @Test
    fun `reps out of 1 to 200 is WORKOUT_REPS_RANGE`() {
        assertThat(SetInputValidator.validate("80", "0", WeightUnit.KG))
            .isEqualTo(SetInputValidation.Invalid(ValidationReason.WORKOUT_REPS_RANGE))
        assertThat(SetInputValidator.validate("80", "201", WeightUnit.KG))
            .isEqualTo(SetInputValidation.Invalid(ValidationReason.WORKOUT_REPS_RANGE))
    }
}

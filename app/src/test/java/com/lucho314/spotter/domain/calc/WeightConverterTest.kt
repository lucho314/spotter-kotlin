package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WeightUnit
import org.junit.Test

class WeightConverterTest {

    @Test
    fun `100 lb converts to 45,36 kg`() {
        assertThat(WeightConverter.toKg(100.0, WeightUnit.LB)).isWithin(1e-9).of(45.36)
    }

    @Test
    fun `kg round-trips through lb`() {
        val kg = 80.0
        val lb = WeightConverter.fromKg(kg, WeightUnit.LB)
        val backToKg = WeightConverter.toKg(lb, WeightUnit.LB)

        assertThat(backToKg).isWithin(0.01).of(kg)
    }

    @Test
    fun `toKg with KG unit is the identity, rounded`() {
        assertThat(WeightConverter.toKg(72.567, WeightUnit.KG)).isWithin(1e-9).of(72.57)
    }

    @Test
    fun `format drops trailing decimal zeros`() {
        assertThat(WeightConverter.format(80.0, WeightUnit.KG)).isEqualTo("80")
        assertThat(WeightConverter.format(72.5, WeightUnit.KG)).isEqualTo("72.5")
    }

    @Test
    fun `rounds using the decimal (not the binary) representation of the double`() {
        // 2.675 is stored as 2.67499999999999982236... in binary; BigDecimal(Double) would round
        // this down to 2.67. BigDecimal.valueOf(Double) goes through Double.toString() first and
        // rounds it up to 2.68, matching what a human reading "2.675" would expect.
        assertThat(WeightConverter.toKg(2.675, WeightUnit.KG)).isWithin(1e-9).of(2.68)
    }
}

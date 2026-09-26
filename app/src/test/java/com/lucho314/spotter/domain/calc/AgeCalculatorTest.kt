package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class AgeCalculatorTest {

    @Test
    fun `the day before the birthday, age hasn't incremented yet`() {
        val birth = LocalDate.of(2000, 6, 15)
        val dayBefore = LocalDate.of(2026, 6, 14)

        assertThat(AgeCalculator.age(birth, dayBefore)).isEqualTo(25)
    }

    @Test
    fun `on the birthday itself, age increments`() {
        val birth = LocalDate.of(2000, 6, 15)
        val birthday = LocalDate.of(2026, 6, 15)

        assertThat(AgeCalculator.age(birth, birthday)).isEqualTo(26)
    }

    @Test
    fun `a Feb 29 birthday in a non-leap year turns on Mar 1`() {
        val birth = LocalDate.of(2000, 2, 29)

        assertThat(AgeCalculator.age(birth, LocalDate.of(2026, 2, 28))).isEqualTo(25)
        assertThat(AgeCalculator.age(birth, LocalDate.of(2026, 3, 1))).isEqualTo(26)
    }
}

package com.lucho314.spotter.feature.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BirthDateInputTest {

    @Test
    fun `formats progressively as digits are typed`() {
        assertThat(BirthDateInput.format("")).isEqualTo("")
        assertThat(BirthDateInput.format("0")).isEqualTo("0")
        assertThat(BirthDateInput.format("01")).isEqualTo("01")
        assertThat(BirthDateInput.format("010")).isEqualTo("01/0")
        assertThat(BirthDateInput.format("0101")).isEqualTo("01/01")
        assertThat(BirthDateInput.format("0101199")).isEqualTo("01/01/199")
        assertThat(BirthDateInput.format("01011990")).isEqualTo("01/01/1990")
    }

    @Test
    fun `ignores non-digit characters`() {
        assertThat(BirthDateInput.format("01/01/1990x")).isEqualTo("01/01/1990")
    }

    @Test
    fun `is capped at 8 digits`() {
        assertThat(BirthDateInput.format("0101199012")).isEqualTo("01/01/1990")
    }
}

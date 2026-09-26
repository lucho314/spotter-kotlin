package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NumberFormatterTest {

    @Test
    fun `formatThousands uses a dot as the thousands separator`() {
        assertThat(NumberFormatter.formatThousands(12345)).isEqualTo("12.345")
        assertThat(NumberFormatter.formatThousands(999)).isEqualTo("999")
    }

    @Test
    fun `formatVolume rounds and appends the unit`() {
        assertThat(NumberFormatter.formatVolume(12345.6)).isEqualTo("12.346 kg")
    }
}

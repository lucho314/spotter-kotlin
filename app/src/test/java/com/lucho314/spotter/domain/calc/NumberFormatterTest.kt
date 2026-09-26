package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WeightUnit
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

    @Test
    fun `formatVolume converts to lb and appends lb`() {
        // 100 kg -> 220.46 lb, rounded to 220.
        assertThat(NumberFormatter.formatVolume(100.0, WeightUnit.LB)).isEqualTo("220 lb")
    }

    @Test
    fun `formatVolume with KG matches the single-arg overload`() {
        assertThat(NumberFormatter.formatVolume(12345.6, WeightUnit.KG)).isEqualTo("12.346 kg")
    }
}

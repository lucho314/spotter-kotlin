package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeightInputParserTest {

    @Test
    fun `accepts a comma as the decimal separator`() {
        assertThat(WeightInputParser.parseWeight("72,5")).isWithin(1e-9).of(72.5)
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertThat(WeightInputParser.parseWeight(" 80 ")).isWithin(1e-9).of(80.0)
    }

    @Test
    fun `rejects negatives`() {
        assertThat(WeightInputParser.parseWeight("-1")).isNull()
    }

    @Test
    fun `rejects non-numeric text`() {
        assertThat(WeightInputParser.parseWeight("abc")).isNull()
    }

    @Test
    fun `rejects scientific notation even though it parses to a valid number`() {
        assertThat(WeightInputParser.parseWeight("1e3")).isNull()
    }

    @Test
    fun `rejects more than two decimal digits`() {
        assertThat(WeightInputParser.parseWeight("72.555")).isNull()
    }

    @Test
    fun `rejects weights over 1000`() {
        assertThat(WeightInputParser.parseWeight("1000")).isWithin(1e-9).of(1000.0)
        assertThat(WeightInputParser.parseWeight("1000.01")).isNull()
    }

    @Test
    fun `parseReps accepts 1 to 200`() {
        assertThat(WeightInputParser.parseReps("1")).isEqualTo(1)
        assertThat(WeightInputParser.parseReps("200")).isEqualTo(200)
        assertThat(WeightInputParser.parseReps("0")).isNull()
        assertThat(WeightInputParser.parseReps("201")).isNull()
        assertThat(WeightInputParser.parseReps("abc")).isNull()
    }
}

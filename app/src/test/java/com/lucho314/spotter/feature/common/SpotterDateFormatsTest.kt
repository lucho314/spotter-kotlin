package com.lucho314.spotter.feature.common

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

private val ZONE = ZoneId.of("America/Argentina/Buenos_Aires")

class SpotterDateFormatsTest {

    @Test
    fun `longDay formats the full weekday and month name in Spanish`() {
        val instant = Instant.parse("2025-03-03T15:00:00Z")

        assertThat(SpotterDateFormats.longDay(instant, ZONE)).isEqualTo("lunes 3 de marzo")
    }

    @Test
    fun `shortDayMonth formats as d slash M`() {
        val instant = Instant.parse("2025-03-03T15:00:00Z")

        assertThat(SpotterDateFormats.shortDayMonth(instant, ZONE)).isEqualTo("3/3")
    }

    @Test
    fun `birthDate formats as dd slash MM slash uuuu`() {
        assertThat(SpotterDateFormats.birthDate(LocalDate.of(1990, 1, 5))).isEqualTo("05/01/1990")
    }
}

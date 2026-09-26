package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class WeekRangeTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun `Sunday 23,59 belongs to the week that started the previous Monday`() {
        // Sunday 2026-01-04 23:59 local.
        val sundayNight = ZonedDateTime.of(2026, 1, 4, 23, 59, 0, 0, zone).toInstant()
        val expectedMonday = ZonedDateTime.of(2025, 12, 29, 0, 0, 0, 0, zone).toInstant()

        assertThat(WeekRange.currentWeekStart(sundayNight, zone)).isEqualTo(expectedMonday)
    }

    @Test
    fun `Monday 00,00 is already its own week start`() {
        val mondayMidnight = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, zone).toInstant()

        assertThat(WeekRange.currentWeekStart(mondayMidnight, zone)).isEqualTo(mondayMidnight)
    }
}

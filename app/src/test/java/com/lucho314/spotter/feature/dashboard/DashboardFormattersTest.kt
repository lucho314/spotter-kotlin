package com.lucho314.spotter.feature.dashboard

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

private val ZONE = ZoneId.of("America/Argentina/Buenos_Aires")

private fun at(hour: Int, minute: Int) = ZonedDateTime.of(2026, 1, 15, hour, minute, 0, 0, ZONE).toInstant()

class DashboardFormattersTest {

    @Test
    fun `greeting boundaries`() {
        assertThat(greetingFor(at(4, 59), ZONE)).isEqualTo(Greeting.NIGHT)
        assertThat(greetingFor(at(5, 0), ZONE)).isEqualTo(Greeting.MORNING)
        assertThat(greetingFor(at(11, 59), ZONE)).isEqualTo(Greeting.MORNING)
        assertThat(greetingFor(at(12, 0), ZONE)).isEqualTo(Greeting.AFTERNOON)
        assertThat(greetingFor(at(19, 59), ZONE)).isEqualTo(Greeting.AFTERNOON)
        assertThat(greetingFor(at(20, 0), ZONE)).isEqualTo(Greeting.NIGHT)
    }

    @Test
    fun `lastSessionLabel is None for a user who never trained`() {
        assertThat(lastSessionLabel(null, at(10, 0), ZONE)).isEqualTo(LastSessionLabel.None)
    }

    @Test
    fun `lastSessionLabel is Today for the same local calendar day`() {
        val last = at(8, 0)
        val now = at(20, 0)

        assertThat(lastSessionLabel(last, now, ZONE)).isEqualTo(LastSessionLabel.Today)
    }

    @Test
    fun `lastSessionLabel is Yesterday and DaysAgo`() {
        val now = at(10, 0)
        val yesterday = now.minusSeconds(24 * 3600)
        val fiveDaysAgo = now.minusSeconds(5 * 24 * 3600)

        assertThat(lastSessionLabel(yesterday, now, ZONE)).isEqualTo(LastSessionLabel.Yesterday)
        assertThat(lastSessionLabel(fiveDaysAgo, now, ZONE)).isEqualTo(LastSessionLabel.DaysAgo(5))
    }

    @Test
    fun `a midnight crossing counts as Yesterday, not Today`() {
        // 23:30 the day before vs 00:10 today - less than an hour apart, but a different local calendar day.
        val last = ZonedDateTime.of(2026, 1, 14, 23, 30, 0, 0, ZONE).toInstant()
        val now = ZonedDateTime.of(2026, 1, 15, 0, 10, 0, 0, ZONE).toInstant()

        assertThat(lastSessionLabel(last, now, ZONE)).isEqualTo(LastSessionLabel.Yesterday)
    }
}

package com.lucho314.spotter.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class RestTimerTest {

    private val timer = RestTimer(endsAt = Instant.ofEpochSecond(100), totalSeconds = 90)

    @Test
    fun `remainingSeconds counts down, rounded up`() {
        assertThat(timer.remainingSeconds(Instant.ofEpochSecond(0))).isEqualTo(100)
        // 1.2s left rounds up to 2s, not down to 1s.
        assertThat(timer.remainingSeconds(Instant.ofEpochMilli(98_800))).isEqualTo(2)
    }

    @Test
    fun `remainingSeconds never goes negative`() {
        assertThat(timer.remainingSeconds(Instant.ofEpochSecond(100))).isEqualTo(0)
        assertThat(timer.remainingSeconds(Instant.ofEpochSecond(200))).isEqualTo(0)
    }
}

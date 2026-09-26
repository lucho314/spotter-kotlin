package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class DateMappersTest {

    @Test
    fun `toInstant parses a timestamptz with a +00,00 offset`() {
        assertThat("2026-01-15T10:00:00+00:00".toInstant()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"))
    }

    @Test
    fun `toInstant parses microsecond precision`() {
        assertThat("2026-01-15T10:00:00.123456+00:00".toInstant()).isEqualTo(Instant.parse("2026-01-15T10:00:00.123456Z"))
    }

    @Test
    fun `toInstant parses a non-UTC offset`() {
        assertThat("2026-01-15T07:00:00-03:00".toInstant()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"))
    }

    @Test
    fun `toLocalDate parses a plain yyyy-MM-dd string`() {
        assertThat("2000-06-15".toLocalDate()).isEqualTo(LocalDate.of(2000, 6, 15))
    }

    @Test
    fun `toDateString formats back to yyyy-MM-dd`() {
        assertThat(LocalDate.of(2000, 6, 15).toDateString()).isEqualTo("2000-06-15")
    }
}

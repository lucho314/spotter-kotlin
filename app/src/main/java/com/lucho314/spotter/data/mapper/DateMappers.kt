package com.lucho314.spotter.data.mapper

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Parses a Postgres `timestamptz` string (including sub-second precision) into an [Instant]. */
fun String.toInstant(): Instant = OffsetDateTime.parse(this).toInstant()

/** Formats an [Instant] as an ISO-8601 UTC `timestamptz` string, for insert/update DTOs. */
fun Instant.toTimestampString(): String = DateTimeFormatter.ISO_INSTANT.format(this)

/** Parses a Postgres `date` string ("yyyy-MM-dd"). */
fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

/** Formats a [LocalDate] as a Postgres `date` string ("yyyy-MM-dd"). */
fun LocalDate.toDateString(): String = this.toString()

package com.lucho314.spotter.feature.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** es-AR (Río de la Plata) date formatting shared by the dashboard, history and profile screens. */
object SpotterDateFormats {

    private val LOCALE: Locale = Locale.forLanguageTag("es-AR")
    private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LOCALE)
    private val SHORT_DAY_MONTH = DateTimeFormatter.ofPattern("d/M", LOCALE)
    private val BIRTH_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", LOCALE)

    /** e.g. "lunes 3 de marzo". */
    fun longDay(instant: Instant, zone: ZoneId): String = LONG_DAY.format(instant.atZone(zone))

    /** e.g. "3/3", used for progress chart labels. */
    fun shortDayMonth(instant: Instant, zone: ZoneId): String = SHORT_DAY_MONTH.format(instant.atZone(zone))

    /** e.g. "03/03/1990". */
    fun birthDate(date: LocalDate): String = BIRTH_DATE.format(date)
}

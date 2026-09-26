package com.lucho314.spotter.domain.calc

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** es-AR number formatting: "." as the thousands separator. */
object NumberFormatter {

    private val LOCALE = Locale.forLanguageTag("es-AR")

    fun formatThousands(value: Long): String = NumberFormat.getIntegerInstance(LOCALE).format(value)

    fun formatVolume(volumeKg: Double): String = "${formatThousands(volumeKg.roundToLong())} kg"
}

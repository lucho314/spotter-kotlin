package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.WeightUnit
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** es-AR number formatting: "." as the thousands separator. */
object NumberFormatter {

    private val LOCALE = Locale.forLanguageTag("es-AR")

    fun formatThousands(value: Long): String = NumberFormat.getIntegerInstance(LOCALE).format(value)

    /** Rounded, in kg. Kept for callers that never convert (the old, single-unit behavior). */
    fun formatVolume(volumeKg: Double): String = "${formatThousands(volumeKg.roundToLong())} kg"

    /** Rounded, converted to [unit]. */
    fun formatVolume(volumeKg: Double, unit: WeightUnit): String {
        val value = WeightConverter.fromKg(volumeKg, unit).roundToLong()
        val suffix = if (unit == WeightUnit.KG) "kg" else "lb"
        return "${formatThousands(value)} $suffix"
    }
}

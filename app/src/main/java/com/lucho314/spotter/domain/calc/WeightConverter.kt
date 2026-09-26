package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

/** Converts between kg (always the persisted unit, `weight_kg`) and lb for display/input. */
object WeightConverter {

    const val KG_PER_LB = 0.45359237

    fun fromKg(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg / KG_PER_LB
    }

    /** Converts [value] (in [unit]) to kg, rounded HALF_UP to 2 decimals. */
    fun toKg(value: Double, unit: WeightUnit): Double {
        val kg = when (unit) {
            WeightUnit.KG -> value
            WeightUnit.LB -> value * KG_PER_LB
        }
        // BigDecimal.valueOf(Double), not the BigDecimal(Double) constructor: the latter reflects
        // the double's exact binary representation (e.g. 2.675 becomes 2.67499999999999982236...),
        // which can round the wrong way; valueOf() goes through Double.toString() first.
        return BigDecimal.valueOf(kg).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /** Formats [kg] converted to [unit], without trailing decimal zeros (e.g. "80", "72.5"). */
    fun format(kg: Double, unit: WeightUnit): String {
        val value = BigDecimal.valueOf(fromKg(kg, unit)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        return value.toPlainString()
    }
}

package com.lucho314.spotter.domain.calc

import java.time.LocalDate
import java.time.Period

/**
 * Age in whole years, using [Period.between] instead of a fixed 365.25-day division (bug 27: the
 * RN app's approximation could be off by a day around leap years and the birthday itself).
 */
object AgeCalculator {
    fun age(birth: LocalDate, today: LocalDate): Int = Period.between(birth, today).years
}

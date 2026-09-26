package com.lucho314.spotter.feature.profile

/**
 * Formats free-text keyboard input into "dd/MM/aaaa" as the user types, auto-inserting the `/`
 * separators - kept free of Compose/Android types so it's unit-testable on the JVM.
 */
object BirthDateInput {

    private const val MAX_DIGITS = 8

    /** Keeps only digits (up to [MAX_DIGITS]) and inserts "/" after the 2nd and 4th. */
    fun format(raw: String): String {
        val digits = raw.filter { it.isDigit() }.take(MAX_DIGITS)
        return buildString {
            digits.forEachIndexed { index, char ->
                if (index == 2 || index == 4) append('/')
                append(char)
            }
        }
    }
}

package com.lucho314.spotter.domain.calc

/**
 * Parses free-text weight/reps input from the workout screen's number fields. The es-AR keyboard
 * produces `,` as the decimal separator (bug 9 in `docs/MIGRATION_PLAN.md` section 7: the RN app
 * used `parseFloat`, which truncates at the comma).
 */
object WeightInputParser {

    // Plain decimal only (no sign, no scientific notation): up to 4 integer digits, optionally
    // followed by 1-2 decimal digits. This alone rejects negatives, "abc" and "1e3".
    private val WEIGHT_PATTERN = Regex("^\\d{1,4}(\\.\\d{1,2})?$")
    private const val MAX_WEIGHT_KG = 1000.0

    /** Null if [text] isn't a plain non-negative number with at most 2 decimals, or exceeds 1000 (kg-equivalent). */
    fun parseWeight(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (!WEIGHT_PATTERN.matches(normalized)) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value > MAX_WEIGHT_KG) return null
        return value
    }

    /** Null if [text] isn't an integer in 1..200. */
    fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..200 }
}

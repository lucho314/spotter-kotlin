package com.lucho314.spotter.domain.calc

/**
 * Rules for the short per-exercise note written during a workout ("la próxima aumentar", "me
 * molestó el hombro") and shown next time in the last-session sheet. The same [MAX_LENGTH] is
 * enforced server-side by `workout_exercise_notes`' check constraint: Postgres counts code points,
 * this counts UTF-16 `Char`s, so anything accepted here always fits there too.
 */
object ExerciseNote {
    const val MAX_LENGTH = 50

    /** While typing: only caps the length (never trims, so the cursor doesn't jump). */
    fun clampInput(text: String): String = TextSanitizer.truncateSafely(text, MAX_LENGTH)

    /** What actually gets saved/shown: a single trimmed line, or null if blank. */
    fun normalize(raw: String?): String? = TextSanitizer.singleLine(raw, MAX_LENGTH)
}

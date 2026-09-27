package com.lucho314.spotter.domain.calc

/**
 * Sanitizes free-form text coming from other users (shared routines) or external systems (the AI
 * import edge function) before it's ever stored or rendered: never trust user-controlled strings
 * to be a single, printable line, or to be short - see section 8 (B2/B6) of the migration plan.
 */
object TextSanitizer {

    private val BIDI_CONTROLS = Regex("[‎‏‪-‮⁦-⁩]")
    private val LINE_SEPARATORS = Regex("[  ]")

    /**
     * Collapses control characters (including `\n`/`\t`), U+2028/U+2029 and runs of whitespace into
     * a single space; strips bidi control characters entirely; trims; cuts to [maxLength] `Char`s
     * without splitting a surrogate pair. Returns null if the result is blank.
     */
    fun singleLine(raw: String?, maxLength: Int): String? {
        if (raw == null) return null
        val withoutBidi = BIDI_CONTROLS.replace(raw, "")
        val withoutLineSeparators = LINE_SEPARATORS.replace(withoutBidi, " ")
        val collapsed = buildString {
            var lastWasSpace = false
            for (c in withoutLineSeparators) {
                val isWhitespaceLike = c.isWhitespace() || c.isISOControl()
                if (isWhitespaceLike) {
                    if (!lastWasSpace) append(' ')
                    lastWasSpace = true
                } else {
                    append(c)
                    lastWasSpace = false
                }
            }
        }.trim()
        if (collapsed.isEmpty()) return null
        return truncateSafely(collapsed, maxLength)
    }

    /** Like [singleLine] but keeps `\n` (`\r\n` and `\r` normalized to `\n`), capped at 2 consecutive newlines. */
    fun multiLine(raw: String?, maxLength: Int): String? {
        if (raw == null) return null
        val withoutBidi = BIDI_CONTROLS.replace(raw, "")
        val normalizedNewlines = withoutBidi.replace("\r\n", "\n").replace('\r', '\n')
        val withoutLineSeparators = LINE_SEPARATORS.replace(normalizedNewlines, "\n")
        val collapsed = buildString {
            var consecutiveNewlines = 0
            var lastWasSpace = false
            for (c in withoutLineSeparators) {
                when {
                    c == '\n' -> {
                        if (consecutiveNewlines < 2) append('\n')
                        consecutiveNewlines++
                        lastWasSpace = false
                    }
                    c.isWhitespace() || c.isISOControl() -> {
                        if (!lastWasSpace) append(' ')
                        lastWasSpace = true
                        consecutiveNewlines = 0
                    }
                    else -> {
                        append(c)
                        lastWasSpace = false
                        consecutiveNewlines = 0
                    }
                }
            }
        }.trim('\n', ' ').let { trimmedEnds ->
            // trim() only strips plain whitespace from both ends without touching interior
            // newlines; re-trim any leading/trailing spaces line-by-line is unnecessary here since
            // interior single spaces were already normalized above.
            trimmedEnds
        }
        if (collapsed.isEmpty()) return null
        return truncateSafely(collapsed, maxLength)
    }

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")

    fun isUuid(value: String?): Boolean = value != null && UUID_REGEX.matches(value)

    /** Cuts [text] to at most [maxLength] `Char`s, never splitting a surrogate pair at the boundary. */
    private fun truncateSafely(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        var cut = maxLength
        if (cut > 0 && Character.isHighSurrogate(text[cut - 1]) && (cut >= text.length || Character.isLowSurrogate(text[cut]))) {
            cut -= 1
        }
        return text.substring(0, cut)
    }
}

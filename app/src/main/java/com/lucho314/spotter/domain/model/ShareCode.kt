package com.lucho314.spotter.domain.model

import java.security.SecureRandom

/**
 * A validated routine share code. Two formats are accepted: the 8-char client-generated alphabet
 * (uppercase, ambiguity-free) and the 12-char lowercase hex codes produced by the legacy DB trigger.
 */
@JvmInline
value class ShareCode private constructor(val value: String) {

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val CLIENT_FORMAT = Regex("^[A-HJ-NP-Z2-9]{8}$")
        private val HEX_FORMAT = Regex("^[0-9a-f]{12}$")

        /** Trims [raw] and normalizes case; returns null if it matches neither known format. */
        fun parse(raw: String?): ShareCode? {
            val trimmed = raw?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            val upper = trimmed.uppercase()
            if (CLIENT_FORMAT.matches(upper)) return ShareCode(upper)
            val lower = trimmed.lowercase()
            if (HEX_FORMAT.matches(lower)) return ShareCode(lower)
            return null
        }

        /** Generates a new 8-character client-format code using [random]. */
        fun generate(random: SecureRandom): ShareCode {
            val chars = CharArray(8) { ALPHABET[random.nextInt(ALPHABET.length)] }
            return ShareCode(String(chars))
        }
    }
}

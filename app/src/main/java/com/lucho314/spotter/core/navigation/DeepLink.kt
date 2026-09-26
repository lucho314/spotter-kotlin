package com.lucho314.spotter.core.navigation

import com.lucho314.spotter.domain.model.ShareCode
import java.net.URI

/** Deep links Spotter understands. Anything else is ignored (see [DeepLinkParser]). */
sealed interface DeepLink {
    /** `spotter://auth/callback[?...]` — handled by `supabase.handleDeeplinks`, not by navigation. */
    data object AuthCallback : DeepLink

    /** `spotter://import/{code}` — always requires explicit user confirmation before importing. */
    data class ImportRoutine(val code: ShareCode) : DeepLink
}

/**
 * Deep links are parsed here instead of via Navigation Compose's `navDeepLink`, so a link can
 * never skip the authentication guard and all validation lives in one testable place.
 */
object DeepLinkParser {

    private const val SCHEME = "spotter"

    fun parse(raw: String?): DeepLink? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME) return null

        return when (uri.host) {
            "auth" -> parseAuthCallback(uri)
            "import" -> parseImportRoutine(uri)
            else -> null
        }
    }

    private fun parseAuthCallback(uri: URI): DeepLink? {
        val path = uri.path.orEmpty()
        return if (path == "/callback" || path.isEmpty()) DeepLink.AuthCallback else null
    }

    private fun parseImportRoutine(uri: URI): DeepLink? {
        val segments = uri.path.orEmpty().split("/").filter { it.isNotBlank() }
        if (segments.size != 1) return null
        val code = ShareCode.parse(segments.first()) ?: return null
        return DeepLink.ImportRoutine(code)
    }
}

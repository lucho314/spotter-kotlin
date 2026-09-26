package com.lucho314.spotter.core.config

import java.net.URI

/**
 * Runtime configuration read from [com.lucho314.spotter.BuildConfig], which in turn is generated
 * from `local.properties` (or the environment) at build time. Never hold secrets in source.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val googleWebClientId: String?,
) {
    /**
     * False when the Supabase URL/key are missing or malformed; the UI shows
     * [com.lucho314.spotter.feature.root.ConfigErrorScreen] instead of touching the client.
     *
     * Beyond a plain `https://` prefix check, this rejects URLs with a non-empty path (e.g. one
     * that accidentally includes `/rest/v1` or `/auth/v1`): supabase-kt's `SupabaseClientBuilder`
     * throws for those instead of just misbehaving, which would otherwise crash the app rather
     * than showing [com.lucho314.spotter.feature.root.ConfigErrorScreen].
     */
    val isValid: Boolean
        get() {
            if (supabaseAnonKey.isBlank()) return false
            val uri = runCatching { URI(supabaseUrl) }.getOrNull() ?: return false
            if (uri.scheme != "https") return false
            if (uri.host.isNullOrBlank()) return false
            val path = uri.path.orEmpty()
            return path.isEmpty() || path == "/"
        }
}

package com.lucho314.spotter.core.common

/** Typed error surfaced at repository/use case boundaries. Never a raw [Throwable]. */
sealed interface AppError {
    data object Network : AppError
    data object Unauthorized : AppError
    data object NotFound : AppError
    data class Conflict(val detail: String? = null) : AppError
    data class Validation(val reason: ValidationReason) : AppError
    data class Server(val code: String? = null, val message: String? = null) : AppError

    /**
     * @param cause kept for stack traces in debug-only logging (`Logger.d`/`Logger.w`, which are
     * no-ops in release builds). Its `message`/`toString()` must never be shown to the user or
     * written to release logs: for exceptions coming from supabase-kt's `RestException`, the
     * message embeds the request URL and headers (`Authorization: Bearer <jwt>`, `apikey`).
     */
    data class Unknown(val cause: Throwable? = null) : AppError
}

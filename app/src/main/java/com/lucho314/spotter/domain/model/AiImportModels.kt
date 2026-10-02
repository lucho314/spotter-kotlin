package com.lucho314.spotter.domain.model

/** Successful outcome of [com.lucho314.spotter.domain.usecase.ImportRoutineFromImageUseCase]. */
data class AiImportedRoutine(val routineId: String, val routineName: String?)

/**
 * Codes carried in [com.lucho314.spotter.core.common.AppError.Server.code] for AI import
 * failures. The server's own error text is never carried - see
 * [com.lucho314.spotter.data.repository.AiImportErrorMapper]'s KDoc.
 */
object AiImportErrorCodes {
    const val RATE_LIMITED = "ai_rate_limited"
    /** [io.ktor.client.plugins.HttpRequestTimeoutException]: the request may have been processed regardless. */
    const val TIMEOUT = "ai_timeout"

    /** The image had no recognizable exercises (or a legacy response has no error code). */
    const val REJECTED = "ai_rejected"

    /** Undecodable response, missing/non-UUID routine id, or the routine isn't found for the user afterwards. */
    const val INVALID_RESPONSE = "ai_invalid_response"

    /** Any other non-2xx [io.github.jan.supabase.exceptions.RestException]. */
    const val FAILED = "ai_failed"
}

/** ~3 MB of JPEG, base64-encoded. Shared by [com.lucho314.spotter.domain.usecase.ImportRoutineFromImageUseCase] and the encoder. */
const val AI_IMPORT_MAX_BASE64_LENGTH = 4_000_000

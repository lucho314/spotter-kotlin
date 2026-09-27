package com.lucho314.spotter.data.repository

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.core.network.ErrorMapper
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException

/**
 * Maps exceptions thrown by [com.lucho314.spotter.data.remote.datasource.AiImportRemoteDataSource]
 * to [AppError], **never** carrying the edge function's own error text (`RestException.error`,
 * `Throwable.message`): the live function's error text embeds the upstream gateway's own message,
 * which can leak third-party data and must never reach a log or the UI (section 8, B1/B6).
 *
 * Branch order matters: [HttpRequestTimeoutException] is itself an `IOException`, and
 * [ErrorMapper.map] would otherwise fold it into a generic [AppError.Network] - it's checked first
 * so a client-side timeout is distinguishable from "no connection" (see this repository's KDoc:
 * a timeout doesn't mean the routine wasn't created).
 */
internal object AiImportErrorMapper {
    fun map(t: Throwable): AppError = when {
        t is HttpRequestTimeoutException -> AppError.Server(AiImportErrorCodes.TIMEOUT)
        t is RestException && t.statusCode == 413 -> AppError.Validation(ValidationReason.IMAGE_TOO_LARGE)
        t is UnauthorizedRestException -> AppError.Unauthorized
        t is RestException -> AppError.Server(AiImportErrorCodes.FAILED) // never t.error / t.message
        t is SerializationException -> AppError.Server(AiImportErrorCodes.INVALID_RESPONSE)
        else -> ErrorMapper.map(t) // IOException/HttpRequestException -> Network, etc.
    }
}

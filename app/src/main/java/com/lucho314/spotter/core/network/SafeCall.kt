package com.lucho314.spotter.core.network

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Runs [block], mapping any thrown exception to a typed [AppError]. [CancellationException] is
 * always rethrown so coroutine cancellation keeps working.
 */
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(ErrorMapper.map(e))
}

/**
 * Maps supabase-kt / Ktor / kotlinx.serialization exceptions to [AppError].
 *
 * IMPORTANT: never build an [AppError] field from `Throwable.message`/`toString()` for a
 * [RestException] (or subclass): that message embeds the request URL and headers (including the
 * `Authorization: Bearer <jwt>` and `apikey` headers) - see [RestException]'s own constructor. Use
 * the structured [RestException.error]/[RestException.description] fields instead, which never
 * carry the URL/headers. [AppError.Unknown.cause] exists for stack traces in debug logging only;
 * its `message` must never be surfaced to the UI or written to release logs.
 */
object ErrorMapper {
    fun map(t: Throwable): AppError = when {
        t is IOException || t is HttpRequestException || t is HttpRequestTimeoutException -> AppError.Network
        t is UnauthorizedRestException || t is AuthRestException -> AppError.Unauthorized
        t is NotFoundRestException -> AppError.NotFound
        t is PostgrestRestException && t.code == "23505" -> AppError.Conflict(t.description ?: t.error)
        t is PostgrestRestException && t.code == "PGRST116" -> AppError.NotFound
        t is PostgrestRestException && t.code == "42501" -> AppError.Server("42501")
        t is RestException -> AppError.Server(code = (t as? PostgrestRestException)?.code, message = t.error)
        t is SerializationException -> AppError.Server("decode")
        else -> AppError.Unknown(t)
    }
}

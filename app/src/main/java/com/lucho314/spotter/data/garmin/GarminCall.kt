package com.lucho314.spotter.data.garmin

import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

/** Internal, data-layer-only exception used to carry a typed [GarminError] out of a [garminCall] block. Its message is fixed and PII-free. */
class GarminApiException(val error: GarminError) : Exception("Garmin API error")

/**
 * Runs [block], mapping any thrown exception to a typed [GarminResult]. Mirrors
 * [com.lucho314.spotter.core.network.safeCall], but with Garmin's own error type and exception
 * mapping (Ktor, not supabase-kt).
 */
internal suspend fun <T> garminCall(block: suspend () -> T): GarminResult<T> = try {
    GarminResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: GarminApiException) {
    GarminResult.Failure(e.error)
} catch (e: IOException) {
    GarminResult.Failure(GarminError.Network)
} catch (e: HttpRequestTimeoutException) {
    GarminResult.Failure(GarminError.Network)
} catch (e: ConnectTimeoutException) {
    GarminResult.Failure(GarminError.Network)
} catch (e: SocketTimeoutException) {
    GarminResult.Failure(GarminError.Network)
} catch (e: SerializationException) {
    GarminResult.Failure(GarminError.ServiceChanged("decode"))
} catch (e: IllegalArgumentException) {
    GarminResult.Failure(GarminError.ServiceChanged("decode"))
} catch (e: Throwable) {
    GarminResult.Failure(GarminError.Unknown(e))
}

/** Unwraps a [GarminResult.Success], or re-throws its [GarminResult.Failure] as a [GarminApiException] for an enclosing [garminCall]. */
internal fun <T> GarminResult<T>.getOrThrowGarmin(): T = when (this) {
    is GarminResult.Success -> value
    is GarminResult.Failure -> throw GarminApiException(error)
}

package com.lucho314.spotter.core.network

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Test

class SafeCallTest {

    @Test
    fun `IOException maps to Network`() = runTest {
        val result = safeCall { throw IOException("boom") }

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
    }

    @Test
    fun `SerializationException maps to Server decode`() = runTest {
        val result = safeCall { throw SerializationException("bad json") }

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server("decode")))
    }

    @Test
    fun `unknown RuntimeException maps to Unknown`() = runTest {
        val cause = RuntimeException("unexpected")

        val result = safeCall { throw cause }

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val error = (result as AppResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        assertThat((error as AppError.Unknown).cause).isSameInstanceAs(cause)
    }

    @Test
    fun `CancellationException is rethrown, not wrapped`() = runTest {
        try {
            safeCall { throw CancellationException("cancelled") }
            throw AssertionError("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertThat(e).hasMessageThat().isEqualTo("cancelled")
        }
    }

    @Test
    fun `successful block returns Success`() = runTest {
        val result = safeCall { 42 }

        assertThat(result).isEqualTo(AppResult.Success(42))
    }
}

/**
 * [ErrorMapper.isUnauthorizedCode] is exercised directly rather than through a real
 * [io.github.jan.supabase.postgrest.exception.PostgrestRestException]: that exception's
 * constructor eagerly builds its message from a real Ktor `HttpResponse`'s in-flight request
 * (`HttpResponseKt.getRequest`), which would need a hand-rolled fake of several Ktor internals to
 * construct in a unit test - the actual decision logic this review item cares about is this pure
 * function.
 */
class ErrorMapperIsUnauthorizedCodeTest {

    @Test
    fun `HTTP 401 is unauthorized regardless of code`() {
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 401, code = null)).isTrue()
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 401, code = "42501")).isTrue()
    }

    @Test
    fun `PGRST301, PGRST302 and PGRST303 are unauthorized regardless of status code`() {
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 400, code = "PGRST301")).isTrue()
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 400, code = "PGRST302")).isTrue()
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 400, code = "PGRST303")).isTrue()
    }

    @Test
    fun `an unrelated 4xx with an unrelated code is not unauthorized`() {
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 403, code = "42501")).isFalse()
        assertThat(ErrorMapper.isUnauthorizedCode(statusCode = 404, code = "PGRST116")).isFalse()
    }
}

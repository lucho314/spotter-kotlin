package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageResponse
import com.lucho314.spotter.domain.model.AiImportErrorCodes
import com.lucho314.spotter.domain.model.AiImportedRoutine
import com.lucho314.spotter.testutil.FakeAiImportRemoteDataSource
import com.lucho314.spotter.testutil.FakeLogger
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiImportRepositoryImplTest {

    private lateinit var remote: FakeAiImportRemoteDataSource
    private lateinit var logger: FakeLogger
    private lateinit var repository: AiImportRepositoryImpl

    @Before
    fun setUp() {
        remote = FakeAiImportRemoteDataSource()
        logger = FakeLogger()
        repository = AiImportRepositoryImpl(remote, logger)
    }

    @Test
    fun `a successful response returns the routine id and name`() = runTest {
        remote.response = ParseRoutineImageResponse(routineId = "r1", routineName = "Push Day")

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Success(AiImportedRoutine("r1", "Push Day")))
    }

    @Test
    fun `a 200 response with an error field is REJECTED, never carrying the server's text`() = runTest {
        remote.response = ParseRoutineImageResponse(error = "some upstream gateway text")

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.REJECTED)))
        assertThat(logger.warnings.joinToString() + logger.errors.joinToString()).doesNotContain("upstream gateway text")
    }

    @Test
    fun `a 200 response missing routine_id maps to INVALID_RESPONSE`() = runTest {
        remote.response = ParseRoutineImageResponse(routineId = null, routineName = null)

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE)))
    }

    @Test
    fun `HttpRequestTimeoutException maps to Server TIMEOUT`() = runTest {
        remote.error = HttpRequestTimeoutException("http://example.com", 120_000L)

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.TIMEOUT)))
    }

    @Test
    fun `a generic IOException maps to Network`() = runTest {
        remote.error = IOException("connection reset")

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
    }

    @Test
    fun `a SerializationException maps to INVALID_RESPONSE`() = runTest {
        remote.error = SerializationException("bad json")

        val result = repository.importFromImage("user-1", "base64...")

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Server(AiImportErrorCodes.INVALID_RESPONSE)))
    }

    @Test
    fun `mimeType is image jpeg and userId is sent as given`() = runTest {
        repository.importFromImage("user-1", "base64...")

        val request = remote.requests.single()
        assertThat(request.mimeType).isEqualTo("image/jpeg")
        assertThat(request.userId).isEqualTo("user-1")
    }
}

package com.lucho314.spotter.data.garmin.remote

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.garmin.GarminApiException
import com.lucho314.spotter.domain.model.GarminError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, listOf("application/json"))

class KtorGarminActivityRemoteDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dataSource(status: HttpStatusCode, body: String, captureRequest: ((io.ktor.client.request.HttpRequestData) -> Unit)? = null) =
        KtorGarminActivityRemoteDataSource(
            GarminHttpClientFactory { HttpClient(MockEngine { request -> captureRequest?.invoke(request); respond(body, status, jsonHeaders()) }) { expectSuccess = false } },
            json,
        )

    @Test
    fun `the request is a multipart upload with the file part, filename and Bearer header`() = runTest {
        var contentTypeHeader: String? = null
        var authHeader: String? = null
        var isMultipart = false
        val ds = KtorGarminActivityRemoteDataSource(
            GarminHttpClientFactory {
                HttpClient(
                    MockEngine { request ->
                        contentTypeHeader = request.body.contentType?.toString()
                        authHeader = request.headers[HttpHeaders.Authorization]
                        isMultipart = request.body is io.ktor.client.request.forms.MultiPartFormDataContent
                        respond("""{"detailedImportResult":{"successes":[],"failures":[]}}""", HttpStatusCode.Accepted, jsonHeaders())
                    },
                ) { expectSuccess = false }
            },
            json,
        )

        ds.upload("token-123", "spotter_abcd1234.fit", byteArrayOf(1, 2, 3))

        assertThat(contentTypeHeader).contains("multipart/form-data")
        assertThat(authHeader).isEqualTo("Bearer token-123")
        assertThat(isMultipart).isTrue()
    }

    @Test
    fun `202 with no successes is Accepted with a null activity id but the uploadId`() = runTest {
        val ds = dataSource(
            HttpStatusCode.Accepted,
            """{"detailedImportResult":{"uploadId":777,"successes":[],"failures":[]}}""",
        )

        val result = ds.upload("token", "f.fit", byteArrayOf())

        assertThat(result).isEqualTo(UploadHttpResult.Accepted(null, 777L))
    }

    @Test
    fun `201 with an internalId is Accepted with that id`() = runTest {
        val ds = dataSource(
            HttpStatusCode.Created,
            """{"detailedImportResult":{"uploadId":1,"successes":[{"internalId":999}],"failures":[]}}""",
        )

        val result = ds.upload("token", "f.fit", byteArrayOf())

        assertThat(result).isEqualTo(UploadHttpResult.Accepted(999L, 1L))
    }

    @Test
    fun `409 is a Duplicate`() = runTest {
        val ds = dataSource(HttpStatusCode.Conflict, """{"detailedImportResult":{"failures":[{"internalId":42}]}}""")

        val result = ds.upload("token", "f.fit", byteArrayOf())

        assertThat(result).isEqualTo(UploadHttpResult.Duplicate(42L))
    }

    @Test
    fun `202 with a 'Duplicate Activity' failure message is also a Duplicate`() = runTest {
        val ds = dataSource(
            HttpStatusCode.Accepted,
            """{"detailedImportResult":{"successes":[],"failures":[{"internalId":11,"messages":[{"content":"Duplicate Activity."}]}]}}""",
        )

        val result = ds.upload("token", "f.fit", byteArrayOf())

        assertThat(result).isEqualTo(UploadHttpResult.Duplicate(11L))
    }

    @Test
    fun `401 is Unauthorized`() = runTest {
        val ds = dataSource(HttpStatusCode.Unauthorized, "")

        assertThat(ds.upload("token", "f.fit", byteArrayOf())).isEqualTo(UploadHttpResult.Unauthorized)
    }

    @Test
    fun `400 and 413 throw InvalidFile with the http status`() = runTest {
        val ds400 = dataSource(HttpStatusCode.BadRequest, "")
        val error400 = runCatching { ds400.upload("token", "f.fit", byteArrayOf()) }.exceptionOrNull() as GarminApiException
        assertThat(error400.error).isEqualTo(GarminError.InvalidFile(400))

        val ds413 = dataSource(HttpStatusCode.PayloadTooLarge, "")
        val error413 = runCatching { ds413.upload("token", "f.fit", byteArrayOf()) }.exceptionOrNull() as GarminApiException
        assertThat(error413.error).isEqualTo(GarminError.InvalidFile(413))
    }

    @Test
    fun `429 throws RateLimited`() = runTest {
        val ds = dataSource(HttpStatusCode.TooManyRequests, "")

        val error = runCatching { ds.upload("token", "f.fit", byteArrayOf()) }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.RateLimited)
    }

    @Test
    fun `503 throws Server with the http status`() = runTest {
        val ds = dataSource(HttpStatusCode.ServiceUnavailable, "")

        val error = runCatching { ds.upload("token", "f.fit", byteArrayOf()) }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.Server(503))
    }
}

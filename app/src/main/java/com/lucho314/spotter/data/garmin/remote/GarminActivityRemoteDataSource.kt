package com.lucho314.spotter.data.garmin.remote

import com.lucho314.spotter.data.garmin.GarminApiException
import com.lucho314.spotter.domain.model.GarminError
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface UploadHttpResult {
    data class Accepted(val activityId: Long?, val uploadId: Long?) : UploadHttpResult
    data class Duplicate(val activityId: Long?) : UploadHttpResult
    data object Unauthorized : UploadHttpResult
}

interface GarminActivityRemoteDataSource {
    /** Throws [GarminApiException]. */
    suspend fun upload(accessToken: String, fileName: String, bytes: ByteArray): UploadHttpResult
}

class KtorGarminActivityRemoteDataSource @Inject constructor(
    private val clientFactory: GarminHttpClientFactory,
    private val json: Json,
) : GarminActivityRemoteDataSource {

    private val client by lazy { clientFactory.create(withCookies = false) }

    override suspend fun upload(accessToken: String, fileName: String, bytes: ByteArray): UploadHttpResult {
        val response = client.post(GarminEndpoints.UPLOAD_URL) {
            GarminEndpoints.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) }
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        val status = response.status.value
        // Best-effort parse: an empty/invalid body on a 2xx still means "accepted", just without ids.
        val parsed = decodeOrNull<UploadResponse>(response.bodyAsText())
        val result = parsed?.detailedImportResult
        return when {
            status == 401 -> UploadHttpResult.Unauthorized
            status == 409 -> UploadHttpResult.Duplicate(result?.failures?.firstOrNull()?.internalId)
            status in 200..202 -> {
                val duplicate = result?.failures?.firstOrNull { failure ->
                    failure.messages.orEmpty().any { it.code == DUPLICATE_MESSAGE_CODE || it.content.isDuplicateMessage() }
                }
                if (duplicate != null) {
                    UploadHttpResult.Duplicate(duplicate.internalId)
                } else {
                    UploadHttpResult.Accepted(result?.successes?.firstOrNull()?.internalId, result?.uploadId)
                }
            }
            status == 400 || status == 413 || status == 415 || status == 422 -> throw GarminApiException(GarminError.InvalidFile(status))
            status == 429 -> throw GarminApiException(GarminError.RateLimited)
            // 403 is ambiguous here (could be a genuine block or a rejected upload) - treated as a
            // retryable server error rather than a hard failure, up to the worker's own retry cap.
            status == 403 || status in 500..599 -> throw GarminApiException(GarminError.Server(status))
            else -> throw GarminApiException(GarminError.Server(status))
        }
    }

    private fun String?.isDuplicateMessage(): Boolean = this?.contains("duplicate", ignoreCase = true) == true

    private inline fun <reified T> decodeOrNull(text: String): T? = try {
        json.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val DUPLICATE_MESSAGE_CODE = 202
    }
}

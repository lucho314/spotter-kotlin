package com.lucho314.spotter.data.remote.datasource

import com.lucho314.spotter.data.remote.dto.ParseRoutineImageRequest
import com.lucho314.spotter.data.remote.dto.ParseRoutineImageResponse
import io.github.jan.supabase.functions.Functions
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

private const val FUNCTION_NAME = "parse-routine-image"

/**
 * The edge function decodes the image, calls an external vision model and writes the routine -
 * comfortably longer than `SupabaseModule`'s global 60s request timeout for the rest of the app.
 */
private const val REQUEST_TIMEOUT_MS = 120_000L

// NOTE: `requestTimeoutMillis` alone isn't enough. Ktor 3.5.1's OkHttp engine
// (`OkHttpEngine.setupTimeoutAttributes`) only overrides `OkHttpClient.Builder.readTimeout` when
// `socketTimeoutMillis` is set; otherwise it keeps OkHttp's own default of 10s. Since the edge
// function doesn't write any response bytes until it's done, any analysis longer than 10s used to
// fail with a `SocketTimeoutException` well before the 120s request timeout ever kicked in
// (section 2, finding 1).

@Singleton
class SupabaseAiImportRemoteDataSource @Inject constructor(
    private val functions: Functions,
    private val json: Json,
) : AiImportRemoteDataSource {

    override suspend fun parseRoutineImage(request: ParseRoutineImageRequest): ParseRoutineImageResponse {
        // Uses the block-based `invoke` overload (not the one taking a body directly) so a
        // per-request timeout can be set; the body is serialized and attached manually instead.
        val response = functions.invoke(FUNCTION_NAME) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ParseRoutineImageRequest.serializer(), request))
            timeout {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
        return json.decodeFromString(ParseRoutineImageResponse.serializer(), response.bodyAsText())
    }
}
